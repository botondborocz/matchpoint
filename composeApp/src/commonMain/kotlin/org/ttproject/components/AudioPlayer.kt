package org.ttproject.components

import androidx.compose.runtime.Composable

interface AudioPlayer {
    val isPlaying: Boolean
    val currentPosition: Long // In milliseconds
    val duration: Long        // In milliseconds

    fun play(url: String)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(position: Long)
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer