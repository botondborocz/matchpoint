package org.ttproject

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

// 👇 IMPORTS
// 1. Import your Shared Strings (from the shared module)
import org.ttproject.shared.resources.Res as SharedRes
import org.ttproject.shared.resources.home
import org.ttproject.shared.resources.map
import org.ttproject.shared.resources.ai_coach
import org.ttproject.shared.resources.match
import org.ttproject.shared.resources.messages
import org.ttproject.shared.resources.profile
import org.ttproject.viewmodel.MessagesViewModel

// 2. Import your App Resources (where brain.xml is)
import ttproject.composeapp.generated.resources.Res as AppRes
import ttproject.composeapp.generated.resources.brain
import ttproject.composeapp.generated.resources.flame
import ttproject.composeapp.generated.resources.house
import ttproject.composeapp.generated.resources.map
import ttproject.composeapp.generated.resources.user
import ttproject.composeapp.generated.resources.message_circle
import ttproject.composeapp.generated.resources.zap

// --- THE WRAPPER CLASS ---
sealed interface AppIcon {
    data class Vector(val value: ImageVector) : AppIcon
    data class Drawable(val value: DrawableResource) : AppIcon
}

// 1. The Type-Safe Routes
@Serializable
sealed class NavRoute {
    @Serializable data object Map : NavRoute()
    @Serializable data object Match : NavRoute()
    @Serializable data object Coach : NavRoute()
    @Serializable data object AiChat : NavRoute()
    @Serializable data object Messages : NavRoute()
    @Serializable data object Profile : NavRoute()
    @Serializable data class ChatDetail(val chatId: String, val otherUsername: String, val otherUserImageUrl: String?, val themeName: String) : NavRoute()
}

// 2. Your Navigation Item Data Class
data class NavigationItem(
    val route: NavRoute,
    val title: StringResource,
    val icon: AppIcon,
    val isPro: Boolean = false
)

val MainNavItems = listOf(

    // Standard Icon -> Wrap in AppIcon.Vector
    NavigationItem(NavRoute.Map, SharedRes.string.map, AppIcon.Drawable(AppRes.drawable.map)),
    NavigationItem(NavRoute.Match, SharedRes.string.match, AppIcon.Drawable(AppRes.drawable.zap)),

    // YOUR CUSTOM XML -> Wrap in AppIcon.Drawable
//    NavigationItem(
//        NavRoute.Coach,
//        SharedRes.string.ai_coach,
//        AppIcon.Drawable(AppRes.drawable.brain), // 👈 Uses your xml file
//        isPro = true
//    ),

    // Standard Icon -> Wrap in AppIcon.Vector
    NavigationItem(NavRoute.Messages, SharedRes.string.messages, AppIcon.Drawable(AppRes.drawable.message_circle)),
    NavigationItem(NavRoute.Profile, SharedRes.string.profile, AppIcon.Drawable(AppRes.drawable.user))
)