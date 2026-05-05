package org.ttproject

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.components.DesktopSidebar
import org.ttproject.components.MobileBottomNav
import org.ttproject.components.MobileBottomNavPill
import org.ttproject.components.MobileTopBar
import org.ttproject.data.TokenStorage
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
import org.ttproject.viewmodel.MessagesViewModel

enum class AuthRoute {
    Login, Register
}

@Serializable
object HomeBase

val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun App(
    pendingChatId: String? = null,
    onChatConsumed: () -> Unit = {}
) {
    val tokenStorage: TokenStorage = koinInject()
    // 👇 1. TWO CONTROLLERS! One for sliding chats, one for switching tabs.
    val rootNavController = rememberNavController()
    val tabNavController = rememberNavController()

    var isLoggedIn by remember { mutableStateOf(tokenStorage.getToken() != null) }
    var playMessagesAnimation by remember { mutableStateOf(true) }

    val systemLanguage = Locale.current.language

    val supportedSystemLanguage = if (systemLanguage == "hu") "hu" else "en"

    var currentLanguage by remember { mutableStateOf(tokenStorage.getLanguage() ?: supportedSystemLanguage) }
    println("Current Language: $currentLanguage") // Debug log to verify language loading

    var isLanguageApplied by remember { mutableStateOf(false) }
    LaunchedEffect(currentLanguage) {
        changePlatformLanguage(currentLanguage)
        isLanguageApplied = true
    }

    if (!isLanguageApplied) {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.Background))
        return
    }

    // 👇 2. Track routes on the TAB controller
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var currentRoute = remember(currentDestination) {
        when {
            currentDestination?.hasRoute(NavRoute.Match::class) == true -> NavRoute.Match
            currentDestination?.hasRoute(NavRoute.Coach::class) == true -> NavRoute.Coach
            currentDestination?.hasRoute(NavRoute.Messages::class) == true -> NavRoute.Messages
            currentDestination?.hasRoute(NavRoute.Profile::class) == true -> NavRoute.Profile
            else -> NavRoute.Map
        }
    }

    var currentAuthRoute by remember { mutableStateOf(AuthRoute.Login) }

    val onTabNavigate: (NavRoute) -> Unit = { targetRoute ->
        if (targetRoute == NavRoute.Messages) playMessagesAnimation = true
        tabNavController.navigate(targetRoute) {
            popUpTo<NavRoute.Map> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingChatId) {
        if (pendingChatId != null) {
            tabNavController.navigate(NavRoute.Messages) // Inner switch
            rootNavController.navigate(NavRoute.ChatDetail(pendingChatId, "Chat", null, "Default")) // Outer push!
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

    // 👇 1. Actively listen to the live system theme
    val isSystemDark = isSystemInDarkTheme()

    // 👇 2. Calculate a definitive, reactive boolean that changes INSTANTLY
    val isCurrentlyDark = when (currentThemeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemDark
    }

    SetStatusBarColors(
        isDark = currentThemeMode == ThemeMode.Dark || (currentThemeMode == ThemeMode.System && isSystemInDarkTheme()),
        isSystemDefault = currentThemeMode == ThemeMode.System
    )

    key(currentLanguage) {
        CompositionLocalProvider(
            LocalThemeMode provides currentThemeMode,
            LocalIsDarkTheme provides isCurrentlyDark // 👇 Pass the live boolean down!
        ) {


//            val navBackStackEntry by navController.currentBackStackEntryAsState()
//            val currentDestination = navBackStackEntry?.destination
//
            // This tracks every screen currently active or animating on the screen.
//            val visibleEntries by tabNavController.visibleEntries.collectAsState()
//            val isChatDetailVisible = visibleEntries.any { it.destination.hasRoute(NavRoute.ChatDetail::class) }
//
//            var currentRoute = remember(currentDestination) {
//                when {
//                    currentDestination?.hasRoute(NavRoute.Match::class) == true -> NavRoute.Match
//                    currentDestination?.hasRoute(NavRoute.Coach::class) == true -> NavRoute.Coach
//                    currentDestination?.hasRoute(NavRoute.Messages::class) == true || currentDestination?.hasRoute(NavRoute.ChatDetail::class) == true -> NavRoute.Messages
//                    currentDestination?.hasRoute(NavRoute.Profile::class) == true -> NavRoute.Profile
//                    else -> NavRoute.Map
//                }
//            }
//
//            // 2. Remember which Auth screen the user is on (Defaults to Login)
//            var currentAuthRoute by remember { mutableStateOf(AuthRoute.Login) }
//
//            val onNavigate: (NavRoute) -> Unit = { targetRoute ->
//                if (targetRoute == NavRoute.Messages) {
//                    playMessagesAnimation = true
//                }
//                navController.navigate(targetRoute) {
//                    popUpTo<NavRoute.Map> {
//                        saveState = true
//                    }
//                    launchSingleTop = true
//                    restoreState = targetRoute != NavRoute.Messages
//                }
//            }

            // Determine the title for the TopBar
            val topBarTitle = when (currentRoute) {
                NavRoute.Messages -> "Messages" // Or use stringResource(SharedStrings.home)
                NavRoute.Map -> "Map"
                NavRoute.Coach -> "AI Coach"
                NavRoute.AiChat -> "AI Coach"
                NavRoute.Match -> "Match"
                NavRoute.Profile -> "Profile"
                NavRoute.ChatDetail -> "Chat"
                is NavRoute.ChatDetail -> "Chat"
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

                val isMobile = maxWidth < 600.dp
                val isChatDetailScreen = currentDestination?.hasRoute(NavRoute.ChatDetail::class) == true

                Row(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {

                    // Show Desktop Sidebar if width is >= 800.dp
                    if (!isMobile) {
                        DesktopSidebar(currentRoute = currentRoute, onNavigate = onTabNavigate)
                    }

                    // 👇 3. THE ROOT NAV HOST
                    NavHost(
                        navController = rootNavController,
                        startDestination = HomeBase,
                        modifier = Modifier.weight(1f).clipToBounds(),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None }
                    ) {
                        // --- SCREEN A: THE TAB CONTAINER (Including Bottom Nav) ---
                        composable<HomeBase>(
                            exitTransition = {
                                if (targetState.destination.hasRoute(NavRoute.ChatDetail::class) ||
                                    targetState.destination.hasRoute(NavRoute.AiChat::class)) {
                                    if (isIosPlatform()) {
                                        // iOS: Keep the original slide out
                                        slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300, easing = LinearEasing))
                                    } else {
                                        // Android: Stay perfectly still so the map doesn't flash.
                                        ExitTransition.None
                                    }
                                } else null
                            },
                            popEnterTransition = {
                                if (initialState.destination.hasRoute(NavRoute.ChatDetail::class) ||
                                    initialState.destination.hasRoute(NavRoute.AiChat::class)) {
                                    if (isIosPlatform()) {
                                        // iOS: Keep the original slide in
                                        slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300, easing = LinearEasing))
                                    } else {
                                        // Android: Stay perfectly still while the top screen fades out.
                                        EnterTransition.None
                                    }
                                } else null
                            }
                        ) {
                            // Scaffold is now INSIDE the route, so it slides beautifully!
                            Scaffold(
                                topBar = {
                                    if (isMobile && currentRoute != NavRoute.Map && currentRoute != NavRoute.Messages && currentRoute != NavRoute.Match && currentRoute != NavRoute.Profile && currentRoute != NavRoute.Coach) {
                                        MobileTopBar()
                                    }
                                },
                                containerColor = Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) { innerPadding ->

                                val bottomNavHeight = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                val frozenBottomPadding = if (isMobile) bottomNavHeight else 0.dp
                                val isMapActive = currentRoute == NavRoute.Map

                                Box(modifier = Modifier.fillMaxSize()) {

                                    // LAYER 1: MAP
                                    Box(modifier = Modifier.fillMaxSize().padding(bottom = frozenBottomPadding)) {
                                        MapScreen()
                                    }

                                    // LAYER 2: GLOBAL BACKGROUND
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = currentRoute != NavRoute.Map,
                                        enter = fadeIn(tween(200)),
                                        exit = fadeOut(tween(200))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize().background(AppColors.Background))
                                    }

                                    // LAYER 3: BOTTOM NAV BAR
                                    // No logic needed! It's permanently glued to the HomeBase screen.
                                    if (isMobile) {
                                        Box(modifier = Modifier.align(Alignment.BottomCenter).zIndex(10f)) {
                                            MobileBottomNavPill(currentRoute = currentRoute, onNavigate = onTabNavigate)
                                        }
                                    }

                                    // LAYER 4: TAB NAVHOST
                                    NavHost(
                                        navController = tabNavController,
                                        startDestination = NavRoute.Map,
                                        modifier = Modifier.fillMaxSize(),
                                        enterTransition = { EnterTransition.None },
                                        exitTransition = { ExitTransition.None },
                                        popEnterTransition = { EnterTransition.None },
                                        popExitTransition = { ExitTransition.None }
                                    ) {
                                        composable<NavRoute.Map> { Spacer(modifier = Modifier.fillMaxSize()) }

                                        composable<NavRoute.Match> {
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

                                        composable<NavRoute.Coach> {
                                            // 👇 NEW: Mount the AiHubScreen on the Coach tab!
                                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(bottom = frozenBottomPadding)) {
                                                AiHubScreen(
                                                    onNavigateToAiChat = {
                                                        // Push the AI Chat full screen onto the ROOT controller
                                                        rootNavController.navigate(NavRoute.AiChat)
                                                    },
                                                    onNavigateToVideoAnalysis = {
                                                        // Ready for your next AI feature
                                                    }
                                                )
                                            }
                                        }

                                        composable<NavRoute.Messages> {
                                            MessagesScreen(
                                                playAnimation = playMessagesAnimation,
                                                bottomNavPadding = frozenBottomPadding,
                                                onNavigateToChat = { chatId, otherUsername, otherUserImageUrl, themeName ->
                                                    playMessagesAnimation = false
                                                    // 👇 THE MAGIC: Push ChatDetail onto the ROOT stack!
                                                    rootNavController.navigate(NavRoute.ChatDetail(chatId, otherUsername, otherUserImageUrl, themeName))
                                                }
                                            )
                                        }

                                        composable<NavRoute.Profile> {
                                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(bottom = 0.dp)) {
                                                if (isLoggedIn) {
                                                    ProfileScreen(
                                                        currentLanguage = currentLanguage,
                                                        currentThemeMode = currentThemeMode,
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
                                                        }
                                                    )
                                                } else {


                                                    // 3. Switch between them!
                                                    when (currentAuthRoute) {
                                                        AuthRoute.Login -> {
                                                            LoginScreen(
                                                                onLoginSuccess = {
                                                                    if (tokenStorage.getToken() != null) {
                                                                        tokenStorage.saveLanguage(
                                                                            currentLanguage
                                                                        )
                                                                        isLoggedIn = true
                                                                    }
                                                                },
                                                                // Pass a lambda to change the state to Register
                                                                onNavigateToRegister = {
                                                                    currentAuthRoute = AuthRoute.Register
                                                                }
                                                            )
                                                        }

                                                        AuthRoute.Register -> {
                                                            RegisterScreen(
                                                                onRegisterSuccess = {
                                                                    if (tokenStorage.getToken() != null) {
                                                                        tokenStorage.saveLanguage(
                                                                            currentLanguage
                                                                        )
                                                                        isLoggedIn =
                                                                            true // Auto-login after registration!
                                                                    }
                                                                },
                                                                // Pass a lambda to change the state back to Login
                                                                onNavigateToLogin = {
                                                                    currentAuthRoute = AuthRoute.Login
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- SCREEN B: CHAT DETAIL SCREEN ---
                        composable<NavRoute.ChatDetail>(
                            enterTransition = {
                                if (isIosPlatform()) {
                                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                } else {
                                    // Android: Smooth fade in over the frozen HomeBase
                                    fadeIn(animationSpec = tween(250))
                                }
                            },
                            popExitTransition = {
                                if (isIosPlatform()) {
                                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                } else {
                                    // Android: Smooth fade out to reveal the frozen HomeBase
                                    fadeOut(animationSpec = tween(250))
                                }
                            }
                        ) { backStackEntry ->
                            val route = backStackEntry.toRoute<NavRoute.ChatDetail>()
                            val chatViewModel = koinViewModel<ChatViewModel>(
                                parameters = { org.koin.core.parameter.parametersOf(route.chatId) }
                            )

                            Box(modifier = Modifier.fillMaxSize()) {
                                ChatDetailScreen(
                                    viewModel = chatViewModel,
                                    chatId = route.chatId,
                                    otherUsername = route.otherUsername,
                                    otherUserImageUrl = route.otherUserImageUrl,
                                    initialThemeName = route.themeName,
                                    bottomNavPadding = 0.dp,
                                    onBack = { rootNavController.popBackStack() }
                                )
                            }
                        }

                        // --- SCREEN C: AI CHAT SCREEN ---
                        composable<NavRoute.AiChat>(
                            enterTransition = {
                                if (isIosPlatform()) {
                                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                } else {
                                    fadeIn(animationSpec = tween(250))
                                }
                            },
                            popExitTransition = {
                                if (isIosPlatform()) {
                                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300, easing = LinearEasing))
                                } else {
                                    fadeOut(animationSpec = tween(250))
                                }
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                DummyAiChatPlayground(
                                    onBack = { rootNavController.popBackStack() }
                                )
                            }
                        }
                    }


                    // Main Content Area: Replaced Column with Scaffold
//                    Scaffold(
//                        topBar = {
//                            if (isMobile && currentRoute != NavRoute.Map && currentRoute != NavRoute.Messages) {
//                                MobileTopBar()
//                            }
//                        },
//                        containerColor = Color.Transparent,
//                        modifier = Modifier.weight(1f).clipToBounds()
//                    ) { innerPadding ->
//
//                        // Calculate standard bottom nav height (80.dp + system insets)
//                        val bottomNavHeight = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
//                        val frozenBottomPadding = if (isMobile) bottomNavHeight else 0.dp
//                        val isMapActive = currentRoute == NavRoute.Map
//
//                        Box(modifier = Modifier.fillMaxSize()) {
//
//                            // --- LAYER 1: MAP ---
//                            Box(modifier = Modifier.fillMaxSize().padding(bottom = frozenBottomPadding)) {
//                                MapScreen(isActive = isMapActive)
//                            }
//
//                            // --- LAYER 2: GLOBAL SOLID BACKGROUND ---
//                            // This guarantees a dark canvas so the map doesn't show through.
//                            androidx.compose.animation.AnimatedVisibility(
//                                visible = currentRoute != NavRoute.Map,
//                                enter = fadeIn(tween(200)),
//                                exit = fadeOut(tween(200))
//                            ) {
//                                Box(modifier = Modifier.fillMaxSize().background(AppColors.Background))
//                            }
//
//                            // --- LAYER 3: BOTTOM NAV BAR ---
//                            // Placed under the NavHost so ChatDetail can slide over it.
//                            Box(modifier = Modifier.align(Alignment.BottomCenter).zIndex(if (isChatDetailVisible) 0f else 1f)) {
//                                androidx.compose.animation.AnimatedVisibility(
//                                    visible = isMobile && !isChatDetailScreen,
//                                    enter = slideInHorizontally(
//                                        initialOffsetX = { -it / 3 },
//                                        animationSpec = tween(300, easing = LinearEasing)
//                                    ),
//                                    exit = slideOutHorizontally(
//                                        targetOffsetX = { -it / 3 },
//                                        animationSpec = tween(300, easing = LinearEasing)
//                                    )
//                                ) {
//                                    MobileBottomNav(currentRoute = currentRoute, onNavigate = onNavigate)
//                                }
//                            }
//
//                            // --- LAYER 4: NAVHOST ---
//                            NavHost(
//                                navController = navController,
//                                startDestination = NavRoute.Map,
//                                modifier = Modifier.fillMaxSize().zIndex(if (isChatDetailVisible) 1f else 0f),
//                                enterTransition = { EnterTransition.None },
//                                exitTransition = { ExitTransition.None },
//                                popEnterTransition = { EnterTransition.None },
//                                popExitTransition = { ExitTransition.None }
//                            ) {
//                                composable<NavRoute.Map> { Spacer(modifier = Modifier.fillMaxSize()) }
//
//                                composable<NavRoute.Match> {
//                                    // 👇 Fixed: Added padding, REMOVED solid background
//                                    Box(modifier = Modifier.fillMaxSize().padding(bottom = frozenBottomPadding)) {
//                                        MatchScreen(
//                                            onNavigateToLogin = {
//                                                currentAuthRoute = AuthRoute.Login
//                                                onNavigate(NavRoute.Profile)
//                                            },
//                                            onNavigateToMessages = { onNavigate(NavRoute.Messages) }
//                                        )
//                                    }
//                                }
//                                composable<NavRoute.Coach> {
//                                    Box(modifier = Modifier.fillMaxSize().padding(bottom = frozenBottomPadding).padding(16.dp)) {
//                                        Text("Coach Screen", color = AppColors.TextPrimary)
//                                        Button(
//                                            onClick = {
//                                                tokenStorage.clearToken()
//                                                tokenStorage.clearLanguage()
//                                                isLoggedIn = false
//                                            },
//                                            modifier = Modifier.padding(top = 16.dp)
//                                        ) {
//                                            Text("Logout")
//                                        }
//                                    }
//                                }
//                                composable<NavRoute.Messages>(
//                                    exitTransition = {
//                                        if (targetState.destination.hasRoute(NavRoute.ChatDetail::class)) {
//                                            // 👇 Just the slide, no fade!
//                                            slideOutHorizontally(
//                                                targetOffsetX = { -it / 3 },
//                                                animationSpec = tween(300, easing = LinearEasing)
//                                            )
//                                        } else {
//                                            fadeOut(tween(200))
//                                        }
//                                    },
//                                    popEnterTransition = {
//                                        if (initialState.destination.hasRoute(NavRoute.ChatDetail::class)) {
//                                            // 👇 Just the slide, no fade!
//                                            slideInHorizontally(
//                                                initialOffsetX = { -it / 3 },
//                                                animationSpec = tween(300, easing = LinearEasing)
//                                            )
//                                        } else {
//                                            fadeIn(tween(200))
//                                        }
//                                    }
//                                ) {
//                                    MessagesScreen(
//                                        // 👇 3. Pass the state to the screen
//                                        playAnimation = playMessagesAnimation,
//                                        bottomNavPadding = frozenBottomPadding,
//                                        onNavigateToChat = { chatId, otherUsername, otherUserImageUrl ->
//                                            // 👇 4. We are going to a chat! Turn off the animation for when we come back.
//                                            playMessagesAnimation = false
//                                            navController.navigate(NavRoute.ChatDetail(chatId, otherUsername, otherUserImageUrl))
//                                        }
//                                    )
//                                }
//
//                                composable<NavRoute.ChatDetail>(
//                                    enterTransition = {
//                                        // 👇 Chat Detail slides in fully from the right edge
//                                        slideInHorizontally(
//                                            initialOffsetX = { it },
//                                            animationSpec = tween(300, easing = LinearEasing)
//                                        )
//                                    },
//                                    popExitTransition = {
//                                        // 👇 Chat Detail slides out fully to the right edge (tracks finger!)
//                                        slideOutHorizontally(
//                                            targetOffsetX = { it },
//                                            animationSpec = tween(300, easing = LinearEasing)
//                                        )
//                                    }
//                                ) { backStackEntry ->
//                                    val route = backStackEntry.toRoute<NavRoute.ChatDetail>()
//                                    val chatViewModel =
//                                        org.koin.compose.viewmodel.koinViewModel<ChatViewModel>(
//                                            parameters = {
//                                                org.koin.core.parameter.parametersOf(
//                                                    route.chatId
//                                                )
//                                            }
//                                        )
//
//                                    Box(modifier = Modifier.fillMaxSize()) {
//                                        ChatDetailScreen(
//                                            viewModel = chatViewModel,
//                                            chatId = route.chatId,
//                                            otherUsername = route.otherUsername,
//                                            otherUserImageUrl = route.otherUserImageUrl,
//                                            bottomNavPadding = 0.dp, // ChatDetail is full screen, no need to account for bottom nav
//                                            onBack = { navController.popBackStack() }
//                                        )
//                                    }
//                                }
//
//
//
//                                composable<NavRoute.Profile> {
//                                    Box(modifier = Modifier.fillMaxSize().padding(bottom = frozenBottomPadding)) {
//                                        if (isLoggedIn) {
//                                            ProfileScreen(
//                                                bottomNavPadding = 0.dp,
//                                                currentLanguage = currentLanguage,
//                                                currentThemeMode = currentThemeMode,
//                                                onLogoutClick = {
//                                                    tokenStorage.clearToken()
//                                                    tokenStorage.clearLanguage()
//                                                    isLoggedIn = false
//                                                },
//                                                onChangeLanguage = { newLangCode ->
//                                                    tokenStorage.saveLanguage(newLangCode)
//                                                    currentLanguage = newLangCode
//                                                    changePlatformLanguage(newLangCode)
//                                                },
//                                                onChangeTheme = { newThemeMode ->
//                                                    tokenStorage.saveThemeMode(
//                                                        when (newThemeMode) {
//                                                            ThemeMode.Light -> "light"
//                                                            ThemeMode.Dark -> "dark"
//                                                            ThemeMode.System -> "system"
//                                                        }
//                                                    )
//                                                    currentThemeMode = newThemeMode
//                                                }
//                                            )
//                                        } else {
//
//
//                                            // 3. Switch between them!
//                                            when (currentAuthRoute) {
//                                                AuthRoute.Login -> {
//                                                    LoginScreen(
//                                                        onLoginSuccess = {
//                                                            if (tokenStorage.getToken() != null) {
//                                                                tokenStorage.saveLanguage(
//                                                                    currentLanguage
//                                                                )
//                                                                isLoggedIn = true
//                                                            }
//                                                        },
//                                                        // Pass a lambda to change the state to Register
//                                                        onNavigateToRegister = {
//                                                            currentAuthRoute = AuthRoute.Register
//                                                        }
//                                                    )
//                                                }
//
//                                                AuthRoute.Register -> {
//                                                    RegisterScreen(
//                                                        onRegisterSuccess = {
//                                                            if (tokenStorage.getToken() != null) {
//                                                                tokenStorage.saveLanguage(
//                                                                    currentLanguage
//                                                                )
//                                                                isLoggedIn =
//                                                                    true // Auto-login after registration!
//                                                            }
//                                                        },
//                                                        // Pass a lambda to change the state back to Login
//                                                        onNavigateToLogin = {
//                                                            currentAuthRoute = AuthRoute.Login
//                                                        }
//                                                    )
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
                }
            }
        }
    }
}