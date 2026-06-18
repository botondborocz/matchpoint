package org.ttproject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
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
import org.ttproject.screens.SettingsSubScreen
import org.ttproject.screens.MatchScreen
import org.ttproject.screens.MessagesScreen
import org.ttproject.screens.ProfileScreen
import org.ttproject.screens.RegisterScreen
import org.ttproject.screens.ResetPasswordScreen
import org.ttproject.util.AuthEventBus
import org.ttproject.util.LocalThemeMode
import org.ttproject.util.SetStatusBarColors
import org.ttproject.util.ThemeMode
import org.ttproject.util.changePlatformLanguage
import org.ttproject.viewmodel.ChatViewModel
import org.ttproject.viewmodel.LocationViewModel
import org.ttproject.viewmodel.LocationsUiState
import org.ttproject.viewmodel.LoginViewModel
import org.ttproject.viewmodel.MatchViewModel
import org.ttproject.viewmodel.MessagesViewModel
import org.ttproject.viewmodel.ProfileViewModel
import org.ttproject.components.InAppNotification
import org.ttproject.components.BadgeLevelUpCelebrationDialog
import org.ttproject.components.getBadgeIcon
import org.ttproject.components.getBadgeName
import getBadgeColor
import org.jetbrains.compose.resources.stringResource
import org.ttproject.shared.resources.badge_progress_prefix
import org.ttproject.shared.resources.badge_progress_suffix
import org.ttproject.shared.resources.Res as SharedRes



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
    onTabChangedBySystem: (NavRoute) -> Unit = {},
    onThemeStyleChanged: (String) -> Unit = {},
    onSubScreenVisibilityChanged: (Boolean) -> Unit = {}
) {
    val tokenStorage: TokenStorage = koinInject()
    val appIconManager: AppIconManager = koinInject()
    val rootNavController = rememberNavController()

    val loginViewModel: LoginViewModel = koinViewModel()
    val profileViewModel: ProfileViewModel = koinViewModel()
    val matchViewModel: MatchViewModel = koinViewModel()
    val locationViewModel: LocationViewModel = koinViewModel()
    val messagesViewModel: MessagesViewModel = koinViewModel()

    val badgeProgressEvent by profileViewModel.badgeProgressEvent.collectAsState()

    var isMapNavBarVisible by remember { mutableStateOf(true) }
    var currentTabRoute by remember { mutableStateOf<NavRoute>(NavRoute.Map) }
    var activeSettingsScreen by remember { mutableStateOf(SettingsSubScreen.None) }
    var isDetailScreen by remember { mutableStateOf(false) }

    var isMapLoaded by remember { mutableStateOf(false) }
    val locationState by locationViewModel.uiState.collectAsState()
    val isPinsLoaded = locationState is LocationsUiState.Success || locationState is LocationsUiState.Error

    var showInitialLoader by remember { mutableStateOf(true) }

    LaunchedEffect(isMapLoaded, isPinsLoaded) {
        if (isMapLoaded && isPinsLoaded) {
            delay(1000)
            showInitialLoader = false
        }
    }

    LaunchedEffect(Unit) {
        delay(3500)
        showInitialLoader = false
    }

    LaunchedEffect(rootNavController) {
        rootNavController.addOnDestinationChangedListener { _, destination, _ ->
            isDetailScreen = !destination.hasRoute(HomeBase::class)
        }
    }

    LaunchedEffect(isDetailScreen, currentTabRoute, isMapNavBarVisible) {
        val shouldHideTabBar = isDetailScreen || (currentTabRoute == NavRoute.Map && !isMapNavBarVisible)
        onSubScreenVisibilityChanged(shouldHideTabBar)
    }

    val loadedTabs = remember { mutableStateListOf<NavRoute>(NavRoute.Map) }
    var isLoggedIn by remember { mutableStateOf(tokenStorage.getToken() != null) }
    val isResetPasswordActive by AuthEventBus.resetPasswordActive.collectAsState()
    var playMessagesAnimation by remember { mutableStateOf(true) }

    LaunchedEffect(externalTabRoute) {
        if (externalTabRoute != null && externalTabRoute != currentTabRoute) {
            currentTabRoute = externalTabRoute
            if (!loadedTabs.contains(externalTabRoute)) {
                loadedTabs.add(externalTabRoute)
            }
        }
    }

    val onTabNavigate: (NavRoute) -> Unit = { targetRoute ->
        if (targetRoute == NavRoute.Messages) playMessagesAnimation = true
        currentTabRoute = targetRoute
        if (!loadedTabs.contains(targetRoute)) {
            loadedTabs.add(targetRoute)
        }
        activeSettingsScreen = SettingsSubScreen.None
        onTabChangedBySystem(targetRoute)
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
                "sunset" -> AppThemeStyle.SUNSET
                else -> AppThemeStyle.DEFAULT
            }
        )
    }

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

    LaunchedEffect(currentThemeStyle, isCurrentlyDark) {
        val activeHexColor = if (isCurrentlyDark) {
            currentThemeStyle.darkAccent
        } else {
            currentThemeStyle.lightAccent
        }
        onThemeStyleChanged(activeHexColor)
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
                // 👇 Track explicit local map visibility states cleanly
                val isMapVisible = isLoggedIn && loadedTabs.contains(NavRoute.Map) && currentTabRoute == NavRoute.Map && !isDetailScreen

                Row(modifier = Modifier.fillMaxSize()) {
                    if (!isMobile) {
                        DesktopSidebar(currentRoute = currentTabRoute, onNavigate = onTabNavigate)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // 🗺️ LAYER 1: PERSISTENT BACKGROUND MAP SCREEN
                        if (isLoggedIn && loadedTabs.contains(NavRoute.Map)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = if (isMapVisible) 1f else 0f }
                                    .zIndex(if (isMapVisible) 1f else -1f) // 👈 Sits directly on top of background layers when active
                            ) {
                                MapScreen(
                                    viewModel = locationViewModel,
                                    bottomNavHeight = if (isMobile) 82.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp,
                                    systemNavHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                                    onNavBarVisibilityChange = { isVisible -> isMapNavBarVisible = isVisible },
                                    onMapLoaded = { isMapLoaded = true }
                                )
                            }
                        }

                        // 📱 LAYER 2: ROOT SUB-SCREEN SYSTEM CONTAINER
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isMapVisible) 0f else 2f) // 👈 Drops deep below the map when open to avoid intercepting touches
                                .background(if (isMapVisible) Color.Transparent else AppColors.Background)
                        ) {
                            NavHost(
                                navController = rootNavController,
                                startDestination = HomeBase,
                                modifier = Modifier.fillMaxSize().clipToBounds(),
                                enterTransition = { EnterTransition.None },
                                exitTransition = { ExitTransition.None }
                            ) {
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
                                contentWindowInsets = WindowInsets(0.dp),
                                modifier = Modifier.fillMaxSize()
                            ) { innerPadding ->
                                val systemNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                val frozenBottomPadding = if (isMobile) 82.dp + systemNavPadding else 0.dp

                                Box(modifier = Modifier.fillMaxSize()) {
                                    loadedTabs.forEach { route ->
                                        val isVisible = currentTabRoute == route
                                        if (isVisible && route != NavRoute.Map) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(AppColors.Background)
                                            ) {
                                                when (route) {
                                                    NavRoute.Match -> {
                                                        MatchScreen(
                                                            viewModel = matchViewModel,
                                                            onNavigateToLogin = {
                                                                currentAuthRoute = AuthRoute.Login
                                                                onTabNavigate(NavRoute.Profile)
                                                            },
                                                            onNavigateToMessages = { onTabNavigate(NavRoute.Messages) }
                                                        )
                                                    }
                                                    NavRoute.Coach -> {
                                                        AiHubScreen(
                                                            onNavigateToAiChat = { rootNavController.navigate(NavRoute.AiChat) },
                                                            onNavigateToVideoAnalysis = {}
                                                        )
                                                    }
                                                    NavRoute.Messages -> {
                                                        MessagesScreen(
                                                            viewModel = messagesViewModel,
                                                            playAnimation = playMessagesAnimation,
                                                            bottomNavPadding = frozenBottomPadding,
                                                            onNavigateToChat = { chatId, otherUsername, otherUserImageUrl, themeName ->
                                                                playMessagesAnimation = false
                                                                rootNavController.navigate(NavRoute.ChatDetail(chatId, otherUsername, otherUserImageUrl, themeName))
                                                            },
                                                            galleryLauncher = galleryLauncher
                                                        )
                                                    }
                                                    NavRoute.Profile -> {
                                                        if (isLoggedIn) {
                                                            ProfileScreen(
                                                                isVisible = currentTabRoute == NavRoute.Profile,
                                                                currentLanguage = currentLanguage,
                                                                currentThemeMode = currentThemeMode,
                                                                currentAppIcon = currentAppIcon,
                                                                activeSettingsScreen = activeSettingsScreen,
                                                                onActiveSettingsScreenChange = { activeSettingsScreen = it },
                                                                viewModel = profileViewModel,
                                                                onLogoutClick = {
                                                                    tokenStorage.clearToken()
                                                                    tokenStorage.clearUserId()
                                                                    tokenStorage.clearPremiumStatus()
                                                                    profileViewModel.clearData()
                                                                    matchViewModel.clearData()
                                                                    locationViewModel.clearData()
                                                                    messagesViewModel.clearData()
                                                                    loginViewModel.resetState()
                                                                    isLoggedIn = false
                                                                    activeSettingsScreen = SettingsSubScreen.None
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
                                                                            AppThemeStyle.SUNSET -> "sunset"
                                                                        }
                                                                    )
                                                                    currentThemeStyle = newStyle
                                                                },
                                                                onChangeAppIcon = { newIcon ->
                                                                    tokenStorage.saveAppIcon(newIcon.alias)
                                                                    currentAppIcon = newIcon
                                                                    appIconManager.changeIcon(newIcon)
                                                                }
                                                            )
                                                        } else {
                                                            when (currentAuthRoute) {
                                                                AuthRoute.Login -> {
                                                                    LoginScreen(
                                                                        viewModel = loginViewModel,
                                                                        onLoginSuccess = {
                                                                            isLoggedIn = true
                                                                        },
                                                                        onNavigateToRegister = {
                                                                            currentAuthRoute = AuthRoute.Register
                                                                        }
                                                                    )
                                                                }
                                                                AuthRoute.Register -> {
                                                                    RegisterScreen(
                                                                        viewModel = loginViewModel,
                                                                        onRegisterSuccess = {
                                                                            currentAuthRoute = AuthRoute.Login
                                                                        },
                                                                        onNavigateToLogin = {
                                                                            currentAuthRoute = AuthRoute.Login
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }

                                        // 👇 Generates a transparent touch fallback block when the map tab handles foreground input focus
                                        if (currentTabRoute == NavRoute.Map) {
                                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
                                        }

                                        // 🛑 CLEANUP REMOVAL: The original inline AnimatedBottomNavBar block has been cleanly pruned from here!
                                    }
                                }
                            }
                        }
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
                            val chatViewModel = koinViewModel<ChatViewModel>(key = route.chatId, parameters = { org.koin.core.parameter.parametersOf(route.chatId) })

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

                // 🌟 LAYER 3: GLOBAL FLOATING BOTTOM NAVIGATION OVERLAY
                if (isMobile && !isIosPlatform()) {
                    val showNavBar = !isDetailScreen && (currentTabRoute != NavRoute.Map || isMapNavBarVisible)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(3f) // 👈 Forces the navigation capsule to stay pinned to the absolute top view layer
                    ) {
                        AnimatedBottomNavBar(
                            isVisible = showNavBar,
                            currentTabRoute = currentTabRoute,
                            onTabNavigate = onTabNavigate
                        )
                    }
                }

                // 🌟 LAYER 4: BADGE PROGRESS EVENTS (NOTIFICATIONS & POPUPS)
                badgeProgressEvent?.let { event ->
                    val isSilencedBadge = event.badgeKey == "profileSwipes" ||
                            event.badgeKey == "successfulMatches" ||
                            event.badgeKey == "sentMessages"

                    if (event.isLevelUp) {
                        val shouldShowPopup = !isSilencedBadge || (currentTabRoute == NavRoute.Profile)
                        if (shouldShowPopup) {
                            BadgeLevelUpCelebrationDialog(
                                badgeKey = event.badgeKey,
                                newLevel = event.newLevel,
                                onDismiss = { profileViewModel.dismissBadgeProgressEvent() }
                            )
                        }
                    } else {
                        if (!isSilencedBadge) {
                            val badgeName = getBadgeName(event.badgeKey)
                            val progressMsg = "${stringResource(SharedRes.string.badge_progress_prefix)}$badgeName${stringResource(SharedRes.string.badge_progress_suffix)}"
                            
                            InAppNotification(
                                message = progressMsg,
                                icon = getBadgeIcon(event.badgeKey),
                                iconColor = getBadgeColor(event.newLevel, isCompleted = true),
                                onDismiss = { profileViewModel.dismissBadgeProgressEvent() },
                                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                            )
                        } else {
                            profileViewModel.dismissBadgeProgressEvent()
                        }
                    }
                }

                if (isResetPasswordActive) {
                    ResetPasswordScreen(
                        viewModel = loginViewModel,
                        onDismiss = {
                            AuthEventBus.clearResetPassword()
                        }
                    )
                }

                AnimatedVisibility(
                    visible = showInitialLoader,
                    enter = fadeIn(),
                    exit = fadeOut(tween(600)) + scaleOut(targetScale = 1.1f, animationSpec = tween(600)),
                    modifier = Modifier.fillMaxSize().zIndex(100f)
                ) {
                    GlobalPremiumLoader(isDark = isCurrentlyDark, accentColor = AppColors.AccentOrange)
                }
            }
        }
    }
}

@Composable
fun GlobalPremiumLoader(isDark: Boolean, accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition()

    val bounceY by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BounceY"
    )

    val ballScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BallScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    alpha = glowAlpha
                    scaleX = 1.1f
                    scaleY = 1.1f
                }
                .background(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .offset(y = bounceY.dp)
                        .graphicsLayer {
                            scaleX = ballScale
                            scaleY = ballScale
                        }
                        .background(accentColor, CircleShape)
                        .shadow(4.dp, CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(48.dp, 8.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = (-15).dp)
                        .background(if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "TABLE TENNIS MAP",
                color = if (isDark) Color.White else Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Drawing arena and placing pins...",
                color = if (isDark) Color.Gray else Color.DarkGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AnimatedBottomNavBar(
    isVisible: Boolean,
    currentTabRoute: NavRoute,
    onTabNavigate: (NavRoute) -> Unit
) {
    AnimatedVisibility(
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