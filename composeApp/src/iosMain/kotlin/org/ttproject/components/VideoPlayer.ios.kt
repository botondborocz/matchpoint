package org.ttproject.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.AVFoundation.play

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(modifier: Modifier, url: String) {
    // 👇 FIX 1: Explicitly define <AVPlayer> so the compiler doesn't lose the type!
    val player = remember<AVPlayer>(url) { AVPlayer(uRL = NSURL.URLWithString(url)!!) }
    val playerViewController = remember<AVPlayerViewController>(url) { AVPlayerViewController() }

    playerViewController.player = player
    playerViewController.showsPlaybackControls = true

    UIKitView(
        factory = { playerViewController.view },
        modifier = modifier,
        update = {
            // 👇 FIX 2: Safely call play through the controller's specific player reference
            playerViewController.player?.play()
        }
    )
}