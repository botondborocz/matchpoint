package org.ttproject

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.components.DesktopSidebar
import org.ttproject.components.MobileBottomNavPill
import org.ttproject.components.MobileTopBar
import org.ttproject.components.NativeGalleryLauncher
import org.ttproject.data.TokenStorage
import org.ttproject.icon.AppIconManager
import org.ttproject.icon.PremiumAppIcon
import org.ttproject.screens.AiHubScreen
import org.ttproject.screens.ChatDetailScreen
import org.ttproject.screens.DummyAiChatPlayground
import org.ttproject.screens.LoginScreen
import org.ttproject.screens.MapScreen
import org.ttproject.screens.MatchScreen
import org.ttproject.screens.MessagesScreen
import org.ttproject.screens.ProfileScreen
import org.ttproject.screens.RegisterScreen
import org.ttproject.util.LocalThemeMode
import org.ttproject.util.SetStatusBarColors
import org.ttproject.util.ThemeMode
import org.ttproject.util.changePlatformLanguage
import org.ttproject.viewmodel.ChatViewModel

enum class AuthRoute {
    Login, Register
}

@Serializable
object HomeBase

val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun App(
    pendingChatId: String? = null,
    onChatConsumed: () -> Unit = {},
    galleryLauncher: NativeGalleryLauncher,
    externalTabRoute: NavRoute? = null,
    onTabChangedBySystem: (NavRoute) -> Unit = {}
) {
    val tokenStorage: TokenStorage = koinInject()
    val appIconManager: AppIconManager = koinInject()
    val rootNavController = rememberNavController()

    // 👇 1. ADDED: State to track if the Map's floating buttons are visible
    var isMapNavBarVisible by remember { mutableStateOf(true) }

    var currentTabRoute by remember { mutableStateOf<NavRoute>(NavRoute.Map) }
    val loadedTabs = remember { mutableStateListOf<NavRoute>(NavRoute.Map) }
    var isLoggedIn by remember { mutableStateOf(tokenStorage.getToken() != null) }
    var playMessagesAnimation by remember { mutableStateOf(true) }

    // 2. NEW: Listen to tab changes coming from native iOS
    LaunchedEffect(externalTabRoute) {
        if (externalTabRoute != null && externalTabRoute != currentTabRoute) {
            currentTabRoute = externalTabRoute
            if (!loadedTabs.contains(externalTabRoute)) {
                loadedTabs.add(externalTabRoute)
            }
        }
    }

    // 3. UPDATED: Notify native iOS when an internal tab change happens
    val onTabNavigate: (NavRoute) -> Unit = { targetRoute ->
        if (targetRoute == NavRoute.Messages) playMessagesAnimation = true
        currentTabRoute = targetRoute
        if (!loadedTabs.contains(targetRoute)) {
            loadedTabs.add(targetRoute)
        }
        onTabChangedBySystem(targetRoute) // 👈 NEW
    }

    val systemLanguage = Locale.current.language
    val supportedSystemLanguage = if (systemLanguage == "hu") "hu" else "en"

    var currentLanguage by remember { mutableStateOf(tokenStorage.getLanguage() ?: supportedSystemLanguage) }
    var isLanguageApplied by remember { mutableStateOf(false) }

    LaunchedEffect(currentLanguage) {
        changePlatformLanguage(currentLanguage)
        isLanguageApplied = true
    }

    if (!isLanguageApplied) {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.Background))
        return
    }

    var currentAuthRoute by remember { mutableStateOf(AuthRoute.Login) }

    LaunchedEffect(pendingChatId) {
        if (pendingChatId != null) {
            onTabNavigate(NavRoute.Messages)
            rootNavController.navigate(NavRoute.ChatDetail(pendingChatId, "Chat", null, "Default"))
            onChatConsumed()
        }
    }

    var currentThemeMode by remember {
        mutableStateOf(
            when (tokenStorage.getThemeMode()) {
                "light" -> ThemeMode.Light
                "dark" -> ThemeMode.Dark
                else -> ThemeMode.System
            }
        )
    }

    var currentThemeStyle by remember {
        mutableStateOf(
            when (tokenStorage.getAppTheme()) {
                "vip" -> AppThemeStyle.VIP
                "adrenalin" -> AppThemeStyle.ADRENALIN
                "matrix" -> AppThemeStyle.MATRIX
                "arctic" -> AppThemeStyle.ARCTIC
                "neon" -> AppThemeStyle.NEON
                "stealth" -> AppThemeStyle.STEALTH
                "tokyo" -> AppThemeStyle.TOKYO
                "classic" -> AppThemeStyle.CLASSIC
                "royal" -> AppThemeStyle.ROYAL
                "volt" -> AppThemeStyle.VOLT
                else -> AppThemeStyle.DEFAULT
            }
        )
    }

    // 👇 NEW: Load the saved AppIcon from storage, defaulting to DEFAULT
    var currentAppIcon by remember {
        mutableStateOf(
            PremiumAppIcon.entries.find { it.alias == tokenStorage.getAppIcon() } ?: PremiumAppIcon.DEFAULT
        )
    }

    val isSystemDark = isSystemInDarkTheme()
    val isCurrentlyDark = when (currentThemeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemDark
    }

    SetStatusBarColors(
        isDark = isCurrentlyDark,
        isSystemDefault = currentThemeMode == ThemeMode.System
    )

    key(currentLanguage) {
        CompositionLocalProvider(
            LocalThemeMode provides currentThemeMode,
            LocalIsDarkTheme provides isCurrentlyDark,
            LocalAppThemeStyle provides currentThemeStyle
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isMobile = maxWidth < 600.dp

                Row(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {

                    if (!isMobile) {
                        DesktopSidebar(currentRoute = currentTabRoute, onNavigate = onTabNavigate)
                    }

                    NavHost(
                        navController = rootNavController,
                        startDestination = HomeBase,
                        modifier = Modifier.weight(1f).clipToBounds(),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None }
                    ) {
                        // --- SCREEN A: THE HOME BASE (Holds the Tabs) ---
                        composable<HomeBase>(
                            exitTransition = {
                                if (targetState.destination.hasRoute(NavRoute.ChatDetail::class) ||
                                    targetState.destination.hasRoute(NavRoute.AiChat::class)) {
                                    if (isIosPlatform()) slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300, easing = LinearEasing))
                                    else ExitTransition.None
                                } else null
                            },
                            popEnterTransition = {
                                if (initialState.destination.hasRoute(NavRoute.ChatDetail::class) ||
                                    initialState.destination.hasRoute(NavRoute.AiChat::class)) {
                                    if (isIosPlatform()) slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300, easing = LinearEasing))
                                    else EnterTransition.None
                                } else null
                            }
                        ) {
                            Scaffold(
                                topBar = {
                                    if (isMobile && currentTabRoute != NavRoute.Map && currentTabRoute != NavRoute.Messages && currentTabRoute != NavRoute.Match && currentTabRoute != NavRoute.Profile && currentTabRoute != NavRoute.Coach) {
                                        MobileTopBar()
                                    }
                                },
                                containerColor = Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) { innerPadding ->

                                val bottomNavHeight = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                val frozenBottomPadding = if (isMobile) bottomNavHeight else 0.dp

                                Box(modifier = Modifier.fillMaxSize()) {

                                    // --- LAYER 1: THE PERSISTENT TABS ---
                                    loadedTabs.forEach { route ->
                                        val isVisible = currentTabRoute == route

                                        val alpha by animateFloatAsState(
                                            targetValue = if (isVisible) 1f else 0f,
                                            animationSpec = tween(200),
                                            label = "tabAlpha"
                                        )

                                        val zIndex = if (isVisible) 1f else 0f

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .alpha(alpha)
                                                .zIndex(zIndex)
                                                .background(if (route != NavRoute.Map) AppColors.Background else Color.Transparent)
                                        ) {
                                            when (route) {
                                                NavRoute.Map -> {
                                                    val systemNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                                    Box(modifier = Modifier.fillMaxSize()) {
                                                        MapScreen(
                                                            bottomNavHeight = frozenBottomPadding,
                                                            systemNavHeight = systemNavPadding, // 👇 FIX 2: Restored the dynamic calculation (removed 60.dp)
                                                            onNavBarVisibilityChange = { isVisible ->
                                                                isMapNavBarVisible = isVisible
                                                            }
                                                        )
                                                    }
                                                }
                                                NavRoute.Match -> {
                                                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(bottom = frozenBottomPadding)) {
                                                        MatchScreen(
                                                            onNavigateToLogin = {
                                                                currentAuthRoute = AuthRoute.Login
                                                                onTabNavigate(NavRoute.Profile)
                                                            },
                                                            onNavigateToMessages = { onTabNavigate(NavRoute.Messages) }
                                                        )
                                                    }
                                                }
                                                NavRoute.Coach -> {
                                                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(bottom = frozenBottomPadding)) {
                                                        AiHubScreen(
                                                            onNavigateToAiChat = { rootNavController.navigate(NavRoute.AiChat) },
                                                            onNavigateToVideoAnalysis = {}
                                                        )
                                                    }
                                                }
                                                NavRoute.Messages -> {
                                                    MessagesScreen(
                                                        playAnimation = playMessagesAnimation,
                                                        bottomNavPadding = frozenBottomPadding,
                                                        onNavigateToChat = { chatId, otherUsername, otherUserImageUrl, themeName ->
                                                            playMessagesAnimation = false
                                                            rootNavController.navigate(NavRoute.ChatDetail(chatId, otherUsername, otherUserImageUrl, themeName))
                                                        }
                                                    )
                                                }
                                                NavRoute.Profile -> {
                                                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                                        if (isLoggedIn) {
                                                            ProfileScreen(
                                                                currentLanguage = currentLanguage,
                                                                currentThemeMode = currentThemeMode,
                                                                currentAppIcon = currentAppIcon,
                                                                onLogoutClick = {
                                                                    tokenStorage.clearToken()
                                                                    tokenStorage.clearLanguage()
                                                                    isLoggedIn = false
                                                                },
                                                                onChangeLanguage = { newLangCode ->
                                                                    tokenStorage.saveLanguage(newLangCode)
                                                                    currentLanguage = newLangCode
                                                                    changePlatformLanguage(newLangCode)
                                                                },
                                                                onChangeTheme = { newThemeMode ->
                                                                    tokenStorage.saveThemeMode(
                                                                        when (newThemeMode) {
                                                                            ThemeMode.Light -> "light"
                                                                            ThemeMode.Dark -> "dark"
                                                                            ThemeMode.System -> "system"
                                                                        }
                                                                    )
                                                                    currentThemeMode = newThemeMode
                                                                },
                                                                onChangeAppThemeStyle = { newStyle ->
                                                                    tokenStorage.saveAppTheme(
                                                                        when (newStyle) {
                                                                            AppThemeStyle.DEFAULT -> "default"
                                                                            AppThemeStyle.VIP -> "vip"
                                                                            AppThemeStyle.ADRENALIN -> "adrenalin"
                                                                            AppThemeStyle.MATRIX -> "matrix"
                                                                            AppThemeStyle.ARCTIC -> "arctic"
                                                                            AppThemeStyle.NEON -> "neon"
                                                                            AppThemeStyle.STEALTH -> "stealth"
                                                                            AppThemeStyle.TOKYO -> "tokyo"
                                                                            AppThemeStyle.CLASSIC -> "classic"
                                                                            AppThemeStyle.ROYAL -> "royal"
                                                                            AppThemeStyle.VOLT -> "volt"
                                                                        }
                                                                    )
                                                                    currentThemeStyle = newStyle
                                                                    // Itt még el is mentheted a választást, ha szeretnéd
                                                                },
                                                                onChangeAppIcon = { newIcon ->
                                                                    tokenStorage.saveAppIcon(newIcon.alias)
                                                                    currentAppIcon = newIcon
                                                                    appIconManager.changeIcon(newIcon) // Triggers the OS level change
                                                                }
                                                            )
                                                        } else {
                                                            when (currentAuthRoute) {
                                                                AuthRoute.Login -> {
                                                                    LoginScreen(
                                                                        onLoginSuccess = {
                                                                            if (tokenStorage.getToken() != null) {
                                                                                tokenStorage.saveLanguage(currentLanguage)
                                                                                isLoggedIn = true
                                                                            }
                                                                        },
                                                                        onNavigateToRegister = { currentAuthRoute = AuthRoute.Register }
                                                                    )
                                                                }
                                                                AuthRoute.Register -> {
                                                                    RegisterScreen(
                                                                        onRegisterSuccess = {
                                                                            if (tokenStorage.getToken() != null) {
                                                                                tokenStorage.saveLanguage(currentLanguage)
                                                                                isLoggedIn = true
                                                                            }
                                                                        },
                                                                        onNavigateToLogin = { currentAuthRoute = AuthRoute.Login }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    }

                                    // 4. UPDATED: Modify Layer 2 at the bottom of your Scaffold
                                    // --- LAYER 2: BOTTOM NAV BAR ---
                                    // 👇 Add !isIosPlatform() so Compose steps back on iOS device types
                                    if (isMobile && !isIosPlatform()) {
                                        val showNavBar = currentTabRoute != NavRoute.Map || isMapNavBarVisible

                                        Box(modifier = Modifier.align(Alignment.BottomCenter).zIndex(10f)) {
                                            AnimatedBottomNavBar(
                                                isVisible = showNavBar,
                                                currentTabRoute = currentTabRoute,
                                                onTabNavigate = onTabNavigate
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // --- SCREEN B: CHAT DETAIL SCREEN ---
                        composable<NavRoute.ChatDetail>(
                            enterTransition = {
                                if (isIosPlatform()) slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                else fadeIn(animationSpec = tween(250))
                            },
                            popExitTransition = {
                                if (isIosPlatform()) slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                else fadeOut(animationSpec = tween(250))
                            }
                        ) { backStackEntry ->
                            val route = backStackEntry.toRoute<NavRoute.ChatDetail>()
                            val chatViewModel = koinViewModel<ChatViewModel>(parameters = { org.koin.core.parameter.parametersOf(route.chatId) })

                            Box(modifier = Modifier.fillMaxSize()) {
                                ChatDetailScreen(
                                    viewModel = chatViewModel,
                                    chatId = route.chatId,
                                    otherUsername = route.otherUsername,
                                    otherUserImageUrl = route.otherUserImageUrl,
                                    initialThemeName = route.themeName,
                                    bottomNavPadding = 0.dp,
                                    onBack = { rootNavController.popBackStack() },
                                    galleryLauncher = galleryLauncher
                                )
                            }
                        }

                        // --- SCREEN C: AI CHAT SCREEN ---
                        composable<NavRoute.AiChat>(
                            enterTransition = {
                                if (isIosPlatform()) slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                else fadeIn(animationSpec = tween(250))
                            },
                            popExitTransition = {
                                if (isIosPlatform()) slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                else fadeOut(animationSpec = tween(250))
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                DummyAiChatPlayground(onBack = { rootNavController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedBottomNavBar(
    isVisible: Boolean,
    currentTabRoute: NavRoute,
    onTabNavigate: (NavRoute) -> Unit
) {
    // Because this is a separate function, it doesn't know about the "Row"
    // and correctly uses the standard AnimatedVisibility!
    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300))
    ) {
        MobileBottomNavPill(
            currentRoute = currentTabRoute,
            onNavigate = onTabNavigate
        )
    }
}