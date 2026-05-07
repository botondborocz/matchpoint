package org.ttproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.ttproject.di.appModule
import android.content.pm.ActivityInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import org.ttproject.util.LiveActivityController

class MainActivity : ComponentActivity() {
    private var pendingChatId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LiveActivityController.applicationContext = this.applicationContext

        val smallestWidth = resources.configuration.smallestScreenWidthDp
        if (smallestWidth < 600) {
            // It's a phone (or small foldable front screen)! Lock to Portrait.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            // It's a tablet! Let it rotate freely.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MainActivity)
                modules(appModule)
            }
        }

        // 👇 ADD THIS TO CREATE THE HIGH PRIORITY CHANNEL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "chat_messages"
            val channelName = "Chat Messages"
            // IMPORTANCE_HIGH is the magic word that makes it drop down!
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for new chat messages"
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        pendingChatId = intent.extras?.getString("chatId")

        setContent {
            App(
                pendingChatId = pendingChatId,
                onChatConsumed = { pendingChatId = null } // Reset after navigating
            )
        }
    }

    // 👇 2. Check if the app was ALREADY OPEN in the background when clicked
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingChatId = intent.extras?.getString("chatId")
    }
}



@Preview
@Composable
fun AppAndroidPreview() {
    App()
}