package org.ttproject.screens

import BadgeDetailsDialog
import BadgesSection
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
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import org.ttproject.shared.resources.backhand
import org.ttproject.shared.resources.blade
import org.ttproject.shared.resources.dark
import org.ttproject.shared.resources.forehand
import org.ttproject.shared.resources.language
import org.ttproject.shared.resources.light
import org.ttproject.shared.resources.logout
import org.ttproject.shared.resources.my_gear
import org.ttproject.shared.resources.system_default
import org.ttproject.shared.resources.theme
import org.ttproject.viewmodel.ProfileState
import org.ttproject.viewmodel.ProfileViewModel
import org.ttproject.util.ThemeMode
import org.ttproject.shared.resources.Res as SharedRes
import coil3.compose.AsyncImage
import org.ttproject.shared.resources.cancel
import org.ttproject.shared.resources.save
import org.ttproject.shared.resources.username
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.coroutines.launch
import mapMetricsToBadges
import org.koin.compose.koinInject
import org.ttproject.AppThemeStyle
import org.ttproject.LocalAppThemeStyle
import org.ttproject.components.NativeDatePickerField
import org.ttproject.components.NativeDropdownField
import org.ttproject.components.PremiumAppIconSelector
import org.ttproject.components.PremiumThemeSelector
import org.ttproject.data.BadgeData
import org.ttproject.data.TokenStorage
import org.ttproject.icon.PremiumAppIcon
import org.ttproject.isIosPlatform

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProfileScreen(
    currentLanguage: String = "en",
    currentAppThemeStyle: AppThemeStyle = LocalAppThemeStyle.current,
    currentAppIcon: PremiumAppIcon = PremiumAppIcon.DEFAULT,
    isUserPremium: Boolean = true,
    currentThemeMode: ThemeMode = ThemeMode.System,
    onLogoutClick: () -> Unit = {},
    onChangeLanguage: (String) -> Unit = {},
    onChangeTheme: (ThemeMode) -> Unit = {},
    onChangeAppThemeStyle: (AppThemeStyle) -> Unit = {},
    onChangeAppIcon: (PremiumAppIcon) -> Unit = {},
    onOverlayActive: (Boolean) -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var isAvatarExpanded by remember { mutableStateOf(false) }

    var isEditUsernameModalOpen by remember { mutableStateOf(false) }
    var isEditBioModalOpen by remember { mutableStateOf(false) }
    var isEditGearModalOpen by remember { mutableStateOf(false) }
    var isEditBasicInfoModalOpen by remember { mutableStateOf(false) }
    var isMatchCardPreviewOpen by remember { mutableStateOf(false) }
    var selectedBadge by remember { mutableStateOf<BadgeData?>(null) }

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

    LaunchedEffect(isAvatarExpanded, isMatchCardPreviewOpen) {
        onOverlayActive(isAvatarExpanded || isMatchCardPreviewOpen)
    }

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

    if (selectedBadge != null) {
        BadgeDetailsDialog(
            badge = selectedBadge!!,
            onDismiss = { selectedBadge = null }
        )
    }

    LaunchedEffect(Unit) {
        if (viewModel.uiState.value !is ProfileState.Success) {
            viewModel.fetchUserProfile()
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

            LaunchedEffect(userData.language) {
                if (userData.language != null && userData.language != currentLanguage) {
                    onChangeLanguage(userData.language!!)
                }
            }

            val scrollState = rememberScrollState()

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
                Box(modifier = Modifier.fillMaxSize()) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedVisibility(
                            visible = animateTrigger,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 50 }
                        ) {
                            Column {
                                ProfileHeader(
                                    isAvatarExpanded = isAvatarExpanded, // 👈 Triggers source morph
                                    isMatchCardPreviewOpen = isMatchCardPreviewOpen, // 👈 Triggers source morph
                                    profileData = userData,
                                    onAvatarClick = { isAvatarExpanded = true },
                                    onPhotoEditClick = { singleImagePicker.launch() },
                                    onUsernameEditClick = { isEditUsernameModalOpen = true },
                                    onBioEditClick = { isEditBioModalOpen = true },
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
                                    onEditClick = { isEditBasicInfoModalOpen = true }
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
                                    onGearEditClick = { isEditGearModalOpen = true }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        AnimatedVisibility(
                            visible = animateTrigger,
                            enter = fadeIn(tween(400, delayMillis = 300)) + slideInVertically(tween(400, delayMillis = 300)) { 50 }) {
                            Column {
                                SettingsAndLogout(
                                    currentLanguage = currentLanguage,
                                    currentThemeMode = currentThemeMode,
                                    currentAppThemeStyle = currentAppThemeStyle,
                                    currentAppIcon = currentAppIcon,
                                    isUserPremium = isUserPremium,
                                    onLogoutClick = { viewModel.clearProfile(); onLogoutClick() },
                                    onChangeLanguage = onChangeLanguage,
                                    onChangeTheme = onChangeTheme,
                                    onChangeAppThemeStyle = onChangeAppThemeStyle,
                                    onChangeAppIcon = onChangeAppIcon,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp)
                    )

                    // 🌟 DESTINATION 1: MORPHING AVATAR PREVIEW
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
                                singleImagePicker.launch()
                                isAvatarExpanded = false
                            }
                        )
                    }

                    // 🌟 DESTINATION 2: MORPHING MATCHCARD PREVIEW
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
            badgeMetrics = profileData.badgeMetrics
        )
    }

    val cardGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF3B4CCA), Color(0xFF151C2C))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures { /* Block bubbling */ } }
        ) {
            Text(
                text = "THIS IS HOW OTHERS SEE YOU",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 👇 Morph boundary Target
            Box(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "matchcard_transition"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .fillMaxWidth(0.85f)
                    .aspectRatio(3f / 4f)
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
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onDismissRequest() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 👇 Morph boundary Target
            Box(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "avatar_transition"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
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
                        color = AppColors.TextPrimary,
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
                Text("Upload Photo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        IconButton(
            onClick = onDismissRequest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 24.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close Preview", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.ProfileHeader(
    isAvatarExpanded: Boolean, // 👈 New: Drives source morph state
    isMatchCardPreviewOpen: Boolean, // 👈 New: Drives source morph state
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 📦 FIXED SIZE WRAPPER: Prevents layout collapse when the avatar flies out
        Box(modifier = Modifier.size(100.dp).clickable { onAvatarClick() }) {

            // 👇 Morph boundary Source - Disappears exactly when the big one appears
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
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Edit Profile Picture", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUsernameEditClick() }.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = username, color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.Edit, contentDescription = "Edit Username", tint = AppColors.TextGray, modifier = Modifier.size(18.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onBioEditClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = bio.takeIf { it.isNotBlank() } ?: "Add a bio...",
                color = if (bio.isNotBlank()) AppColors.TextPrimary else AppColors.TextGray,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📦 FIXED SIZE WRAPPER: Prevents UI jumping when button leaves
        Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {

            // 👇 Morph boundary Source - Disappears exactly when the big Matchcard appears
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
                    Text("PREVIEW MATCHCARD", color = AppColors.AccentOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// --- STANDARD LEAF CORE SNEAK DIALOG LAYOUTS RETAINED BELOW (UNTOUCHED) ---

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
            Text("Edit Basic Info", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            NativeDatePickerField(
                value = birthDate,
                label = "BIRTH DATE",
                onDateSelected = { birthDate = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            NativeDropdownField(
                value = level,
                label = "SKILL LEVEL",
                options = skillLevels,
                onOptionSelected = { level = it }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = Color.Gray) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(birthDate, level) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = Color.White)
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
            Text("BASIC INFO", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Info", tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val birthDate = profileData.birthDate ?: "Set birth date"
        val ageDisplay = profileData.age?.toString() ?: "Set age"
        val skillLevel = profileData.skillLevel ?: "Set level"

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.SurfaceDark)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "AGE", color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                Text(text = "LEVEL", color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
        Text(text = label.uppercase(), color = AppColors.TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppColors.AccentOrange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
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
            Text("Frame Your Avatar", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

            Text("Adjust Position", color = Color.Gray, fontSize = 14.sp)
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
                    Text("Cancel", color = Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(verticalBias) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)
                ) {
                    Text("Save & Upload", color = Color.White)
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

        GearItem(
            label = stringResource(SharedRes.string.blade).uppercase(),
            value = profileData.blade ?: "Butterfly Viscaria",
            iconContent = { Text("🏓", fontSize = 16.sp) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        GearItem(
            label = stringResource(SharedRes.string.forehand).uppercase(),
            value = profileData.rubberFh ?: "Tenergy 05",
            iconContent = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF4B4B))) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        GearItem(
            label = stringResource(SharedRes.string.backhand).uppercase(),
            value = profileData.rubberBh ?: "DHS Hurricane 3 Neo",
            iconContent = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.Black)) }
        )
    }
}

@Composable
private fun GearItem(
    label: String,
    value: String,
    iconContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceDark)
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
private fun SettingsAndLogout(
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
    tokenStorage: TokenStorage = koinInject()
) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        PremiumThemeSelector(
            currentThemeStyle = currentAppThemeStyle,
            isUserPremium = isUserPremium,
            onThemeSelected = onChangeAppThemeStyle,
            onPremiumLockedClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("Ez egy prémium téma! Oldd fel az előfizetéssel.")
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PremiumAppIconSelector(
            currentAppIcon = currentAppIcon,
            isUserPremium = isUserPremium,
            onIconSelected = onChangeAppIcon,
            onPremiumLockedClick = {
                scope.launch { snackbarHostState.showSnackbar("Ez egy prémium ikon! Oldd fel az előfizetéssel.") }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        LanguageSelector(currentLanguage, onChangeLanguage, viewModel)

        Spacer(modifier = Modifier.height(12.dp))

        ThemeSelector(currentThemeMode, onChangeTheme)

        Spacer(modifier = Modifier.height(12.dp))

        if (isIosPlatform()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.SurfaceDark)
                    .clickable {
                        tokenStorage.clearMapChoice()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Map preference reset successfully",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "Reset Map Choice",
                    tint = AppColors.TextGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Reset Map Choice",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

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

@Composable
private fun LanguageSelector(
    currentLanguage: String,
    onChangeLanguage: (String) -> Unit,
    viewModel: ProfileViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    val displayLanguage = when (currentLanguage) {
        "hu" -> "Magyar"
        "en" -> "English"
        else -> "English"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceDark)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Language, contentDescription = null, tint = TextGray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(stringResource(SharedRes.string.language), color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.weight(1f))

            Text(displayLanguage, color = TextGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextGray, modifier = Modifier.rotate(rotation))
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
            Column(modifier = Modifier.fillMaxWidth().clipToBounds().padding(bottom = 8.dp)) {
                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                LanguageOptionRow("English", currentLanguage == "en") { viewModel.changeLanguage("en"); onChangeLanguage("en"); viewModel.fetchUserProfile(); expanded = false }
                LanguageOptionRow("Magyar", currentLanguage == "hu") { viewModel.changeLanguage("hu"); onChangeLanguage("hu"); viewModel.fetchUserProfile(); expanded = false }
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val textColor = if (isSelected) AppColors.AccentOrange else AppColors.TextPrimary
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 56.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = textColor, fontSize = 15.sp, fontWeight = fontWeight)
        Spacer(modifier = Modifier.weight(1f))
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = AppColors.AccentOrange, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ThemeSelector(currentThemeMode: ThemeMode, onChangeTheme: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    val displayTheme = when (currentThemeMode) {
        ThemeMode.Light -> stringResource(SharedRes.string.light)
        ThemeMode.Dark -> stringResource(SharedRes.string.dark)
        ThemeMode.System -> stringResource(SharedRes.string.system_default)
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.SurfaceDark).animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(stringResource(SharedRes.string.theme), color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.weight(1f))

            Text(displayTheme, color = AppColors.TextGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.rotate(rotation))
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
            Column(modifier = Modifier.fillMaxWidth().clipToBounds().padding(bottom = 8.dp)) {
                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                ThemeOptionRow(stringResource(SharedRes.string.system_default), currentThemeMode == ThemeMode.System) { onChangeTheme(ThemeMode.System) }
                ThemeOptionRow(stringResource(SharedRes.string.light), currentThemeMode == ThemeMode.Light) { onChangeTheme(ThemeMode.Light) }
                ThemeOptionRow(stringResource(SharedRes.string.dark), currentThemeMode == ThemeMode.Dark) { onChangeTheme(ThemeMode.Dark) }
            }
        }
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
            Text("Edit Username", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            EditTextField(
                label = stringResource(SharedRes.string.username),
                value = name,
                modifier = Modifier.focusRequester(focusRequester)
            ) { name = it }

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = Color.Gray) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(name) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = Color.White)
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
            Text("Edit Bio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 150) bio = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                minLines = 3,
                maxLines = 5,
                placeholder = { Text("Tell everyone a bit about your playstyle...", color = AppColors.TextGray.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = AppColors.AccentOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = AppColors.AccentOrange
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = Color.Gray) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(bio) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = Color.White)
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
            Text("Edit Gear", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                TextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.cancel), color = Color.Gray) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(blade, forehand, backhand) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange)) {
                    Text(stringResource(SharedRes.string.save), color = Color.White)
                }
            }
        }
    }
}