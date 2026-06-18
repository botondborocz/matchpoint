package org.ttproject.data

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    PENDING,
    SENT,
    READ
}

@Serializable
data class ReactionDto(
    val userId: String,
    val emoji: String
)

@Serializable
data class MessageDto(
    val id: String,
    val senderId: String,
    val content: String,
    val createdAt: String,
    val replyToMessageId: String? = null,
    val reactions: List<ReactionDto> = emptyList(),
    val status: MessageStatus? = MessageStatus.SENT
)

@Serializable
data class ThemeUpdateRequest(
    val themeName: String
)

@Serializable
data class IncomingMessageDto(
    val type: String,
    val content: String,
    val replyToMessageId: String? = null,
    val targetMessageId: String? = null
)

@Serializable
data class ChatThreadDto(
    val id: String, // This is the connectionId
    val otherUserName: String,
    val otherUserImageUrl: String? = null,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val theme: String
)