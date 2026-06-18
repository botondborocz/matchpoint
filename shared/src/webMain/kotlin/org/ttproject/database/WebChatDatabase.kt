package org.ttproject.database

import org.ttproject.data.ChatThreadDto
import org.ttproject.data.MessageDto

class WebChatDatabase : ChatDatabase {
    override fun saveThreads(threads: List<ChatThreadDto>) {}
    override fun getThreads(): List<ChatThreadDto> = emptyList()
    override fun clearThreads() {}

    override fun saveMessages(connectionId: String, messages: List<MessageDto>) {}
    override fun getMessages(connectionId: String): List<MessageDto> = emptyList()
    override fun clearMessages(connectionId: String) {}

    override fun getPendingMessages(): List<PendingMessage> = emptyList()
    override fun savePendingMessages(pending: List<PendingMessage>) {}
    override fun saveTempFile(fileName: String, bytes: ByteArray): String = ""
}
