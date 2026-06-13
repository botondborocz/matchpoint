package org.ttproject.repository

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ttproject.ORACLE_IP
import org.ttproject.SERVER_DNS
import org.ttproject.SERVER_IP
import org.ttproject.data.ChatThreadDto
import org.ttproject.data.IncomingMessageDto
import org.ttproject.data.Location
import org.ttproject.data.MessageDto
import org.ttproject.data.ThemeUpdateRequest
import org.ttproject.data.TokenResponse
import org.ttproject.data.TokenStorage
import org.ttproject.database.ChatDatabase
import org.ttproject.database.PendingMessage
import org.ttproject.util.ConnectivityChecker
import org.ttproject.data.MessageStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class ChatEvent {
    data class Message(val message: MessageDto) : ChatEvent()
    data class Reaction(val messageId: String, val userId: String, val emoji: String) : ChatEvent()
    data class RemoveReaction(val messageId: String, val userId: String) : ChatEvent()
    data class Read(val readerId: String) : ChatEvent()
}

interface ChatRepository {
    suspend fun getMessageHistory(connectionId: String): List<MessageDto>
    fun observeLiveMessages(connectionId: String): Flow<ChatEvent>
    suspend fun sendMessage(text: String, replyToMessageId: String? = null)
    suspend fun sendReaction(messageId: String, emoji: String)
    suspend fun removeReaction(messageId: String)
    fun disconnect()
    suspend fun getConnections(): List<ChatThreadDto>
    suspend fun savePushToken(fcmToken: String)
    suspend fun markMessagesAsRead(chatId: String)
    suspend fun updateChatTheme(connectionId: String, themeName: String)
    suspend fun uploadChatImages(connectionId: String, images: List<ByteArray>): Result<List<String>>
    suspend fun uploadAudioMessage(connectionId: String, audioBytes: ByteArray): Result<String>
    
    fun isConnected(): Boolean
    fun getPendingMedia(url: String): ByteArray?
    suspend fun queuePendingMessage(
        connectionId: String,
        tempId: String,
        text: String,
        replyToId: String?,
        mediaType: String,
        mediaBytes: List<ByteArray>?,
        createdAt: String
    )
    fun triggerPendingSync()
}

class ChatRepositoryImpl (
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
    private val chatDatabase: ChatDatabase,
    private val connectivityChecker: ConnectivityChecker
) : ChatRepository {
    // We hold onto the active session so we can send messages through it later
    private var webSocketSession: DefaultClientWebSocketSession? = null

    // Memory cache of pending media byte-arrays mapped to temporary IDs
    private val pendingMediaMap = mutableMapOf<String, List<ByteArray>>()
    private val syncScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private val syncMutex = Mutex()

    init {
        // Populate the pending media map on startup so that Coil can display pending media picked in a previous offline session
        try {
            val pending = chatDatabase.getPendingMessages()
            for (p in pending) {
                if (!p.mediaBytes.isNullOrEmpty()) {
                    pendingMediaMap[p.id] = p.mediaBytes
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 1. Fetch History via REST
    override suspend fun getMessageHistory(connectionId: String): List<MessageDto> {
        val currentUserId = tokenStorage.getUserId() ?: ""
        val token = tokenStorage.getToken() ?: return getCachedHistoryWithPending(connectionId, currentUserId)
        return try {
            val remote: List<MessageDto> = client.get("${SERVER_IP}/api/connections/$connectionId/messages"){
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()

            // Save to database cache
            chatDatabase.saveMessages(connectionId, remote)

            // Merge remote messages with local pending messages
            mergeHistoryWithPending(connectionId, remote, currentUserId)
        } catch (e: Exception) {
            e.printStackTrace()
            getCachedHistoryWithPending(connectionId, currentUserId)
        }
    }

    private fun getCachedHistoryWithPending(connectionId: String, currentUserId: String): List<MessageDto> {
        val localCached = chatDatabase.getMessages(connectionId)
        return mergeHistoryWithPending(connectionId, localCached, currentUserId)
    }

    private fun mergeHistoryWithPending(connectionId: String, messages: List<MessageDto>, currentUserId: String): List<MessageDto> {
        val pending = chatDatabase.getPendingMessages()
            .filter { it.connectionId == connectionId }
            .map { p ->
                MessageDto(
                    id = p.id,
                    senderId = currentUserId,
                    content = p.text,
                    createdAt = p.createdAt,
                    replyToMessageId = p.replyToId,
                    reactions = emptyList(),
                    status = MessageStatus.PENDING
                )
            }
        // Deduplicate: remove any messages from local cache that are actually in pending (if we had saved them, or if there's ID overlap)
        val messageIds = messages.map { it.id }.toSet()
        val uniquePending = pending.filter { !messageIds.contains(it.id) }
        return messages + uniquePending
    }

    // 2. Open WebSocket and return a stream (Flow) of incoming messages
    override fun observeLiveMessages(connectionId: String): Flow<ChatEvent> = flow {
        // A lenient parser prevents crashes if the server adds new fields later
        val jsonParser = Json { ignoreUnknownKeys = true }

        while (true) {
            val token = tokenStorage.getToken()
            if (token == null) {
                // No auth token, wait and try again
                kotlinx.coroutines.delay(3000)
                continue
            }

            try {
                client.webSocket(
                    urlString = "wss://${SERVER_DNS}/api/connections/$connectionId/chat",
                    request = {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                ) {
                    webSocketSession = this

                    while (true) {
                        val frame = incoming.receive()
                        if (frame is Frame.Text) {
                            val text = frame.readText()

                            // Peek at the JSON to see what type of event it is!
                            val jsonElement = Json.parseToJsonElement(text).jsonObject
                            val type = jsonElement["type"]?.jsonPrimitive?.content

                            if (type == "reaction") {
                                // Extract the userId alongside msgId and emoji!
                                val msgId = jsonElement["messageId"]!!.jsonPrimitive.content
                                val userId = jsonElement["userId"]!!.jsonPrimitive.content
                                val emoji = jsonElement["emoji"]!!.jsonPrimitive.content

                                // Save reaction in local cache
                                try {
                                    val currentCached = chatDatabase.getMessages(connectionId)
                                    val updated = currentCached.map { msg ->
                                        if (msg.id == msgId) {
                                            val updatedReactions = msg.reactions
                                                .filter { it.userId != userId }
                                                .toMutableList()
                                                .apply { add(org.ttproject.data.ReactionDto(userId, emoji)) }
                                            msg.copy(reactions = updatedReactions)
                                        } else msg
                                    }
                                    chatDatabase.saveMessages(connectionId, updated)
                                } catch (e: Exception) { e.printStackTrace() }

                                emit(ChatEvent.Reaction(msgId, userId, emoji))

                            } else if (type == "remove_reaction") {
                                // Extract the userId here too!
                                val msgId = jsonElement["messageId"]!!.jsonPrimitive.content
                                val userId = jsonElement["userId"]!!.jsonPrimitive.content

                                // Remove reaction from local cache
                                try {
                                    val currentCached = chatDatabase.getMessages(connectionId)
                                    val updated = currentCached.map { msg ->
                                        if (msg.id == msgId) {
                                            val updatedReactions = msg.reactions.filter { it.userId != userId }
                                            msg.copy(reactions = updatedReactions)
                                        } else msg
                                    }
                                    chatDatabase.saveMessages(connectionId, updated)
                                } catch (e: Exception) { e.printStackTrace() }

                                emit(ChatEvent.RemoveReaction(msgId, userId))
                            } else if (type == "read") {
                                val readerId = jsonElement["readerId"]!!.jsonPrimitive.content
                                
                                // Update local cache: mark messages sent by others (not readerId) as READ
                                try {
                                    val currentCached = chatDatabase.getMessages(connectionId)
                                    val updated = currentCached.map { msg ->
                                        if (msg.senderId != readerId && msg.status != MessageStatus.READ) {
                                            msg.copy(status = MessageStatus.READ)
                                        } else msg
                                    }
                                    chatDatabase.saveMessages(connectionId, updated)
                                } catch (e: Exception) { e.printStackTrace() }

                                emit(ChatEvent.Read(readerId))
                            } else {
                                // It's a standard message! Decode it safely.
                                val message = jsonParser.decodeFromString<MessageDto>(text)

                                // Save to local cache in background
                                try {
                                    val currentCached = chatDatabase.getMessages(connectionId)
                                    if (!currentCached.any { it.id == message.id }) {
                                        chatDatabase.saveMessages(connectionId, currentCached + message)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }

                                emit(ChatEvent.Message(message))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            } finally {
                webSocketSession = null
            }

            // Connection lost/failed. Wait 3 seconds and retry.
            kotlinx.coroutines.delay(3000)
        }
    }

    // 3. Send a message through the active WebSocket
    override suspend fun sendMessage(text: String, replyToMessageId: String?) {
        // Create the object
        val payload = IncomingMessageDto(
            content = text,
            replyToMessageId = replyToMessageId,
            type = "message"
        )

        // Convert it to a JSON string
        val jsonString = Json.encodeToString(payload)

        // Send the JSON string to the server!
        webSocketSession?.send(Frame.Text(jsonString))
    }

    override suspend fun sendReaction(messageId: String, emoji: String) {
        val payload = IncomingMessageDto(
            type = "reaction",
            content = emoji,
            targetMessageId = messageId
        )
        webSocketSession?.send(Frame.Text(Json.encodeToString(payload)))
    }

    override suspend fun removeReaction(messageId: String) {
        val payload = IncomingMessageDto(
            type = "remove_reaction",
            content = "", // Content doesn't matter for removal
            targetMessageId = messageId
        )
        webSocketSession?.send(Frame.Text(Json.encodeToString(payload)))
    }

    override fun disconnect() {
        // Handled automatically when the coroutine observing the Flow is cancelled,
        // but you can add explicit close logic here if needed.
    }

    override suspend fun getConnections(): List<ChatThreadDto> {
        val token = tokenStorage.getToken() ?: return chatDatabase.getThreads()

        return try {
            val remote: List<ChatThreadDto> = client.get("${SERVER_IP}/api/connections") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
            chatDatabase.saveThreads(remote)
            remote
        } catch (e: Exception) {
            e.printStackTrace()
            chatDatabase.getThreads()
        }
    }

    override suspend fun savePushToken(fcmToken: String) {
        val token = tokenStorage.getToken() ?: return

        try {
            client.post("${SERVER_IP}/api/users/fcm-token") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(TokenResponse(token = fcmToken))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun markMessagesAsRead(chatId: String) {
        try {
            val token = tokenStorage.getToken() ?: return
            // Hit the endpoint we just created
            client.post("$SERVER_IP/api/connections/$chatId/messages/read") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            // Optional: If you want to trigger a UI refresh immediately, you can do it here.
        } catch (e: Exception) {
            e.printStackTrace()
            // It's okay if this fails silently in the background,
            // the user will just try again next time they open the chat.
        }
    }

    override suspend fun updateChatTheme(connectionId: String, themeName: String) {
        val token = tokenStorage.getToken() ?: return

        try {
            client.put("${SERVER_IP}/api/connections/$connectionId/theme") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(ThemeUpdateRequest(themeName = themeName))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fail silently, the theme just won't persist if offline
        }
    }

    override suspend fun uploadChatImages(connectionId: String, images: List<ByteArray>): Result<List<String>> {
        val token = tokenStorage.getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.post("${SERVER_IP}/api/connections/$connectionId/images") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            images.forEachIndexed { index, imageBytes ->

                                // 👇 1. THE FIX: Detect MP4s right before we send them!
                                val isVideo = imageBytes.size > 8 &&
                                        imageBytes[4].toInt().toChar() == 'f' &&
                                        imageBytes[5].toInt().toChar() == 't' &&
                                        imageBytes[6].toInt().toChar() == 'y' &&
                                        imageBytes[7].toInt().toChar() == 'p'

                                val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
                                val extension = if (isVideo) "mp4" else "jpg"

                                // 👇 2. Send the correct dynamic headers!
                                append("media_$index", imageBytes, Headers.build {
                                    append(HttpHeaders.ContentType, mimeType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"chat_media_$index.$extension\"")
                                })
                            }
                        }
                    )
                )
            }

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val jsonArray = Json.parseToJsonElement(responseText).jsonObject["imageUrls"]!!.jsonArray
                val imageUrls = jsonArray.map { it.jsonPrimitive.content }

                Result.success(imageUrls)
            } else {
                Result.failure(Exception("Upload failed. Status: ${response.status}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun uploadAudioMessage(connectionId: String, audioBytes: ByteArray): Result<String> {
        val token = tokenStorage.getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.post("${SERVER_IP}/api/connections/$connectionId/voice") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("voice_note", audioBytes, Headers.build {
                                append(HttpHeaders.ContentType, "audio/m4a") // Standard format for iOS/Android voice notes
                                append(HttpHeaders.ContentDisposition, "filename=\"voice_note.m4a\"")
                            })
                        }
                    )
                )
            }
            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val url = Json.parseToJsonElement(responseText).jsonObject["audioUrl"]!!.jsonPrimitive.content
                Result.success(url)
            } else {
                Result.failure(Exception("Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isConnected(): Boolean {
        val session = webSocketSession
        return connectivityChecker.isConnected() && session != null && session.isActive
    }

    override fun getPendingMedia(url: String): ByteArray? {
        val parts = url.split("_")
        if (parts.size >= 4) {
            val tempId = "pending_" + parts[2]
            val index = parts[3].toIntOrNull() ?: 0
            val list = pendingMediaMap[tempId]
            if (list != null && index >= 0 && index < list.size) {
                return list[index]
            }
        }
        val direct = pendingMediaMap[url]?.firstOrNull()
        if (direct != null) return direct
        return null
    }

    override suspend fun queuePendingMessage(
        connectionId: String,
        tempId: String,
        text: String,
        replyToId: String?,
        mediaType: String,
        mediaBytes: List<ByteArray>?,
        createdAt: String
    ) {
        try {
            val pendingList = chatDatabase.getPendingMessages().toMutableList()
            val newPending = PendingMessage(
                id = tempId,
                connectionId = connectionId,
                text = text,
                replyToId = replyToId,
                mediaType = mediaType,
                mediaBytes = mediaBytes,
                createdAt = createdAt
            )
            pendingList.add(newPending)
            chatDatabase.savePendingMessages(pendingList)

            if (!mediaBytes.isNullOrEmpty()) {
                pendingMediaMap[tempId] = mediaBytes
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun triggerPendingSync() {
        syncScope.launch {
            syncMutex.withLock {
                if (!isConnected()) return@withLock
                val pendingList = chatDatabase.getPendingMessages()
                if (pendingList.isEmpty()) return@withLock

                val token = tokenStorage.getToken() ?: return@withLock
                val toRemove = mutableListOf<String>()

                for (p in pendingList) {
                    try {
                        when (p.mediaType) {
                            "TEXT" -> {
                                val payload = IncomingMessageDto(
                                    content = p.text,
                                    replyToMessageId = p.replyToId,
                                    type = "message"
                                )
                                val jsonString = Json.encodeToString(payload)
                                client.webSocket(
                                    urlString = "wss://${SERVER_DNS}/api/connections/${p.connectionId}/chat",
                                    request = { header(HttpHeaders.Authorization, "Bearer $token") }
                                ) {
                                    send(Frame.Text(jsonString))
                                }
                            }
                            "IMAGE" -> {
                                val bytesList = p.mediaBytes ?: emptyList()
                                if (bytesList.isNotEmpty()) {
                                    val uploadResult = uploadChatImages(p.connectionId, bytesList)
                                    if (uploadResult.isSuccess) {
                                        val urls = uploadResult.getOrThrow()
                                        val tag = if (urls.size == 1) "[IMAGE]" else "[IMAGES]"
                                        val joinedUrls = urls.joinToString(",")
                                        val payload = IncomingMessageDto(
                                            content = "$tag$joinedUrls",
                                            replyToMessageId = p.replyToId,
                                            type = "message"
                                        )
                                        val jsonString = Json.encodeToString(payload)
                                        client.webSocket(
                                            urlString = "wss://${SERVER_DNS}/api/connections/${p.connectionId}/chat",
                                            request = { header(HttpHeaders.Authorization, "Bearer $token") }
                                        ) {
                                            send(Frame.Text(jsonString))
                                        }
                                    } else {
                                        throw Exception("Image upload failed during sync")
                                    }
                                }
                            }
                            "VIDEO" -> {
                                val bytesList = p.mediaBytes ?: emptyList()
                                if (bytesList.isNotEmpty()) {
                                    val uploadResult = uploadChatImages(p.connectionId, bytesList)
                                    if (uploadResult.isSuccess) {
                                        val urls = uploadResult.getOrThrow()
                                        val videoUrl = urls.find { it.contains(".mp4") } ?: ""
                                        val thumbUrl = urls.find { it.contains(".jpg") || it.contains(".jpeg") } ?: ""
                                        val payloadStr = if (thumbUrl.isNotBlank()) {
                                            "[VIDEO]$thumbUrl,$videoUrl"
                                        } else {
                                            "[VIDEO]$videoUrl"
                                        }
                                        val payload = IncomingMessageDto(
                                            content = payloadStr,
                                            replyToMessageId = p.replyToId,
                                            type = "message"
                                        )
                                        val jsonString = Json.encodeToString(payload)
                                        client.webSocket(
                                            urlString = "wss://${SERVER_DNS}/api/connections/${p.connectionId}/chat",
                                            request = { header(HttpHeaders.Authorization, "Bearer $token") }
                                        ) {
                                            send(Frame.Text(jsonString))
                                        }
                                    } else {
                                        throw Exception("Video upload failed during sync")
                                    }
                                }
                            }
                            "VOICE" -> {
                                val voiceBytes = p.mediaBytes?.firstOrNull()
                                if (voiceBytes != null) {
                                    val uploadResult = uploadAudioMessage(p.connectionId, voiceBytes)
                                    if (uploadResult.isSuccess) {
                                        val url = uploadResult.getOrThrow()
                                        val payload = IncomingMessageDto(
                                            content = "[VOICE]$url",
                                            replyToMessageId = p.replyToId,
                                            type = "message"
                                        )
                                        val jsonString = Json.encodeToString(payload)
                                        client.webSocket(
                                            urlString = "wss://${SERVER_DNS}/api/connections/${p.connectionId}/chat",
                                            request = { header(HttpHeaders.Authorization, "Bearer $token") }
                                        ) {
                                            send(Frame.Text(jsonString))
                                        }
                                    } else {
                                        throw Exception("Audio upload failed during sync")
                                    }
                                }
                            }
                        }
                        toRemove.add(p.id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (toRemove.isNotEmpty()) {
                    val currentPending = chatDatabase.getPendingMessages()
                    val updatedPending = currentPending.filter { !toRemove.contains(it.id) }
                    chatDatabase.savePendingMessages(updatedPending)
                    for (id in toRemove) {
                        pendingMediaMap.remove(id)
                    }
                    org.ttproject.util.NotificationEventBus.triggerRefresh()
                }
            }
        }
    }
}