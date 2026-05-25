package org.ttproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import org.ttproject.components.NativeGalleryLauncher
import org.ttproject.di.initKoin

private var isKoinInitialized = false

fun TabViewController(
    tabName: String,
    galleryLauncher: NativeGalleryLauncher,
    onTabChangedByCompose: (String) -> Unit,
    onThemeChangedByCompose: (String) -> Unit
) = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
){
    if (!isKoinInitialized) {
        initKoin()
        isKoinInitialized = true
    }

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
        }
    )
}