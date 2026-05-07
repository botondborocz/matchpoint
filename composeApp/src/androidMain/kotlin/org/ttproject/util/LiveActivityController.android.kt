package org.ttproject.util

import android.content.Context
import android.content.Intent
import android.os.Build

actual object LiveActivityController {

    // Must be set from your MainActivity
    var applicationContext: Context? = null

    actual fun updateProgress(percent: Int, message: String, isComplete: Boolean) {
        val context = applicationContext ?: return

        val intent = Intent(context, AiCoachService::class.java).apply {
            putExtra("PERCENT", percent)
            putExtra("MESSAGE", message)
            putExtra("IS_COMPLETE", isComplete)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}