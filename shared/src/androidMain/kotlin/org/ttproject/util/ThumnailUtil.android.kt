package org.ttproject.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

actual suspend fun generateVideoThumbnail(videoBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
    try {
        // 1. Write bytes to a temporary file
        val tempFile = File.createTempFile("temp_vid", ".mp4")
        tempFile.writeBytes(videoBytes)

        // 2. Extract the frame using Android's native retriever
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(tempFile.absolutePath)

        // Grab the frame at 1 second (1,000,000 microseconds)
        val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

        // 3. Compress to JPEG
        val stream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, stream)

        // Cleanup
        retriever.release()
        tempFile.delete()

        stream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}