package org.ttproject.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val exoPlayer = ExoPlayer.Builder(context).build()
    override var isPlaying by mutableStateOf(false)
    override var currentPosition by mutableLongStateOf(0L)
    override var duration by mutableLongStateOf(0L)

    private var job: Job? = null

    override fun play(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
        startTracking()
    }

    override fun pause() { exoPlayer.pause(); isPlaying = false }
    override fun resume() { exoPlayer.play(); isPlaying = true }
    override fun stop() { exoPlayer.stop(); isPlaying = false; currentPosition = 0L }
    override fun seekTo(position: Long) { exoPlayer.seekTo(position) }

    private fun startTracking() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                isPlaying = exoPlayer.isPlaying
                delay(100)
            }
        }
    }

    fun release() = exoPlayer.release()
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val context = LocalContext.current
    val player = remember { AndroidAudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}