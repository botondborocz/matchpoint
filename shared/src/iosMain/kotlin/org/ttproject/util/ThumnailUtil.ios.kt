package org.ttproject.util

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.AVFoundation.*
import platform.CoreGraphics.* // 👈 THE FIX: Import CoreGraphics!
import platform.CoreMedia.CMTimeMake
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual suspend fun generateVideoThumbnail(videoBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
    try {
        // 1. Convert ByteArray to NSData
        val nsData = videoBytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), videoBytes.size.toULong())
        }

        // 2. Write to temp file
        val tempPath = NSTemporaryDirectory() + NSUUID().UUIDString() + ".mp4"
        nsData.writeToFile(tempPath, atomically = true)

        // 3. Extract frame using AVFoundation
        val url = NSURL.fileURLWithPath(tempPath)
        val asset = AVURLAsset.assetWithURL(url)
        val generator = AVAssetImageGenerator(asset)
        generator.appliesPreferredTrackTransform = true

        val time = CMTimeMake(1, 1) // 1 second in
        val cgImage = generator.copyCGImageAtTime(time, actualTime = null, error = null)

        // 4. Convert to JPEG ByteArray
        val uiImage = cgImage?.let { UIImage.imageWithCGImage(it) }
        val jpegData = uiImage?.let { UIImageJPEGRepresentation(it, 0.8) }

        val thumbBytes = jpegData?.let { data ->
            ByteArray(data.length.toInt()).apply {
                usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
            }
        }

        // Cleanup
        NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
        thumbBytes
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}