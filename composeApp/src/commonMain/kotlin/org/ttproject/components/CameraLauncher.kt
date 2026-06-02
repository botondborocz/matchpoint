package org.ttproject.components

import androidx.compose.runtime.Composable

expect class CameraLauncher {
    fun launch()
}

@Composable
expect fun rememberCameraLauncher(onResult: (ByteArray?) -> Unit): CameraLauncher

@Composable
expect fun rememberVideoLauncher(onResult: (ByteArray?) -> Unit): CameraLauncher