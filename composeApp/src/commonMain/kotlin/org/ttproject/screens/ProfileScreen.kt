package org.ttproject.screens

import BadgeDetailsOverlay
import BadgesSection
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.NavigationEventInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.AppColors
import org.ttproject.AppColors.TextGray
import org.ttproject.data.Player
import org.ttproject.shared.resources.*
import org.ttproject.shared.resources.Res as SharedRes
import org.ttproject.viewmodel.ProfileState
import org.ttproject.viewmodel.ProfileViewModel
import org.ttproject.util.ThemeMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.coroutines.launch
import mapMetricsToBadges
import org.koin.compose.koinInject
import org.ttproject.AppThemeStyle
import org.ttproject.LocalAppThemeStyle
import org.ttproject.components.MobileTopBar
import org.ttproject.components.NativeDatePickerField
import org.ttproject.components.NativeDropdownField
import org.ttproject.components.PremiumAppIconSelector
import org.ttproject.components.PremiumThemeSelector
import org.ttproject.data.BadgeData
import org.ttproject.data.TokenStorage
import org.ttproject.icon.PremiumAppIcon
import org.ttproject.isIosPlatform
import org.ttproject.util.ConnectivityChecker
import org.ttproject.components.InAppNotification
import androidx.compose.ui.zIndex

enum class SettingsSubScreen {
    None, Main, Appearance, AppIcon, Language, Premium
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProfileScreen(
    isVisible: Boolean = true,
    currentLanguage: String = "en",
    currentAppThemeStyle: AppThemeStyle = LocalAppThemeStyle.current,
    currentAppIcon: PremiumAppIcon = PremiumAppIcon.DEFAULT,
    isUserPremium: Boolean = true,
    currentThemeMode: ThemeMode = ThemeMode.System,
    activeSettingsScreen: SettingsSubScreen = SettingsSubScreen.None,
    onActiveSettingsScreenChange: (SettingsSubScreen) -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onChangeLanguage: (String) -> Unit = {},
    onChangeTheme: (ThemeMode) -> Unit = {},
    onChangeAppThemeStyle: (AppThemeStyle) -> Unit = {},
    onChangeAppIcon: (PremiumAppIcon) -> Unit = {},
    onOverlayActive: (Boolean) -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
    connectivityChecker: ConnectivityChecker = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var isAvatarExpanded by remember { mutableStateOf(false) }

    var isEditUsernameModalOpen by remember { mutableStateOf(false) }
    var isEditBioModalOpen by remember { mutableStateOf(false) }
    var isEditGearModalOpen by remember { mutableStateOf(false) }
    var isEditBasicInfoModalOpen by remember { mutableStateOf(false) }
    var isMatchCardPreviewOpen by remember { mutableStateOf(false) }
    var selectedBadge by remember { mutableStateOf<BadgeData?>(null) }

    // 🌟 THE FIX: Optimistic lock tracking token to reject stale database values
    var locallySelectedLanguage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    var imageToUpload by remember { mutableStateOf<ByteArray?>(null) }

    val singleImagePicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let { imageBytes ->
                imageToUpload = imageBytes
            }
        }
    )

    LaunchedEffect(isAvatarExpanded, isMatchCardPreviewOpen, activeSettingsScreen) {
        onOverlayActive(isAvatarExpanded || isMatchCardPreviewOpen || activeSettingsScreen != SettingsSubScreen.None)
    }

    val profileNavState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = profileNavState,
        isBackEnabled = isAvatarExpanded || isMatchCardPreviewOpen || selectedBadge != null || activeSettingsScreen != SettingsSubScreen.None,
        onBackCompleted = {
            when {
                isAvatarExpanded -> isAvatarExpanded = false
                isMatchCardPreviewOpen -> isMatchCardPreviewOpen = false
                selectedBadge != null -> selectedBadge = null
                activeSettingsScreen != SettingsSubScreen.None -> {
                    if (activeSettingsScreen == SettingsSubScreen.Main) {
                        onActiveSettingsScreenChange(SettingsSubScreen.None)
                    } else {
                        onActiveSettingsScreenChange(SettingsSubScreen.Main)
                    }
                }
            }
        }
    )

    if (imageToUpload != null) {
        AvatarFramerDialog(
            imageBytes = imageToUpload!!,
            onDismiss = { imageToUpload = null },
            onConfirm = { selectedBias ->
                viewModel.uploadProfileImage(imageToUpload!!)
                imageToUpload = null
            }
        )
    }

    var profileNotificationMessage by remember { mutableStateOf<String?>(null) }
    var showOfflineBanner by remember { mutableStateOf(false) }
    var showSuccessBanner by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            viewModel.fetchUserProfile(showLoading = false)
        }
    }

    LaunchedEffect(Unit) {
        var wasConnected = connectivityChecker.isConnected()
        if (isIosPlatform()) {
            if (!wasConnected) {
                org.ttproject.components.PlatformNotificationManager.showNotification("No internet connection", "wifi_off")
            }
        } else {
            showOfflineBanner = !wasConnected
        }
        while (true) {
            kotlinx.coroutines.delay(1000)
            val connected = connectivityChecker.isConnected()
            if (connected) {
                if (isIosPlatform()) {
                    if (!wasConnected) {
                        org.ttproject.components.PlatformNotificationManager.showNotification("Connection restored", "wifi_on")
                        viewModel.fetchUserProfile(showLoading = false)
                    }
                } else {
                    showOfflineBanner = false
                    if (!wasConnected) {
                        showSuccessBanner = true
                        viewModel.fetchUserProfile(showLoading = false)
                    }
                }
            } else if (!connected && wasConnected) {
                if (isIosPlatform()) {
                    org.ttproject.components.PlatformNotificationManager.showNotification("No internet connection", "wifi_off")
                } else {
                    showOfflineBanner = true
                    showSuccessBanner = false
                }
            }
            wasConnected = connected
        }
    }

    LaunchedEffect(showSuccessBanner) {
        if (showSuccessBanner) {
            kotlinx.coroutines.delay(3000)
            showSuccessBanner = false
        }
    }

    when (uiState) {
        is ProfileState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.AccentOrange)
            }
        }

        is ProfileState.Error -> {
            val error = (uiState as ProfileState.Error).message
            Text(text = error, color = Color.Red)
        }

        is ProfileState.Success -> {
            val userData = uiState as ProfileState.Success
            var animateTrigger by remember { mutableStateOf(true) }
            val snackbarHostState = remember { SnackbarHostState() }

            // 🌟 REWRITTEN: Protects local selection from stale background read emissions
            LaunchedEffect(userData.language) {
                if (userData.language != null) {
                    // Release the optimistic selection lock once backend catches up
                    if (userData.language == locallySelectedLanguage) {
                        locallySelectedLanguage = null
                    }
                    // Only pass language updates downstream if we are not waiting on a pending selection write
                    if (locallySelectedLanguage == null && userData.language != currentLanguage) {
                        onChangeLanguage(userData.language!!)
                    }
                }
            }

            LaunchedEffect(userData.isPremium) {
                if (!userData.isPremium) {
                    if (currentAppThemeStyle.isPremium) {
                        onChangeAppThemeStyle(AppThemeStyle.DEFAULT)
                    }
                    if (currentAppIcon.isPremium) {
                        onChangeAppIcon(PremiumAppIcon.DEFAULT)
                    }
                }
            }

            val scrollState = rememberScrollState()

            var activeBadgeForOverlay by remember { mutableStateOf<BadgeData?>(null) }
            LaunchedEffect(selectedBadge) {
                if (selectedBadge != null) {
                    activeBadgeForOverlay = selectedBadge
                }
            }

            if (isEditUsernameModalOpen) {
                EditUsernameDialog(
                    initialName = userData.name ?: "",
                    onDismiss = { isEditUsernameModalOpen = false },
                    onSave = { newName ->
                        viewModel.updateProfile(newName, userData.blade ?: "", userData.rubberFh ?: "", userData.rubberBh ?: "", userData.bio, userData.birthDate, userData.skillLevel)
                        isEditUsernameModalOpen = false
                    }
                )
            }

            if (isEditBioModalOpen) {
                EditBioDialog(
                    initialBio = userData.bio ?: "",
                    onDismiss = { isEditBioModalOpen = false },
                    onSave = { newBio ->
                        viewModel.updateProfile(userData.name ?: "", userData.blade ?: "", userData.rubberFh ?: "", userData.rubberBh ?: "", newBio, userData.birthDate, userData.skillLevel)
                        isEditBioModalOpen = false
                    }
                )
            }

            if (isEditBasicInfoModalOpen) {
                EditBasicInfoDialog(
                    initialBirthDate = userData.birthDate ?: "",
                    initialLevel = userData.skillLevel ?: "Intermediate",
                    onDismiss = { isEditBasicInfoModalOpen = false },
                    onSave = { newBirthDate, newLevel ->
                        viewModel.updateProfile(userData.name ?: "", userData.blade ?: "", userData.rubberFh ?: "", userData.rubberBh ?: "", userData.bio, newBirthDate, newLevel)
                        isEditBasicInfoModalOpen = false
                    }
                )
            }

            if (isEditGearModalOpen) {
                EditGearDialog(
                    initialBlade = userData.blade ?: "",
                    initialForehand = userData.rubberFh ?: "",
                    initialBackhand = userData.rubberBh ?: "",
                    onDismiss = { isEditGearModalOpen = false },
                    onSave = { newBlade, newFh, newBh ->
                        viewModel.updateProfile(userData.name ?: "", newBlade, newFh, newBh, userData.bio, userData.birthDate, userData.skillLevel)
                        isEditGearModalOpen = false
                    }
                )
            }

            SharedTransitionLayout {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isMobile = maxWidth < 600.dp
                    InAppNotification(
                        message = profileNotificationMessage,
                        onDismiss = { profileNotificationMessage = null },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    )

                    val topPadding = if (isIosPlatform()) {
                        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    } else {
                        32.dp
                    }
                    val bottomPadding = if (isMobile) {
                        if (isIosPlatform()) 0.dp else 80.dp
                    } else {
                        0.dp
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isMobile) {
                                    Modifier.padding(bottom = bottomPadding)
                                } else {
                                    Modifier.navigationBarsPadding()
                                }
                            )
                    ) {
                        MobileTopBar(
                            showSettings = true,
                            onSettingsClick = { onActiveSettingsScreenChange(SettingsSubScreen.Main) }
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 16.dp,
                                    bottom = 16.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            AnimatedVisibility(
                                visible = animateTrigger,
                                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 50 }
                            ) {
                                Column {
                                    ProfileHeader(
                                        isAvatarExpanded = isAvatarExpanded,
                                        isMatchCardPreviewOpen = isMatchCardPreviewOpen,
                                        profileData = userData,
                                        onAvatarClick = { isAvatarExpanded = true },
                                        onPhotoEditClick = {
                                            if (!connectivityChecker.isConnected()) {
                                                profileNotificationMessage = "No internet connection"
                                            } else {
                                                singleImagePicker.launch()
                                            }
                                        },
                                        onUsernameEditClick = {
                                            if (!connectivityChecker.isConnected()) {
                                                profileNotificationMessage = "No internet connection"
                                            } else {
                                                isEditUsernameModalOpen = true
                                            }
                                        },
                                        onBioEditClick = {
                                            if (!connectivityChecker.isConnected()) {
                                                profileNotificationMessage = "No internet connection"
                                            } else {
                                                isEditBioModalOpen = true
                                            }
                                        },
                                        onPreviewMatchcardClick = { isMatchCardPreviewOpen = true }
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }

                            AnimatedVisibility(
                                visible = animateTrigger,
                                enter = fadeIn(tween(400, delayMillis = 50)) + slideInVertically(tween(400, delayMillis = 50)) { 50 }
                            ) {
                                Column {
                                    BadgesSection(
                                        metrics = userData.badgeMetrics,
                                        selectedBadge = selectedBadge,
                                        onBadgeClick = { clickedBadge -> selectedBadge = clickedBadge }
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }

                            AnimatedVisibility(
                                visible = animateTrigger,
                                enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(tween(400, delayMillis = 100)) { 50 }) {
                                Column {
                                    BasicInfoSection(
                                        profileData = userData,
                                        onEditClick = {
                                            if (!connectivityChecker.isConnected()) {
                                                profileNotificationMessage = "No internet connection"
                                            } else {
                                                isEditBasicInfoModalOpen = true
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            AnimatedVisibility(
                                visible = animateTrigger,
                                enter = fadeIn(tween(400, delayMillis = 150)) + slideInVertically(tween(400, delayMillis = 150)) { 50 }) {
                                Column {
                                    GearSection(
                                        profileData = userData,
                                        onGearEditClick = {
                                            if (!connectivityChecker.isConnected()) {
                                                profileNotificationMessage = "No internet connection"
                                            } else {
                                                isEditGearModalOpen = true
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp)
                    )

                    AnimatedVisibility(
                        visible = isAvatarExpanded,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(400)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AvatarPreviewOverlay(
                            animatedVisibilityScope = this,
                            username = userData.name ?: "Player",
                            imageUrl = userData.imageUrl,
                            onDismissRequest = { isAvatarExpanded = false },
                            onEditClick = {
                                if (!connectivityChecker.isConnected()) {
                                    profileNotificationMessage = "No internet connection"
                                } else {
                                    singleImagePicker.launch()
                                }
                                isAvatarExpanded = false
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = isMatchCardPreviewOpen,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(400)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MatchCardPreviewOverlay(
                            animatedVisibilityScope = this,
                            profileData = userData,
                            onDismiss = { isMatchCardPreviewOpen = false }
                        )
                    }

                    AnimatedVisibility(
                        visible = selectedBadge != null,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(400)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        activeBadgeForOverlay?.let { badge ->
                            BadgeDetailsOverlay(
                                badge = badge,
                                animatedVisibilityScope = this,
                                onDismiss = { selectedBadge = null }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = activeSettingsScreen != SettingsSubScreen.None,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)),
                        modifier = Modifier.fillMaxSize().zIndex(12f)
                    ) {
                        SettingsOverlay(
                            activeScreen = activeSettingsScreen,
                            onNavigateBack = {
                                if (activeSettingsScreen == SettingsSubScreen.Main) {
                                    onActiveSettingsScreenChange(SettingsSubScreen.None)
                                } else {
                                    onActiveSettingsScreenChange(SettingsSubScreen.Main)
                                }
                            },
                            onNavigateTo = { screen -> onActiveSettingsScreenChange(screen) },
                            currentLanguage = currentLanguage,
                            currentThemeMode = currentThemeMode,
                            currentAppThemeStyle = currentAppThemeStyle,
                            currentAppIcon = currentAppIcon,
                            isUserPremium = userData.isPremium,
                            onLogoutClick = {
                                viewModel.clearData()
                                onLogoutClick()
                                onActiveSettingsScreenChange(SettingsSubScreen.None)
                            },
                            onChangeLanguage = onChangeLanguage,
                            onChangeTheme = onChangeTheme,
                            onChangeAppThemeStyle = onChangeAppThemeStyle,
                            onChangeAppIcon = onChangeAppIcon,
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            // 🌟 PASSING REFERENCE TO TARGET OPTIONS SUB-SCREEN
                            onPendingLanguageChange = { selection -> locallySelectedLanguage = selection }
                        )
                    }

                    AnimatedVisibility(
                        visible = showOfflineBanner && !isIosPlatform(),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
                            .zIndex(15f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AppColors.SurfaceDark.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFEF5350).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Offline",
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(SharedRes.string.offline_profile_warning),
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { showOfflineBanner = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showSuccessBanner && !isIosPlatform(),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
                            .zIndex(15f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AppColors.SurfaceDark.copy(alpha = 0.95f),
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Online",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(SharedRes.string.connection_restored),
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { showSuccessBanner = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MatchCardPreviewOverlay(
    animatedVisibilityScope: AnimatedVisibilityScope,
    profileData: ProfileState.Success,
    onDismiss: () -> Unit
) {
    val meAsPlayer = remember(profileData) {
        Player(
            id = "me",
            username = profileData.name ?: "Player",
            skillLevel = profileData.skillLevel ?: "Beginner",
            age = profileData.age ?: 0,
            elo = profileData.elo,
            distanceKm = 0,
            imageUrl = profileData.imageUrl,
            badgeMetrics = profileData.badgeMetrics,
            isPremium = profileData.isPremium
        )
    }

    val cardGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF3B4CCA), Color(0xFF151C2C))
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        val calculatedWidth = minOf(maxWidth * 0.85f, (maxHeight - 180.dp) * 3 / 4).coerceIn(150.dp, 360.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures { /* Block bubbling */ } }
        ) {
            Text(
                text = stringResource(SharedRes.string.matchcard_preview_subtitle).uppercase(),
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .width(calculatedWidth)
                    .aspectRatio(3f / 4f)
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "matchcard_transition"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
            ) {
                MatchCard(
                    player = meAsPlayer,
                    backgroundBrush = cardGradient,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.1f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.AvatarPreviewOverlay(
    animatedVisibilityScope: AnimatedVisibilityScope,
    username: String,
    imageUrl: String?,
    onEditClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onDismissRequest() } },
        contentAlignment = Alignment.Center
    ) {
        val avatarSize = minOf(maxWidth * 0.85f, maxHeight - 160.dp).coerceIn(150.dp, 360.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "avatar_transition"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceDark)
                    .border(4.dp, Brush.linearGradient(colors = listOf(Color(0xFFFF4B4B), Color(0xFF9C27B0))), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Full Screen Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = getInitials(username),
                        color = Color.Black,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    onEditClick()
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(SharedRes.string.upload_photo), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        IconButton(
            onClick = onDismissRequest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp)
                .background(Color.Black.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close Preview", tint = Color.Black)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.ProfileHeader(
    isAvatarExpanded: Boolean,
    isMatchCardPreviewOpen: Boolean,
    profileData: ProfileState.Success,
    onPhotoEditClick: () -> Unit,
    onUsernameEditClick: () -> Unit,
    onBioEditClick: () -> Unit,
    onPreviewMatchcardClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val username = profileData.name ?: "Player"
    val imageUrl = profileData.imageUrl
    val bio = profileData.bio ?: ""

    val avatarGradient = Brush.linearGradient(colors = listOf(Color(0xFFFF4B4B), Color(0xFF9C27B0)))

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(100.dp).clickable { onAvatarClick() }) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAvatarExpanded,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut(tween(400))
                ) {
                    Box(
                        modifier = Modifier
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "avatar_transition"),
                                animatedVisibilityScope = this@AnimatedVisibility
                            )
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(AppColors.SurfaceDark)
                            .border(4.dp, avatarGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!imageUrl.isNullOrBlank()) {
                            AsyncImage(model = imageUrl, contentDescription = "Profile Picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, 0f))
                        } else {
                            Text(text = getInitials(username), color = AppColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp).clip(CircleShape).background(AppColors.Background).padding(3.dp)) {
                    Box(modifier = Modifier.size(26.dp).clip(CircleShape).clickable { onPhotoEditClick() }.background(AppColors.AccentOrange), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Edit Profile Picture", tint = Color.Black, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onUsernameEditClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(text = username, color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Edit Username", tint = AppColors.TextGray, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBioEditClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = bio.takeIf { it.isNotBlank() } ?: "Add a bio...",
                        color = if (bio.isNotBlank()) AppColors.TextPrimary else AppColors.TextGray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isMatchCardPreviewOpen,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400))
            ) {
                Button(
                    onClick = { onPreviewMatchcardClick() },
                    modifier = Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "matchcard_transition"),
                        animatedVisibilityScope = this@AnimatedVisibility
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = AppColors.AccentOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(SharedRes.string.preview_matchcard_btn), color = AppColors.AccentOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun EditBasicInfoDialog(
    initialBirthDate: String,
    initialLevel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var birthDate by remember { mutableStateOf(initialBirthDate) }
    var level by remember { mutableStateOf(initialLevel) }
    val skillLevels = listOf("Beginner", "Intermediate", "Advanced", "Pro")

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.SurfaceDark)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(SharedRes.string.edit_basic_info_title), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            NativeDatePickerField(
                value = birthDate,
                label = "Birth Date",
                onDateSelected = { birthDate = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            NativeDropdownField(
                value = level,
                label = "Skill Level",
                options = skillLevels,
                onOptionSelected = { level = it }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = AppColors.TextPrimary) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(birthDate, level) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun BasicInfoSection(
    profileData: ProfileState.Success,
    onEditClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(SharedRes.string.basic_info_header), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Info", tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val birthDate = profileData.birthDate ?: stringResource(SharedRes.string.set_birth_date)
        val ageDisplay = profileData.age?.toString() ?: stringResource(SharedRes.string.set_age)
        val skillLevel = profileData.skillLevel ?: stringResource(SharedRes.string.set_level)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.SurfaceDark)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(SharedRes.string.age_label), color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = ageDisplay, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.SurfaceDark)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(SharedRes.string.level_label), color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = skillLevel, color = AppColors.AccentOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EditTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = AppColors.TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary,
                focusedBorderColor = AppColors.AccentOrange,
                unfocusedBorderColor = AppColors.TextPrimary.copy(alpha = 0.3f),
                cursorColor = AppColors.AccentOrange
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun AvatarFramerDialog(
    imageBytes: ByteArray,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var verticalBias by remember { mutableStateOf(0f) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.SurfaceDark)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(SharedRes.string.frame_avatar_title), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .border(2.dp, AppColors.AccentOrange, CircleShape)
            ) {
                AsyncImage(
                    model = imageBytes,
                    contentDescription = "Avatar Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, verticalBias)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(SharedRes.string.adjust_position_label), color = AppColors.TextPrimary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = verticalBias,
                onValueChange = { verticalBias = it },
                valueRange = -1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = AppColors.AccentOrange,
                    activeTrackColor = AppColors.AccentOrange
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(SharedRes.string.cancel), color = AppColors.TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(verticalBias) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)
                ) {
                    Text(stringResource(SharedRes.string.save_upload_btn), color = AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun GearSection(
    profileData: ProfileState.Success,
    onGearEditClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(SharedRes.string.my_gear), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onGearEditClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Gear", tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.SurfaceDark)
        ) {
            GearRowItem(
                label = stringResource(SharedRes.string.blade),
                value = profileData.blade ?: "Butterfly Viscaria",
                iconContent = { Text("🏓", fontSize = 16.sp) }
            )
            HorizontalDivider(
                color = AppColors.TextPrimary.copy(alpha = 0.08f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            GearRowItem(
                label = stringResource(SharedRes.string.forehand),
                value = profileData.rubberFh ?: "Tenergy 05",
                iconContent = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF4B4B))) }
            )
            HorizontalDivider(
                color = AppColors.TextPrimary.copy(alpha = 0.08f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            GearRowItem(
                label = stringResource(SharedRes.string.backhand),
                value = profileData.rubberBh ?: "DHS Hurricane 3 Neo",
                iconContent = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.Black)) }
            )
        }
    }
}

@Composable
private fun GearRowItem(
    label: String,
    value: String,
    iconContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(text = label, color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsOverlay(
    activeScreen: SettingsSubScreen,
    onNavigateBack: () -> Unit,
    onNavigateTo: (SettingsSubScreen) -> Unit,
    currentLanguage: String,
    currentThemeMode: ThemeMode,
    currentAppThemeStyle: AppThemeStyle,
    currentAppIcon: PremiumAppIcon,
    isUserPremium: Boolean,
    onLogoutClick: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    onChangeTheme: (ThemeMode) -> Unit,
    onChangeAppThemeStyle: (AppThemeStyle) -> Unit,
    onChangeAppIcon: (PremiumAppIcon) -> Unit,
    viewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    tokenStorage: TokenStorage = koinInject(),
    onPendingLanguageChange: (String) -> Unit = {} // 👈 NEW: Lambda passed to communicate with the success block
) {
    val scope = rememberCoroutineScope()
    var isLanguageLoading by remember { mutableStateOf(false) }
    val premiumThemeLockedMsg = stringResource(SharedRes.string.premium_theme_locked_msg)
    val premiumIconLockedMsg = stringResource(SharedRes.string.premium_icon_locked_msg)
    val mapResetSuccessMsg = stringResource(SharedRes.string.map_preference_reset_success)
    val premiumActiveTitle = stringResource(SharedRes.string.premium_account_active)
    val upgradePremiumTitle = stringResource(SharedRes.string.upgrade_to_premium)
    val tapFreePlanSub = stringResource(SharedRes.string.tap_to_free_plan)
    val tapUnlockPremiumSub = stringResource(SharedRes.string.tap_to_unlock_premium)
    val resetMapChoiceLabel = stringResource(SharedRes.string.reset_map_choice)

    val topPadding = if (isIosPlatform()) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        32.dp
    }

    val backIcon = if (isIosPlatform()) Icons.Filled.ArrowBackIosNew else Icons.AutoMirrored.Filled.ArrowBack

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .pointerInput(Unit) { detectTapGestures { /* swallow tags */ } }
    ) {
        val isMobile = maxWidth < 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = topPadding, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = backIcon,
                        contentDescription = "Back",
                        tint = AppColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                val title = when (activeScreen) {
                    SettingsSubScreen.Main -> stringResource(SharedRes.string.settings_title)
                    SettingsSubScreen.Appearance -> stringResource(SharedRes.string.appearance_title)
                    SettingsSubScreen.AppIcon -> stringResource(SharedRes.string.app_icon_title)
                    SettingsSubScreen.Language -> stringResource(SharedRes.string.language)
                    SettingsSubScreen.Premium -> stringResource(SharedRes.string.premium_title)
                    else -> ""
                }
                Text(
                    text = title,
                    color = AppColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedContent(
                targetState = activeScreen,
                transitionSpec = {
                    if (targetState == SettingsSubScreen.Main) {
                        slideInHorizontally(initialOffsetX = { -it }) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    } else {
                        slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                    }
                },
                label = "settings_subscreen_transition",
                modifier = Modifier.weight(1f)
            ) { screen ->
                when (screen) {
                    SettingsSubScreen.Main -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppColors.SurfaceDark)
                            ) {
                                SettingsMenuRow(
                                    icon = Icons.Default.Palette,
                                    title = stringResource(SharedRes.string.appearance_title),
                                    onClick = { onNavigateTo(SettingsSubScreen.Appearance) }
                                )
                                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                SettingsMenuRow(
                                    icon = Icons.Default.Star,
                                    title = stringResource(SharedRes.string.app_icon_title),
                                    onClick = { onNavigateTo(SettingsSubScreen.AppIcon) }
                                )
                                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                SettingsMenuRow(
                                    icon = Icons.Default.Language,
                                    title = stringResource(SharedRes.string.language),
                                    onClick = { onNavigateTo(SettingsSubScreen.Language) }
                                )

                                if (isIosPlatform()) {
                                    HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                                    SettingsMenuRow(
                                        icon = Icons.Default.Map,
                                        title = resetMapChoiceLabel,
                                        onClick = {
                                            tokenStorage.clearMapChoice()
                                            scope.launch {
                                                snackbarHostState.showSnackbar(mapResetSuccessMsg)
                                            }
                                        }
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppColors.SurfaceDark)
                            ) {
                                SettingsMenuRow(
                                    icon = Icons.Default.Person,
                                    title = stringResource(SharedRes.string.settings_account_details),
                                    onClick = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Account Details Clicked (Placeholder)")
                                        }
                                    }
                                )
                                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                SettingsMenuRow(
                                    icon = if (isUserPremium) Icons.Default.Stars else Icons.Default.StarBorder,
                                    title = stringResource(SharedRes.string.premium_title),
                                    onClick = { onNavigateTo(SettingsSubScreen.Premium) }
                                )
                                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                SettingsMenuRow(
                                    icon = Icons.Default.Description,
                                    title = stringResource(SharedRes.string.settings_terms_of_service),
                                    onClick = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Terms of Service Clicked (Placeholder)")
                                        }
                                    }
                                )
                                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.08f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                                SettingsMenuRow(
                                    icon = Icons.Default.ThumbUp,
                                    title = stringResource(SharedRes.string.settings_rate_us),
                                    onClick = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Rate Us Clicked (Placeholder)")
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFFF4B4B).copy(alpha = 0.1f))
                                    .border(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .clickable { onLogoutClick() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color(0xFFFF4B4B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(SharedRes.string.logout), color = Color(0xFFFF4B4B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    SettingsSubScreen.Appearance -> {
                        val bottomExtraPadding = if (isMobile) {
                            if (isIosPlatform()) 0.dp else 80.dp
                        } else {
                            0.dp
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(bottom = bottomExtraPadding),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ThemeModeSelector(currentThemeMode, onChangeTheme)

                            PremiumThemeSelector(
                                currentThemeStyle = currentAppThemeStyle,
                                isUserPremium = isUserPremium,
                                onThemeSelected = onChangeAppThemeStyle,
                                onPremiumLockedClick = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(premiumThemeLockedMsg)
                                    }
                                }
                            )
                        }
                    }

                    SettingsSubScreen.AppIcon -> {
                        val bottomExtraPadding = if (isMobile) {
                            if (isIosPlatform()) 0.dp else 80.dp
                        } else {
                            0.dp
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(bottom = bottomExtraPadding)
                        ) {
                            PremiumAppIconSelector(
                                currentAppIcon = currentAppIcon,
                                isUserPremium = isUserPremium,
                                onIconSelected = onChangeAppIcon,
                                onPremiumLockedClick = {
                                    scope.launch { snackbarHostState.showSnackbar(premiumIconLockedMsg) }
                                }
                            )
                        }
                    }

                    SettingsSubScreen.Language -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LanguageGridItem(
                                title = "English",
                                flag = "🇬🇧 🇺🇸",
                                isSelected = currentLanguage == "en",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        isLanguageLoading = true
                                        onPendingLanguageChange("en") // 👈 Set lock token
                                        val success = viewModel.changeLanguageSuspend("en")
                                        if (success) {
                                            onChangeLanguage("en")
                                            viewModel.fetchUserProfile(showLoading = false)
                                            kotlinx.coroutines.delay(800)
                                        } else {
                                            onPendingLanguageChange("") // Reset token on failure
                                            snackbarHostState.showSnackbar("Failed to update language")
                                        }
                                        isLanguageLoading = false
                                    }
                                }
                            )

                            LanguageGridItem(
                                title = "Magyar",
                                flag = "🇭🇺",
                                isSelected = currentLanguage == "hu",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        isLanguageLoading = true
                                        onPendingLanguageChange("hu") // 👈 Set lock token
                                        val success = viewModel.changeLanguageSuspend("hu")
                                        if (success) {
                                            onChangeLanguage("hu")
                                            viewModel.fetchUserProfile(showLoading = false)
                                            kotlinx.coroutines.delay(800)
                                        } else {
                                            onPendingLanguageChange("") // Reset token on failure
                                            snackbarHostState.showSnackbar("Failed to update language")
                                        }
                                        isLanguageLoading = false
                                    }
                                }
                            )
                        }
                    }

                    SettingsSubScreen.Premium -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.SurfaceDark)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isUserPremium) premiumActiveTitle else upgradePremiumTitle,
                                    color = AppColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isUserPremium) tapFreePlanSub else tapUnlockPremiumSub,
                                    color = AppColors.TextGray,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = isUserPremium,
                                onCheckedChange = { viewModel.togglePremiumStatus() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.AccentOrange,
                                    checkedTrackColor = AppColors.AccentOrange.copy(alpha = 0.5f),
                                    uncheckedThumbColor = AppColors.TextGray,
                                    uncheckedTrackColor = AppColors.TextGray.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        if (isLanguageLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) { detectTapGestures { /* swallow click threads */ } },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AppColors.AccentOrange,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(SharedRes.string.settings_changing_language),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.TextGray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = AppColors.TextGray,
            modifier = Modifier.size(20.dp).rotate(-90f)
        )
    }
}

@Composable
private fun ThemeModeSelector(
    currentThemeMode: ThemeMode,
    onChangeTheme: (ThemeMode) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 120.dp).toInt().coerceIn(3, 6)

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(SharedRes.string.theme).uppercase(),
                color = AppColors.TextGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val modes = listOf(
                Triple(ThemeMode.System, stringResource(SharedRes.string.system_default), "system"),
                Triple(ThemeMode.Light, stringResource(SharedRes.string.light), "light"),
                Triple(ThemeMode.Dark, stringResource(SharedRes.string.dark), "dark")
            )

            val chunkedModes = modes.chunked(columns)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                chunkedModes.forEach { rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowModes.forEach { (mode, title, key) ->
                            val isSelected = currentThemeMode == mode

                            Box(modifier = Modifier.weight(1f)) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(3f / 4f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onChangeTheme(mode) }
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) AppColors.AccentOrange else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val w = size.width
                                            val h = size.height

                                            when (mode) {
                                                ThemeMode.Light -> {
                                                    drawRect(color = Color(0xFFF8F9FA))
                                                }

                                                ThemeMode.Dark -> {
                                                    drawRect(color = Color(0xFF0F172A))
                                                }

                                                ThemeMode.System -> {
                                                    val lightPath = Path().apply {
                                                        moveTo(0f, 0f)
                                                        lineTo(w, 0f)
                                                        lineTo(0f, h)
                                                        close()
                                                    }
                                                    drawPath(lightPath, color = Color(0xFFF8F9FA))

                                                    val darkPath = Path().apply {
                                                        moveTo(w, 0f)
                                                        lineTo(w, h)
                                                        lineTo(0f, h)
                                                        close()
                                                    }
                                                    drawPath(darkPath, color = Color(0xFF0F172A))
                                                }
                                            }

                                            drawCircle(
                                                color = Color(0xFFFF6B00),
                                                radius = 24f,
                                                center = center
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = title,
                                        color = if (isSelected) AppColors.AccentOrange else AppColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        val emptySlots = columns - rowModes.size
                        if (emptySlots > 0) {
                            repeat(emptySlots) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageGridItem(
    title: String,
    flag: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderCol = if (isSelected) AppColors.AccentOrange else Color.Transparent
    val borderThick = if (isSelected) 3.dp else 0.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceDark)
            .clickable { onClick() }
            .border(borderThick, borderCol, RoundedCornerShape(16.dp))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = flag,
            fontSize = 36.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = title,
            color = if (isSelected) AppColors.AccentOrange else AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ThemeOptionRow(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val textColor = if (isSelected) AppColors.AccentOrange else AppColors.TextPrimary
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AppColors.AccentOrange, unselectedColor = AppColors.TextGray))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, color = textColor, fontSize = 15.sp, fontWeight = fontWeight)
    }
}

@Composable
fun EditUsernameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.SurfaceDark)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(SharedRes.string.edit_username_title), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            EditTextField(
                label = stringResource(SharedRes.string.username),
                value = name,
                modifier = Modifier.focusRequester(focusRequester)
            ) { name = it }

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = AppColors.TextPrimary) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(name) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
fun EditBioDialog(
    initialBio: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var bio by remember { mutableStateOf(initialBio) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.SurfaceDark)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(SharedRes.string.edit_bio_title), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 150) bio = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                minLines = 3,
                maxLines = 5,
                placeholder = { Text(stringResource(SharedRes.string.bio_placeholder), color = AppColors.TextGray.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextPrimary, unfocusedTextColor = AppColors.TextPrimary,
                    focusedBorderColor = AppColors.AccentOrange, unfocusedBorderColor = AppColors.TextPrimary.copy(alpha = 0.3f),
                    cursorColor = AppColors.AccentOrange
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = AppColors.TextPrimary) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(bio) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
fun EditGearDialog(
    initialBlade: String,
    initialForehand: String,
    initialBackhand: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var blade by remember { mutableStateOf(initialBlade) }
    var forehand by remember { mutableStateOf(initialForehand) }
    var backhand by remember { mutableStateOf(initialBackhand) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.SurfaceDark)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(SharedRes.string.edit_gear_title), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            EditTextField(
                label = stringResource(SharedRes.string.blade),
                value = blade,
                modifier = Modifier.focusRequester(focusRequester)
            ) { blade = it }

            Spacer(modifier = Modifier.height(12.dp))
            EditTextField(stringResource(SharedRes.string.forehand), forehand) { forehand = it }
            Spacer(modifier = Modifier.height(12.dp))
            EditTextField(stringResource(SharedRes.string.backhand), backhand) { backhand = it }

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = AppColors.TextPrimary) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(blade, forehand, backhand) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
fun AppThemeStyle.getLocalizedTitle(): String {
    return when (this) {
        AppThemeStyle.DEFAULT -> stringResource(SharedRes.string.theme_default)
        AppThemeStyle.VIP -> stringResource(SharedRes.string.theme_vip)
        AppThemeStyle.ADRENALIN -> stringResource(SharedRes.string.theme_adrenalin)
        AppThemeStyle.MATRIX -> stringResource(SharedRes.string.theme_matrix)
        AppThemeStyle.ARCTIC -> stringResource(SharedRes.string.theme_arctic)
        AppThemeStyle.NEON -> stringResource(SharedRes.string.theme_neon)
        AppThemeStyle.STEALTH -> stringResource(SharedRes.string.theme_stealth)
        AppThemeStyle.TOKYO -> stringResource(SharedRes.string.theme_tokyo)
        AppThemeStyle.CLASSIC -> stringResource(SharedRes.string.theme_classic)
        AppThemeStyle.ROYAL -> stringResource(SharedRes.string.theme_royal)
        AppThemeStyle.VOLT -> stringResource(SharedRes.string.theme_volt)
        AppThemeStyle.SUNSET -> stringResource(SharedRes.string.theme_sunset)
    }
}

@Composable
fun PremiumAppIcon.getLocalizedTitle(): String {
    return when (this) {
        PremiumAppIcon.DEFAULT -> stringResource(SharedRes.string.icon_default)
        PremiumAppIcon.LIGHT -> stringResource(SharedRes.string.icon_light)
        PremiumAppIcon.GOLD_ELITE -> stringResource(SharedRes.string.icon_gold_elite)
        PremiumAppIcon.GOLD_ELITE_DARK -> stringResource(SharedRes.string.icon_gold_elite_dark)
        PremiumAppIcon.STEALTH -> stringResource(SharedRes.string.icon_stealth)
        PremiumAppIcon.STEALTH_DARK -> stringResource(SharedRes.string.icon_stealth_dark)
        PremiumAppIcon.CYBER -> stringResource(SharedRes.string.icon_cyber)
        PremiumAppIcon.CYBER_DARK -> stringResource(SharedRes.string.icon_cyber_dark)
        PremiumAppIcon.SYNTHWAVE -> stringResource(SharedRes.string.icon_synthwave)
        PremiumAppIcon.SYNTHWAVE_DARK -> stringResource(SharedRes.string.icon_synthwave_dark)
        PremiumAppIcon.SYNTHWAVE_EXTRA -> stringResource(SharedRes.string.icon_synthwave_extra)
        PremiumAppIcon.ICE -> stringResource(SharedRes.string.icon_ice)
        PremiumAppIcon.ICE_DARK -> stringResource(SharedRes.string.icon_ice_dark)
        PremiumAppIcon.ICE_EXTRA -> stringResource(SharedRes.string.icon_ice_extra)
        PremiumAppIcon.CHAMPIONSHIP -> stringResource(SharedRes.string.icon_championship)
        PremiumAppIcon.CHAMPIONSHIP_DARK -> stringResource(SharedRes.string.icon_championship_dark)
        PremiumAppIcon.ADRENALIN -> stringResource(SharedRes.string.icon_adrenalin)
        PremiumAppIcon.ADRENALIN_DARK -> stringResource(SharedRes.string.icon_adrenalin_dark)
    }
}