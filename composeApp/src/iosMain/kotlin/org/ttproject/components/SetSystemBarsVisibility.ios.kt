package org.ttproject.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowLevelStatusBar

@Composable
actual fun SetSystemBarsVisibility(isVisible: Boolean) {
    DisposableEffect(isVisible) {

        // Grab EVERY active window in the iOS app (including hidden CMP Dialog windows)
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()

        if (isVisible) {
            windows.forEach { it.windowLevel = UIWindowLevelNormal }
        } else {
            // Elevate ALL windows above the status bar level to crush the battery/time icons!
            windows.forEach { it.windowLevel = UIWindowLevelStatusBar + 1.0 }
        }

        onDispose {
            // Safety restore
            windows.forEach { it.windowLevel = UIWindowLevelNormal }
        }
    }
}