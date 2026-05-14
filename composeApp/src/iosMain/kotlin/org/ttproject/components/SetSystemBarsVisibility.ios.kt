package org.ttproject.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowLevelStatusBar
import platform.UIKit.UIWindowScene

@Composable
actual fun SetSystemBarsVisibility(isVisible: Boolean) {
    DisposableEffect(isVisible) {
        // 1. Get the current active iOS window
        val window = (UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene)
            ?.windows?.firstOrNull() as? UIWindow
            ?: UIApplication.sharedApplication.keyWindow

        if (window != null) {
            if (isVisible) {
                // Restore window to normal level (status bar is visible again)
                window.windowLevel = UIWindowLevelNormal
            } else {
                // Elevate the app window ABOVE the iOS status bar layer to hide it
                window.windowLevel = UIWindowLevelStatusBar + 1.0
            }
        }

        onDispose {
            // SAFETY: Always restore the window level when the gallery closes
            window?.windowLevel = UIWindowLevelNormal
        }
    }
}