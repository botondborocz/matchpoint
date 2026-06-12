package org.ttproject.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ttproject.data.ChatThreadDto
import org.ttproject.data.MessageDto

class AndroidChatDatabase(private val context: Context) : ChatDatabase, SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "chat.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_CACHE = "chat_cache"
        private const val COLUMN_KEY = "key"
        private const val COLUMN_VALUE = "value"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_CACHE ($COLUMN_KEY TEXT PRIMARY KEY, $COLUMN_VALUE TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHE")
        onCreate(db)
    }

    private fun getVal(key: String): String? {
        val db = readableDatabase
        val cursor = db.query(TABLE_CACHE, arrayOf(COLUMN_VALUE), "$COLUMN_KEY = ?", arrayOf(key), null, null, null)
        return cursor.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    private fun setVal(key: String, value: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_VALUE, value)
        }
        db.insertWithOnConflict(TABLE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun deleteVal(key: String) {
        val db = writableDatabase
        db.delete(TABLE_CACHE, "$COLUMN_KEY = ?", arrayOf(key))
    }

    override fun saveThreads(threads: List<ChatThreadDto>) {
        setVal("threads", json.encodeToString(threads))
    }

    override fun getThreads(): List<ChatThreadDto> {
        val data = getVal("threads") ?: return emptyList()
        return try { json.decodeFromString(data) } catch (e: Exception) { emptyList() }
    }

    override fun clearThreads() {
        deleteVal("threads")
    }

    override fun saveMessages(connectionId: String, messages: List<MessageDto>) {
        setVal("messages_$connectionId", json.encodeToString(messages))
    }

    override fun getMessages(connectionId: String): List<MessageDto> {
        val data = getVal("messages_$connectionId") ?: return emptyList()
        return try { json.decodeFromString(data) } catch (e: Exception) { emptyList() }
    }

    override fun clearMessages(connectionId: String) {
        deleteVal("messages_$connectionId")
    }

    override fun getPendingMessages(): List<PendingMessage> {
        val data = getVal("pending_messages") ?: return emptyList()
        return try { json.decodeFromString(data) } catch (e: Exception) { emptyList() }
    }

    override fun savePendingMessages(pending: List<PendingMessage>) {
        setVal("pending_messages", json.encodeToString(pending))
    }

    override fun saveTempFile(fileName: String, bytes: ByteArray): String {
        val file = java.io.File(context.cacheDir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
