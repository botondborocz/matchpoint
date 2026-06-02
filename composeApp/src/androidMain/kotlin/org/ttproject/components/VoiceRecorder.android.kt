package org.ttproject.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

class VoiceRecorderImpl(
    private val context: Context,
    private val requestPermission: () -> Unit
) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    override fun startRecording(onStart: () -> Unit) {
        // 1. Check permissions first!
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermission()
            return
        }

        try {
            // 2. Create temp file
            audioFile = File.createTempFile("voice_note", ".m4a", context.cacheDir)

            // 3. Configure MediaRecorder
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            // 4. Tell the UI to start the timer!
            onStart()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopRecording(): ByteArray? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null

            // Read the bytes and delete the temp file
            val bytes = audioFile?.readBytes()
            audioFile?.delete()
            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun cancelRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {}
        recorder = null
        audioFile?.delete()
    }
}

@Composable
actual fun rememberVoiceRecorder(onPermissionDenied: () -> Unit): VoiceRecorder {
    val context = LocalContext.current

    // The Compose-safe permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) onPermissionDenied()
    }

    return remember {
        VoiceRecorderImpl(
            context = context,
            requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )
    }
}