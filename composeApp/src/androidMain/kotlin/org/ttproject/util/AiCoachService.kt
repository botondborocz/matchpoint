package org.ttproject.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AiCoachService : Service() {

    private val CHANNEL_ID = "ai_coach_channel"
    private val NOTIFICATION_ID = 888

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val percent = intent?.getIntExtra("PERCENT", 0) ?: 0
        val message = intent?.getStringExtra("MESSAGE") ?: "Processing..."
        val isComplete = intent?.getBooleanExtra("IS_COMPLETE", false) ?: false

        createChannel()

        val notification = buildNotification(percent, message, isComplete)

        // 👇 Explicitly declare Media Processing to satisfy Android 14/15/16 rules
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (isComplete) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(percent: Int, message: String, isComplete: Boolean): Notification {
        // 1. The Click Action (Required for Now Bar)
        val clickIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply {
                setClassName(packageName, "$packageName.MainActivity")
            }
        clickIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. The Rock-Solid Builder
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AI Auto-Cutter")
            .setContentText(message)
            .setContentIntent(pendingIntent) // 👈 VIP Pass 1: Clickable
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 👈 VIP Pass 2: High enough priority
            .setOngoing(!isComplete)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 👈 VIP Pass 3: Show on Lock Screen
            .setCategory(NotificationCompat.CATEGORY_PROGRESS) // 👈 VIP Pass 4: Samsung's magic word
            .setProgress(100, percent, false) // 👈 THE ACTUAL PROGRESS BAR (Missing from last screenshot!)

        // Remove the progress bar and let user swipe away when done
        if (isComplete) {
            builder.setProgress(0, 0, false)
            builder.setOngoing(false)
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Coach Tasks",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}