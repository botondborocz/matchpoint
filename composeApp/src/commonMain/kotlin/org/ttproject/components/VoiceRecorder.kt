package org.ttproject.components

import androidx.compose.runtime.Composable

interface VoiceRecorder {
    // We pass a callback so the UI only switches to "Recording" IF the user grants permission!
    fun startRecording(onStart: () -> Unit)
    fun stopRecording(): ByteArray?
    fun cancelRecording()
}

@Composable
expect fun rememberVoiceRecorder(onPermissionDenied: () -> Unit): VoiceRecorder