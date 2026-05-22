package org.ttproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import org.ttproject.components.NativeGalleryLauncher
import org.ttproject.di.initKoin

// 👇 The State Container that bridges both runtimes reactively
class AppNavigationHolder(
    initialTab: String,
    val onTabChangedByCompose: (String) -> Unit
) {
    var currentTabBySystem by mutableStateOf(initialTab)
}

private var isKoinInitialized = false

fun MainViewController(
    galleryLauncher: NativeGalleryLauncher,
    navHolder: AppNavigationHolder // 👈 Accept the holder instance here
) = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
){
    if (!isKoinInitialized) {
        initKoin()
        isKoinInitialized = true
    }

    // Convert the active string state to your strongly-typed NavRoute enum
    val externalRoute = when (navHolder.currentTabBySystem) {
        "match" -> NavRoute.Match
        "coach" -> NavRoute.Coach
        "messages" -> NavRoute.Messages
        "profile" -> NavRoute.Profile
        else -> NavRoute.Map
    }

    App(
        galleryLauncher = galleryLauncher,
        externalTabRoute = externalRoute,
        onTabChangedBySystem = { route ->
            val stringName = when (route) {
                NavRoute.Match -> "match"
                NavRoute.Coach -> "coach"
                NavRoute.Messages -> "messages"
                NavRoute.Profile -> "profile"
                else -> "map"
            }
            // Prevent recursive update loops if state matches
            if (navHolder.currentTabBySystem != stringName) {
                navHolder.onTabChangedByCompose(stringName)
            }
        }
    )
}