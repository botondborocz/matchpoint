package org.ttproject.database

import kotlinx.serialization.Serializable
import org.ttproject.data.ChatThreadDto
import org.ttproject.data.MessageDto

@Serializable
data class PendingMessage(
    val id: String,
    val connectionId: String,
    val text: String,
    val replyToId: String?,
    val mediaType: String, // "TEXT", "IMAGE", "VIDEO", "VOICE"
    val mediaBytes: List<ByteArray>? = null,
    val createdAt: String
)

interface ChatDatabase {
    fun saveThreads(threads: List<ChatThreadDto>)
    fun getThreads(): List<ChatThreadDto>
    fun clearThreads()

    fun saveMessages(connectionId: String, messages: List<MessageDto>)
    fun getMessages(connectionId: String): List<MessageDto>
    fun clearMessages(connectionId: String)

    fun getPendingMessages(): List<PendingMessage>
    fun savePendingMessages(pending: List<PendingMessage>)
    fun saveTempFile(fileName: String, bytes: ByteArray): String
}
