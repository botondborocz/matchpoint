package org.ttproject.database

import com.liftric.kvault.KVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ttproject.data.ChatThreadDto
import org.ttproject.data.MessageDto
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ExperimentalForeignApi

class IosChatDatabase : ChatDatabase {
    private val vault = KVault()
    private val json = Json { ignoreUnknownKeys = true }

    override fun saveThreads(threads: List<ChatThreadDto>) {
        vault.set("threads", json.encodeToString(threads))
    }

    override fun getThreads(): List<ChatThreadDto> {
        val data = vault.string("threads") ?: return emptyList()
        return try { json.decodeFromString(data) } catch (e: Exception) { emptyList() }
    }

    override fun clearThreads() {
        vault.deleteObject("threads")
    }

    override fun saveMessages(connectionId: String, messages: List<MessageDto>) {
        vault.set("messages_$connectionId", json.encodeToString(messages))
    }

    override fun getMessages(connectionId: String): List<MessageDto> {
        val data = vault.string("messages_$connectionId") ?: return emptyList()
        return try { json.decodeFromString(data) } catch (e: Exception) { emptyList() }
    }

    override fun clearMessages(connectionId: String) {
        vault.deleteObject("messages_$connectionId")
    }

    override fun getPendingMessages(): List<PendingMessage> {
        val data = vault.string("pending_messages") ?: return emptyList()
        return try { json.decodeFromString(data) } catch (e: Exception) { emptyList() }
    }

    override fun savePendingMessages(pending: List<PendingMessage>) {
        vault.set("pending_messages", json.encodeToString(pending))
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun saveTempFile(fileName: String, bytes: ByteArray): String {
        val cacheDir = platform.Foundation.NSTemporaryDirectory()
        val filePath = cacheDir + fileName
        val file = platform.posix.fopen(filePath, "wb")
        if (file != null) {
            bytes.usePinned { pinned ->
                platform.posix.fwrite(pinned.addressOf(0), 1.toULong(), bytes.size.toULong(), file)
            }
            platform.posix.fclose(file)
        }
        return filePath
    }
}
