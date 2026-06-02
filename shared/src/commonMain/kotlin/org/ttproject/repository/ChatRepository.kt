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

sealed class ChatEvent {
    data class Message(val message: MessageDto) : ChatEvent()
    data class Reaction(val messageId: String, val userId: String, val emoji: String) : ChatEvent()
    data class RemoveReaction(val messageId: String, val userId: String) : ChatEvent()
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
}

class ChatRepositoryImpl (
    private val client: HttpClient,
    private val tokenStorage: TokenStorage
) : ChatRepository {
    // We hold onto the active session so we can send messages through it later
    private var webSocketSession: DefaultClientWebSocketSession? = null

    // 1. Fetch History via REST
    override suspend fun getMessageHistory(connectionId: String): List<MessageDto> {
        val token = tokenStorage.getToken() ?: return emptyList()
        return try {
            client.get("${SERVER_IP}/api/connections/$connectionId/messages"){
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 2. Open WebSocket and return a stream (Flow) of incoming messages
    // 2. Open WebSocket and return a stream (Flow) of incoming messages
    override fun observeLiveMessages(connectionId: String): Flow<ChatEvent> = flow {
        val token = tokenStorage.getToken() ?: throw Exception("No auth token found")
        // 👇 A lenient parser prevents crashes if the server adds new fields later
        val jsonParser = Json { ignoreUnknownKeys = true }

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

                        // 👇 Peek at the JSON to see what type of event it is!
                        val jsonElement = Json.parseToJsonElement(text).jsonObject
                        val type = jsonElement["type"]?.jsonPrimitive?.content

                        if (type == "reaction") {
                            // 👇 Extract the userId alongside msgId and emoji!
                            val msgId = jsonElement["messageId"]!!.jsonPrimitive.content
                            val userId = jsonElement["userId"]!!.jsonPrimitive.content
                            val emoji = jsonElement["emoji"]!!.jsonPrimitive.content

                            emit(ChatEvent.Reaction(msgId, userId, emoji))

                        } else if (type == "remove_reaction") {
                            // 👇 Extract the userId here too!
                            val msgId = jsonElement["messageId"]!!.jsonPrimitive.content
                            val userId = jsonElement["userId"]!!.jsonPrimitive.content

                            emit(ChatEvent.RemoveReaction(msgId, userId))

                        } else {
                            // It's a standard message! Decode it safely.
                            val message = jsonParser.decodeFromString<MessageDto>(text)
                            emit(ChatEvent.Message(message))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            webSocketSession = null
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
        val token = tokenStorage.getToken() ?: return emptyList()

        return try {
            client.get("${SERVER_IP}/api/connections") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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

}