package org.ttproject.components

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun SetSystemBarsVisibility(isVisible: Boolean) {
    val view = LocalView.current

    // Look for the Dialog's specific window
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window

    val context = LocalContext.current
    val activityWindow = (context as? Activity)?.window

    val window = dialogWindow ?: activityWindow ?: return

    DisposableEffect(isVisible, window, view) {

        if (dialogWindow != null) {
            // 👇 1. The Silver Bullet: Forces the window to ignore ALL default Android boundaries
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

            // 👇 2. Make the native bars completely transparent so Compose's black background can draw into them
            dialogWindow.statusBarColor = Color.TRANSPARENT
            dialogWindow.navigationBarColor = Color.TRANSPARENT

            // 👇 3. Strip default margins and backgrounds
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            dialogWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val controller = WindowCompat.getInsetsController(window, view)

        if (isVisible) {
            // Unzoomed: Show everything
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            // Zoomed: Hide everything cleanly
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // Always restore safely
            controller.show(WindowInsetsCompat.Type.systemBars())

            // Cleanup the flag when destroyed just in case
            if (dialogWindow != null) {
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            }
        }
    }
}