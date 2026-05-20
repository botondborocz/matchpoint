package org.ttproject

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import org.ttproject.components.NativeGalleryLauncher
import org.ttproject.di.initKoin

private var isKoinInitialized = false

fun MainViewController(galleryLauncher: NativeGalleryLauncher) = ComposeUIViewController(
    configure = {
        // 👇 THE FIX: Tell iOS to stop shifting the canvas!
        // This lets our Compose WindowInsets handle 100% of the movement,
        // which perfectly syncs it with the native keyboard speed.
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
){
    if (!isKoinInitialized) {
        initKoin()
        isKoinInitialized = true
    }
    App(galleryLauncher = galleryLauncher)
}