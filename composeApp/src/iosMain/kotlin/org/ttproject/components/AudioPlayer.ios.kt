package org.ttproject.components

import androidx.compose.runtime.*
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : AudioPlayer {
    private var player: AVPlayer? = null
    private var timeObserver: Any? = null

    override var isPlaying by mutableStateOf(false)
    override var currentPosition by mutableLongStateOf(0L)
    override var duration by mutableLongStateOf(0L)

    override fun play(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        stop()

        val playerItem = AVPlayerItem(uRL = nsUrl)
        player = AVPlayer(playerItem = playerItem)

        // 👇 FIX 1: CMTimeMake directly returns CValue<CMTime>. No wrapper needed!
        val interval = CMTimeMake(value = 1, timescale = 10)

        // KMP passes 'time' as a CValue<CMTime> into this block
        timeObserver = player?.addPeriodicTimeObserverForInterval(interval, null) { time ->
            // 👇 FIX 2: Pass 'time' directly to CMTimeGetSeconds. No .useContents needed!
            val secs = CMTimeGetSeconds(time)
            if (!secs.isNaN()) {
                currentPosition = (secs * 1000).toLong()
            }

            val durationTime = player?.currentItem?.duration
            if (durationTime != null) {
                // durationTime is also a CValue<CMTime>
                val durationSecs = CMTimeGetSeconds(durationTime)
                if (!durationSecs.isNaN()) {
                    duration = (durationSecs * 1000).toLong()
                }
            }

            isPlaying = player?.rate != 0f
        }

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player?.currentItem,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            isPlaying = false
            currentPosition = 0L
            // 👇 FIX 3: kCMTimeZero is already a CValue<CMTime>.
            // If the compiler complains about kCMTimeZero, use CMTimeMakeWithSeconds(0.0, 1)
            player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
        }

        player?.play()
        isPlaying = true
    }

    override fun pause() {
        player?.pause()
        isPlaying = false
    }

    override fun resume() {
        player?.play()
        isPlaying = true
    }

    override fun stop() {
        player?.pause()
        timeObserver?.let {
            player?.removeTimeObserver(it)
            timeObserver = null
        }
        player = null
        isPlaying = false
        currentPosition = 0L
    }

    override fun seekTo(position: Long) {
        // 👇 FIX 4: CMTimeMakeWithSeconds directly returns CValue<CMTime>
        val time = CMTimeMakeWithSeconds(seconds = position / 1000.0, preferredTimescale = 1000)
        player?.seekToTime(time)
        currentPosition = position
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { IosAudioPlayer() }
    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }
    return player
}