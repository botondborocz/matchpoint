package org.ttproject.routes

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.ttproject.data.ChatThreadDto
import org.ttproject.data.MessageDto
import org.ttproject.data.IncomingMessageDto
import org.ttproject.data.ReactionDto
import org.ttproject.database.tables.Connections
import org.ttproject.database.tables.MessageReactions
import org.ttproject.database.tables.Messages
import org.ttproject.database.tables.Users
import org.ttproject.services.BadgeService
import org.ttproject.services.ConnectionManager
import java.lang.reflect.Array.set
import java.time.Instant
import java.util.UUID

// You will need a simple class to manage your active WebSocket connections
val connectionManager = ConnectionManager()

fun Route.messageRoutes(badgeService: BadgeService) {

    authenticate("auth-jwt") {
        get("/api/connections") {
            val principal = call.principal<JWTPrincipal>()
            val currentUserIdStr = principal?.payload?.getClaim("userId")?.asString()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val currentUserId = UUID.fromString(currentUserIdStr)

            val connections = transaction {
                // Find all connections where the current user is involved
                val userConnections = Connections.selectAll().where {
                    (Connections.user1Id eq currentUserId) or (Connections.user2Id eq currentUserId)
                }.toList()

                userConnections.map { row ->
                    val connectionId = row[Connections.id]
                    val user1 = row[Connections.user1Id]
                    val user2 = row[Connections.user2Id]
                    val theme = row[Connections.theme]

                    // Determine who the *other* person is
                    val otherUserId = if (user1 == currentUserId) user2 else user1

                    // Fetch the other user's details
                    val otherUser = Users.selectAll().where { Users.id eq otherUserId }.singleOrNull()
                    val otherUserName = otherUser?.get(Users.username) ?: "Unknown Player"
                    val otherUserImageUrl = otherUser?.get(Users.profileImageUrl) // Optional: If you have profile images

                    // Optional: Fetch the actual last message (Keep it simple for MVP)
                    val lastMessageRow = Messages.selectAll()
                        .where { Messages.connectionId eq connectionId }
                        .orderBy(Messages.createdAt to SortOrder.DESC)
                        .limit(1)
                        .singleOrNull()

                    val lastMessageText = lastMessageRow?.get(Messages.content) ?: "No messages yet"
                    val timestamp = lastMessageRow?.get(Messages.createdAt)?.toString() ?: ""

                    val unreadMessagesCount = Messages.selectAll()
                        .where {
                            (Messages.connectionId eq connectionId) and
                                    (Messages.senderId eq otherUserId) and // Only count messages THEY sent
                                    (Messages.isRead eq false)             // That haven't been read yet
                        }.count().toInt()

                    ChatThreadDto(
                        id = connectionId.toString(),
                        otherUserName = otherUserName,
                        otherUserImageUrl = otherUserImageUrl,
                        lastMessage = lastMessageText,
                        timestamp = timestamp,
                        unreadCount = unreadMessagesCount, // Implement unread logic later
                        isOnline = false, // Implement presence logic later
                        theme = theme
                    )
                }
            }
            val sortedConnections = connections.sortedByDescending { it.timestamp }

            call.respond(HttpStatusCode.OK, sortedConnections)
        }

        route("/api/connections/{connectionId}") {

            // --------------------------------------------------------
            // 1. GET HISTORY: Fetch old messages via standard HTTP
            // --------------------------------------------------------
            get("/messages") {
                val connectionIdStr = call.parameters["connectionId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing connection ID"
                )
                val connectionId = UUID.fromString(connectionIdStr)

                val messages = transaction {
                    // 👇 1. Fetch all messages for this chat
                    val messageRows = Messages.selectAll().where { Messages.connectionId eq connectionId }
                        .orderBy(Messages.createdAt to SortOrder.ASC)
                        .toList()

                    if (messageRows.isEmpty()) return@transaction emptyList<MessageDto>()

                    // 👇 2. Extract just their IDs
                    val messageIds = messageRows.map { it[Messages.id] }

                    // 👇 3. Fetch all reactions attached to these specific messages
                    // .associate() maps them so we can easily look them up by Message ID later
                    val reactionsMap = MessageReactions.selectAll()
                        .where { MessageReactions.messageId inList messageIds }
                        .groupBy(
                            keySelector = { it[MessageReactions.messageId] },
                            valueTransform = {
                                ReactionDto(
                                    userId = it[MessageReactions.userId].toString(),
                                    emoji = it[MessageReactions.emoji]
                                )
                            }
                        )

                    // 👇 4. Combine them! Map the DB rows to your DTO, and attach the emoji if it exists.
                    messageRows.map {
                        MessageDto(
                            id = it[Messages.id].toString(),
                            senderId = it[Messages.senderId].toString(),
                            content = it[Messages.content],
                            createdAt = it[Messages.createdAt].toString(),
                            replyToMessageId = it[Messages.replyToMessageId]?.toString(),
                            reactions = reactionsMap[it[Messages.id]] ?: emptyList()                        )
                    }
                }

                call.respond(HttpStatusCode.OK, messages)
            }

            post("/messages/read") {
                val connectionIdStr = call.parameters["connectionId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing connection ID"
                )
                val connectionId = UUID.fromString(connectionIdStr)

                // Grab the user who is currently reading the chat
                val principal = call.principal<JWTPrincipal>()
                val currentUserIdStr = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val currentUserId = UUID.fromString(currentUserIdStr)

                transaction {
                    // 👇 Only mark messages as read IF they belong to this chat AND were sent by the other person
                    Messages.update({
                        (Messages.connectionId eq connectionId) and (Messages.senderId neq currentUserId)
                    }) {
                        it[isRead] = true
                    }
                }
                call.respond(HttpStatusCode.OK, "Messages marked as read")
            }

            // --------------------------------------------------------
            // 2. LIVE CHAT: Connect via WebSockets
            // --------------------------------------------------------
            webSocket("/chat") {
                val connectionIdStr = call.parameters["connectionId"] ?: return@webSocket close(
                    CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No connection ID")
                )
                val connectionId = UUID.fromString(connectionIdStr)

                val principal = call.principal<JWTPrincipal>()
                val senderIdStr = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                val senderId = UUID.fromString(senderIdStr)

                // 👇 FETCH RECEIVER ID AND SENDER NAME ONCE WHEN THEY CONNECT
                val (receiverId, senderName, senderImageUrl) = transaction {
                    val connRow = Connections.select { Connections.id eq connectionId }.singleOrNull()
                        ?: throw Exception("Connection not found")

                    val u1 = connRow[Connections.user1Id]
                    val u2 = connRow[Connections.user2Id]
                    val rId = if (u1 == senderId) u2 else u1

                    val sName = Users.slice(Users.username).select { Users.id eq senderId }.singleOrNull()?.get(Users.username) ?: "Someone"
                    val senderImageUrl = Users.slice(Users.profileImageUrl).select { Users.id eq senderId }.singleOrNull()?.get(Users.profileImageUrl)
                    Triple(rId, sName, senderImageUrl)
                }

                // 👇 Pass the senderId to the manager
                connectionManager.addSession(connectionId, senderId, this)

                // Create a lenient JSON parser
                val jsonParser = Json { ignoreUnknownKeys = true }

                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            try {
                                val rawPayload = frame.readText()
                                val created_at = Instant.now()

                                val incomingEvent =
                                    jsonParser.decodeFromString<IncomingMessageDto>(rawPayload)

                                if (incomingEvent.type == "message") {

                                    try {
                                        // 👇 1. Parse the incoming JSON from the client
                                        val incomingMessage =
                                            jsonParser.decodeFromString<IncomingMessageDto>(
                                                rawPayload
                                            )
                                        val textContent = incomingMessage.content

                                        // Safely convert the string ID to a UUID if it exists
                                        val replyToId =
                                            incomingMessage.replyToMessageId?.let {
                                                UUID.fromString(
                                                    it
                                                )
                                            }

                                        val newMessageId = transaction {
                                            Messages.insert {
                                                it[Messages.connectionId] = connectionId
                                                it[Messages.senderId] = senderId
                                                it[content] = textContent
                                                it[createdAt] = created_at
                                                // 👇 2. Save the reply ID to the database!
                                                it[replyToMessageId] = replyToId
                                            } get Messages.id
                                        }

                                        badgeService.incrementMetric(
                                            userIdParam = senderId,
                                            column = org.ttproject.database.tables.UserBadgeMetrics.sentMessages
                                        )

                                        // 👇 Use the new ConnectionManager function!
                                        if (!connectionManager.isUserConnected(
                                                connectionId,
                                                receiverId
                                            )
                                        ) {
                                            val targetToken = transaction {
                                                Users.slice(Users.fcmToken)
                                                    .select { Users.id eq receiverId }
                                                    .singleOrNull()
                                                    ?.get(Users.fcmToken)
                                            }

                                            println("🔍 Checking push notification for Receiver: $receiverId")
                                            println("🔍 Token found in DB: $targetToken")

                                            // Check for isNullOrBlank instead of just null!
                                            if (!targetToken.isNullOrBlank()) {
                                                try {
//                                            val message = Message.builder()
//                                                .setToken(targetToken)
//                                                .setNotification(
//                                                    Notification.builder()
//                                                        .setTitle("New message from $senderName")
//                                                        .setBody(textContent)
//                                                        .build()
//                                                )
//                                                .setAndroidConfig(
//                                                    AndroidConfig.builder()
//                                                        .setPriority(AndroidConfig.Priority.HIGH) // 🚨 Forces the heads-up banner!
//                                                        .setNotification(
//                                                            AndroidNotification.builder()
//                                                                .setChannelId("chat_messages") // We will create this channel next
//                                                                .build()
//                                                        )
//                                                        .build()
//                                                )
//                                                .putData("chatId", connectionId.toString())
//                                                .build()
                                                    val message = Message.builder()
                                                        .setToken(targetToken)
                                                        // 🚨 NO .setNotification() HERE! 🚨
                                                        // We send raw data, forcing the Android app to wake up and handle it.
                                                        .putData("chatId", connectionId.toString())
                                                        .putData("senderName", senderName)
                                                        .putData("senderImageUrl", senderImageUrl ?: "")
                                                        .putData("text", textContent)
                                                        .build()

                                                    // 👇 Use synchronous .send() so we can actually catch the Google API errors
                                                    val response =
                                                        FirebaseMessaging.getInstance()
                                                            .send(message)
                                                    println("✅ FCM Success Response: $response")

                                                } catch (e: Exception) {
                                                    println("❌ FCM Sending Failed!")
                                                    e.printStackTrace() // This will tell us exactly why Google rejected it
                                                }
                                            } else {
                                                println("⚠️ Warning: targetToken is empty or null for user $receiverId. Cannot send push.")
                                            }
                                        } else {
                                            println("ℹ️ User $receiverId is currently connected to the WebSocket, skipping push notification.")
                                        }

                                        // 👇 3. Include the replyToMessageId in the broadcast payload back to the clients
                                        val replyJsonStr =
                                            if (replyToId != null) "\"$replyToId\"" else "null"

                                        val payload = """
                                        {
                                            "id": "$newMessageId", 
                                            "senderId": "$senderId", 
                                            "content": "${textContent.replace("\"", "\\\"")}", 
                                            "createdAt": "$created_at",
                                            "replyToMessageId": $replyJsonStr
                                        }
                                    """.trimIndent()

                                        connectionManager.broadcast(connectionId, payload)

                                    } catch (e: Exception) {
                                        call.application.environment.log.error(
                                            "Failed to process individual message",
                                            e
                                        )
                                    }
                                } else if (incomingEvent.type == "reaction") {
                                    val targetId = UUID.fromString(incomingEvent.targetMessageId!!)

                                    transaction {
                                        // Upsert the reaction (insert, or update if they already reacted)
                                        // Note: Exposed doesn't have a native upsert for all DBs, so you can delete and re-insert
                                        MessageReactions.deleteWhere {
                                            (MessageReactions.messageId eq targetId) and (MessageReactions.userId eq senderId)
                                        }
                                        MessageReactions.insert {
                                            it[messageId] = targetId
                                            it[userId] = senderId
                                            it[emoji] = incomingEvent.content
                                        }
                                    }

                                    // Broadcast the reaction to the clients
                                    val payload = """
                                        {
                                            "type": "reaction",
                                            "messageId": "$targetId",
                                            "userId": "$senderId", 
                                            "emoji": "${incomingEvent.content}"
                                        }
                                    """.trimIndent()
                                    connectionManager.broadcast(connectionId, payload)
                                } else if (incomingEvent.type == "remove_reaction") {
                                    val targetId = UUID.fromString(incomingEvent.targetMessageId!!)

                                    transaction {
                                        // Delete the specific reaction for this user and message
                                        MessageReactions.deleteWhere {
                                            (MessageReactions.messageId eq targetId) and (MessageReactions.userId eq senderId)
                                        }
                                    }

                                    // Broadcast the removal to both clients so their UIs update instantly
                                    val payload = """
                                            {
                                                "type": "remove_reaction",
                                                "messageId": "$targetId",
                                                "userId": "$senderId"
                                            }
                                        """.trimIndent()
                                    connectionManager.broadcast(connectionId, payload)
                                }
                            } catch (e: Exception) {
                                call.application.environment.log.error("Failed to process WebSocket frame: ${e.message}", e)
                                e.printStackTrace() // This will print the exact DB error in your server console!
                            }
                        }
                    }
                } catch (e: Exception) {
                    call.application.environment.log.info("WebSocket disconnected")
                } finally {
                    // 👇 Pass the senderId during cleanup
                    connectionManager.removeSession(connectionId, senderId)
                }
            }
            // --------------------------------------------------------
            // 3. UPDATE CHAT THEME
            // --------------------------------------------------------
            put("/theme") {
                val connectionIdStr = call.parameters["connectionId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing connection ID"
                )
                val connectionId = UUID.fromString(connectionIdStr)

                // Ensure the user is authenticated
                val principal = call.principal<JWTPrincipal>()
                val currentUserIdStr = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)

                // Parse the requested theme name
                val request = call.receive<org.ttproject.data.ThemeUpdateRequest>()

                transaction {
                    // Update the theme for this specific connection
                    Connections.update({ Connections.id eq connectionId }) {
                        it[theme] = request.themeName
                    }
                }

                call.respond(HttpStatusCode.OK, "Theme updated successfully")
            }
            // --------------------------------------------------------
            // 4. UPLOAD MULTIPLE CHAT IMAGES
            // --------------------------------------------------------
            post("/images") {
                val connectionId = call.parameters["connectionId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing connection ID")
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val uploadedUrls = mutableListOf<String>()

                try {
                    val bucket = com.google.firebase.cloud.StorageClient.getInstance().bucket()
                    val multipartData = call.receiveMultipart()

                    // Loop through EVERY file attached to the request
                    multipartData.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val fileBytes = part.streamProvider().readBytes()

                            // 👇 THE FIX: Bulletproof MP4 detection using "Magic Bytes"
                            // Every MP4 file contains the string "ftyp" starting at byte 4.
                            val isVideo = fileBytes.size > 8 &&
                                    fileBytes[4].toInt().toChar() == 'f' &&
                                    fileBytes[5].toInt().toChar() == 't' &&
                                    fileBytes[6].toInt().toChar() == 'y' &&
                                    fileBytes[7].toInt().toChar() == 'p'

                            val extension = if (isVideo) "mp4" else "jpg"
                            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

                            val uniqueId = java.util.UUID.randomUUID().toString().take(6)
                            val fileName = "chat_media/${connectionId}_${System.currentTimeMillis()}_$uniqueId.$extension"

                            bucket.create(fileName, fileBytes, mimeType)

                            val encodedFilePath = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString())
                            val publicDownloadUrl = "https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/$encodedFilePath?alt=media"

                            uploadedUrls.add(publicDownloadUrl)
                        }
                        part.dispose()
                    }

                    if (uploadedUrls.isNotEmpty()) {
                        call.respond(HttpStatusCode.OK, mapOf("imageUrls" to uploadedUrls))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No image files provided")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Failed to upload images")
                }
            }

            // --------------------------------------------------------
            // 5. UPLOAD VOICE NOTE
            // --------------------------------------------------------
            post("/voice") {
                val connectionId = call.parameters["connectionId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing connection ID")

                try {
                    val bucket = com.google.firebase.cloud.StorageClient.getInstance().bucket()
                    val multipartData = call.receiveMultipart()
                    var publicDownloadUrl = ""

                    multipartData.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val audioBytes = part.streamProvider().readBytes()
                            val uniqueId = java.util.UUID.randomUUID().toString().take(6)
                            val fileName = "chat_audio/${connectionId}_${System.currentTimeMillis()}_$uniqueId.m4a"

                            bucket.create(fileName, audioBytes, "audio/m4a")

                            val encodedFilePath = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString())
                            publicDownloadUrl = "https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/$encodedFilePath?alt=media"
                        }
                        part.dispose()
                    }

                    if (publicDownloadUrl.isNotBlank()) {
                        call.respond(HttpStatusCode.OK, mapOf("audioUrl" to publicDownloadUrl))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No audio file provided")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Failed to upload audio")
                }
            }
        }
    }
}