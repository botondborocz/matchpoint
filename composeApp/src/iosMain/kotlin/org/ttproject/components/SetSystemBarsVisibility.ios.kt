package org.ttproject.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.interop.LocalUIViewController
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowLevelStatusBar

@Composable
actual fun SetSystemBarsVisibility(isVisible: Boolean) {
    // 👇 1. Get the exact UIViewController hosting THIS specific Compose Dialog
    val viewController = LocalUIViewController.current

    DisposableEffect(isVisible, viewController) {
        val window = viewController.view.window

        if (window != null) {
            if (isVisible) {
                // Draws natively behind the status bar, leaving clock/battery visible
                window.windowLevel = UIWindowLevelNormal
            } else {
                // Elevates the Dialog ABOVE the status bar, hiding clock/battery completely!
                window.windowLevel = UIWindowLevelStatusBar + 1.0
            }
        }

        onDispose {
            window?.windowLevel = UIWindowLevelNormal
        }
    }
}