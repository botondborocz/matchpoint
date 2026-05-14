package org.ttproject.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarAnimationFade

@Composable
actual fun SetSystemBarsVisibility(isVisible: Boolean) {
    DisposableEffect(isVisible) {
        // 👇 Dynamically hide or show the iOS status bar with a smooth fade
        UIApplication.sharedApplication.setStatusBarHidden(
            hidden = !isVisible,
            withAnimation = UIStatusBarAnimationFade
        )

        onDispose {
            // SAFETY: Always restore the status bar when the gallery closes!
            UIApplication.sharedApplication.setStatusBarHidden(
                hidden = false,
                withAnimation = UIStatusBarAnimationFade
            )
        }
    }
}