package org.ttproject.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.ttproject.MainActivity
import org.ttproject.R
import org.ttproject.util.NotificationEventBus
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import kotlin.math.abs

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val chatId = remoteMessage.data["chatId"] ?: return
        val senderName = remoteMessage.data["senderName"] ?: "New Message"
        val senderImageUrl = remoteMessage.data["senderImageUrl"] // 👈 Ensure your backend sends this!

        val rawText = remoteMessage.data["text"] ?: ""

        // 👇 1. Parse Image/Video Tags for the Notification Text!
        val text = if (rawText.startsWith("[IMAGE")) "📸 Photo" else rawText

        // ALWAYS trigger the UI refresh!
        NotificationEventBus.triggerRefresh()

        // 👇 Use the thread-safe check!
        if (isAppInForeground(this)) {
            return // Exit early, no system banner needed
        }

        // ---------------------------------------------------------
        // 👇 Everything below this line ONLY runs if the app is backgrounded/closed
        // ---------------------------------------------------------

        // Check Permissions (Prevents crashes on Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "chat_messages"

        // Create Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Chat Messages", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // Create the Deep Link Intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatId", chatId)
        }

        val safeNotificationId = abs(chatId.hashCode())

        val pendingIntent = PendingIntent.getActivity(
            this,
            safeNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var messagingStyle: NotificationCompat.MessagingStyle? = null

        // Querying active notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifications = notificationManager.activeNotifications
            for (activeNotification in activeNotifications) {
                if (activeNotification.id == safeNotificationId) {
                    messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeNotification.notification)
                    break
                }
            }
        }

        if (messagingStyle == null) {
            messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
        }

        // 👇 2. Create the Avatar Bitmap (Either downloaded or an Initial)
        val avatarBitmap = getBitmapFromUrl(senderImageUrl) ?: createInitialBitmap(senderName)
        val avatarIcon = IconCompat.createWithBitmap(avatarBitmap)

        // 👇 3. Attach the Icon to the Person!
        val sender = Person.Builder()
            .setName(senderName)
            .setIcon(avatarIcon)
            .build()

        messagingStyle.addMessage(text, Date().time, sender)

//        val fullColorLogo = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setLargeIcon(avatarBitmap)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(safeNotificationId, notification)
    }

    // Put this helper function at the bottom of your class
    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName

        for (appProcess in appProcesses) {
            if (appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("🔄 Background FCM Token refreshed: $token")
    }

    // ------------------------------------------------------------------------
    // 👇 HELPER FUNCTIONS FOR AVATARS
    // ------------------------------------------------------------------------

    /**
     * Downloads an image URL into a Bitmap.
     * (Safe to run here because Firebase executes onMessageReceived on a background thread!)
     */
    private fun getBitmapFromUrl(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a circular bitmap with the user's first initial if they have no picture.
     */
    private fun createInitialBitmap(name: String): Bitmap {
        val size = 120 // Standard crisp size for notification icons
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw Background Circle
        val paint = Paint().apply {
            color = Color.parseColor("#FF5722") // Your Accent Orange Color
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Draw Initial Text
        val initial = if (name.isNotBlank()) name.first().uppercase() else "?"
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size / 2.2f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // Center text vertically
        val xPos = size / 2f
        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(initial, xPos, yPos, textPaint)

        return bitmap
    }
}