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
import org.ttproject.components.NativeGalleryLauncher // 👈 Ensure this import is visible
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
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MainActivity)
                modules(appModule)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "chat_messages"
            val channelName = "Chat Messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for new chat messages"
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        pendingChatId = intent.extras?.getString("chatId")
        handleIntent(intent)

        // 👇 1. Create a safe anonymous stub for the gallery launcher interface on Android
        val androidGalleryLauncher = object : NativeGalleryLauncher {
            override fun openGallery(
                images: List<String>,
                initialIndex: Int,
                isMineList: List<Boolean>,
                onDelete: (String) -> Unit,
                onReport: (String, String) -> Unit
            ) {
                // If you implement a full screen gallery screen inside commonMain Compose later,
                // this Android trigger block can safely remain an empty stub.
            }
        }

        setContent {
            App(
                pendingChatId = pendingChatId,
                onChatConsumed = { pendingChatId = null },
                galleryLauncher = androidGalleryLauncher,       // 👈 Pass your Android implementation stub
                externalTabRoute = null,                        // 👈 Android controls routing internally via Compose
                onTabChangedBySystem = {},                      // 👈 No-op on Android
                onThemeStyleChanged = {},                       // 👈 No-op on Android
                onSubScreenVisibilityChanged = {}               // 👈 No-op on Android
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingChatId = intent.extras?.getString("chatId")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val dataString = data.toString()
        org.ttproject.util.AuthEventBus.handleDeeplink(dataString)
    }
}