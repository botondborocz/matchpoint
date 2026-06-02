package org.ttproject.components

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

actual class CameraLauncher(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

@Composable
actual fun rememberCameraLauncher(onResult: (ByteArray?) -> Unit): CameraLauncher {
    // We must hold a reference to the delegate so it isn't garbage collected
    val delegate = remember {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>
            ) {
                val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                if (image != null) {
                    // Compress to JPEG and convert to ByteArray
                    val imageData = UIImageJPEGRepresentation(image, 0.8)
                    val bytes = imageData?.toByteArray()
                    onResult(bytes)
                } else {
                    onResult(null)
                }
                picker.dismissViewControllerAnimated(true, null)
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                onResult(null)
                picker.dismissViewControllerAnimated(true, null)
            }
        }
    }

    return remember {
        CameraLauncher(
            onLaunch = {
                val picker = UIImagePickerController()
                picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                picker.delegate = delegate

                // Present the camera on the top-most view controller
                UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
                    picker, animated = true, completion = null
                )
            }
        )
    }
}

@Composable
actual fun rememberVideoLauncher(onResult: (ByteArray?) -> Unit): CameraLauncher {
    val delegate = remember {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>
            ) {
                // iOS returns video as a file URL
                val videoUrl = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL

                if (videoUrl != null) {
                    val videoData = NSData.dataWithContentsOfURL(videoUrl)
                    onResult(videoData?.toByteArray())
                } else {
                    onResult(null)
                }
                picker.dismissViewControllerAnimated(true, null)
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                onResult(null)
                picker.dismissViewControllerAnimated(true, null)
            }
        }
    }

    return remember {
        CameraLauncher(
            onLaunch = {
                val picker = UIImagePickerController()
                picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera

                // 👇 Force the camera into Video mode!
                picker.mediaTypes = listOf("public.movie")
                picker.videoQuality = UIImagePickerControllerQualityTypeHigh
                picker.delegate = delegate

                UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
                    picker, animated = true, completion = null
                )
            }
        )
    }
}

// Helper extension to convert Apple's NSData into a Kotlin ByteArray
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    return ByteArray(length.toInt()).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}