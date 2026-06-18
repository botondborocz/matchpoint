package org.ttproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import org.ttproject.components.NativeGalleryLauncher
import org.ttproject.di.KoinHelper

private var isKoinInitialized = false

fun TabViewController(
    tabName: String,
    galleryLauncher: NativeGalleryLauncher,
    onTabChangedByCompose: (String) -> Unit,
    onThemeChangedByCompose: (String) -> Unit,
    onSubScreenVisibilityChanged: (Boolean) -> Unit
) = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
){
    KoinHelper.safeInitKoin()


    val fixedRoute = when (tabName) {
        "match" -> NavRoute.Match
        "coach" -> NavRoute.Coach
        "messages" -> NavRoute.Messages
        "profile" -> NavRoute.Profile
        else -> NavRoute.Map
    }

    App(
        galleryLauncher = galleryLauncher,
        externalTabRoute = fixedRoute,
        onTabChangedBySystem = { route ->
            val stringName = when (route) {
                NavRoute.Match -> "match"
                NavRoute.Coach -> "coach"
                NavRoute.Messages -> "messages"
                NavRoute.Profile -> "profile"
                else -> "map"
            }
            onTabChangedByCompose(stringName)
        },
        onThemeStyleChanged = { themeHex ->
            onThemeChangedByCompose(themeHex)
        },
        onSubScreenVisibilityChanged = { isSubScreen ->
            onSubScreenVisibilityChanged(isSubScreen) // 👈 Forward up to Swift
        }
    )
}

fun setNotificationListener(listener: (String, String) -> Unit) {
    org.ttproject.components.PlatformNotificationManager.setListener(listener)
}