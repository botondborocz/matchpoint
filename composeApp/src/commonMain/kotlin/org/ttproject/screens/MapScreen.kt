package org.ttproject.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.ttproject.AppColors
import org.ttproject.shared.resources.indoor
import org.ttproject.shared.resources.nearby_clubs
import org.ttproject.shared.resources.outdoor
import org.ttproject.util.LocalThemeMode
import org.ttproject.util.ThemeMode
import kotlin.math.roundToInt
import org.ttproject.shared.resources.Res as SharedRes
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.NavigationEventInfo
import coil3.compose.AsyncImage
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.components.FullScreenDialog
import org.ttproject.components.InAppNotification
import org.ttproject.components.NativeImageActionMenu
import org.ttproject.components.SetSystemBarsVisibility
import org.ttproject.data.LocationType
import org.ttproject.data.TokenStorage
import org.ttproject.isIosPlatform
import org.ttproject.viewmodel.LocationViewModel
import org.ttproject.viewmodel.LocationsUiState
import ttproject.composeapp.generated.resources.Res
import ttproject.composeapp.generated.resources.apple_logo_white
import ttproject.composeapp.generated.resources.google_logo_white
import kotlin.math.absoluteValue

data class TTClub(
    val id: String, val name: String, val distance: String, val tables: Int,
    val rating: Double, val lat: Double, val lng: Double, val tags: List<String>, val type: LocationType,
    val createdBy: String = "Anonymous",
    val imageUrls: List<String> = emptyList()
)

data class GalleryImage(val url: String, val authorName: String, val isMine: Boolean = false)

enum class SheetState { Expanded, HalfExpanded, Collapsed }

data class MapBounds(val north: Double, val south: Double, val east: Double, val west: Double)

@Composable
expect fun NativeMap(
    modifier: Modifier, locations: List<TTClub>, selectedClub: TTClub?,
    userLocationTrigger: Int, bottomPadding: Dp, isDark: Boolean,
    onMarkerClick: (TTClub) -> Unit, onBoundsChanged: (MapBounds) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun MapScreen(
    viewModel: LocationViewModel = koinViewModel(),
    tokenStorage: TokenStorage = koinInject(),
    isActive: Boolean = true,
    bottomNavHeight: Dp = 0.dp,
    systemNavHeight: Dp = 0.dp,
    onNavBarVisibilityChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.fetchNearbyLocations() }

    val clubs = remember(state) {
        when (val s = state) {
            is LocationsUiState.Success -> s.locations.map { loc ->
                TTClub(
                    id = loc.id ?: "${loc.latitude},${loc.longitude}",
                    name = loc.name,
                    distance = "Calculating...",
                    tables = loc.tableCount,
                    rating = 0.0,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    tags = listOf(loc.type.name, if (loc.isFree) "Free" else "Paid"),
                    type = loc.type,
                    createdBy = loc.createdBy ?: "Anonymous",
                    imageUrls = loc.imageUrls
                )
            }
            else -> emptyList()
        }
    }

    val sheetBg = AppColors.Background
    val cardBg = AppColors.SurfaceDark
    val brandOrange = AppColors.AccentOrange
    val density = LocalDensity.current

    val isDark = when (LocalThemeMode.current) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

    var selectedClub by remember { mutableStateOf<TTClub?>(null) }
    var isDetailsExpanded by remember { mutableStateOf(false) }

    val detailsOffsetAnimatable = remember { Animatable(0f) }
    var isCardAnimating by remember { mutableStateOf(false) }

    var isAddingTable by remember { mutableStateOf(false) }
    var isPickingLocation by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val mapNavState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = mapNavState,
        isBackEnabled = isDetailsExpanded || isAddingTable || isPickingLocation,
        onBackCompleted = {
            when {
                isPickingLocation -> isPickingLocation = false
                isAddingTable -> isAddingTable = false
                isDetailsExpanded -> {
                    isDetailsExpanded = false
                    coroutineScope.launch {
                        detailsOffsetAnimatable.snapTo(0f)
                    }
                }
                selectedClub != null -> {
                    selectedClub = null
                }
            }
        }
    )

    var userLocationTrigger by remember { mutableStateOf(0) }
    var mapBounds by remember { mutableStateOf<MapBounds?>(null) }

    var isIndoorSelected by remember { mutableStateOf(false) }
    var isOutdoorSelected by remember { mutableStateOf(false) }
    var isFreeSelected by remember { mutableStateOf(false) }

    val visibleClubs = remember(clubs, mapBounds, isIndoorSelected, isOutdoorSelected, isFreeSelected) {
        clubs.filter { club ->
            val boundsMatch = mapBounds?.let { bounds ->
                val isInsideLat = club.lat in bounds.south..bounds.north
                val isInsideLng = if (bounds.west <= bounds.east) {
                    club.lng in bounds.west..bounds.east
                } else {
                    club.lng >= bounds.west || club.lng <= bounds.east
                }
                isInsideLat && isInsideLng
            } ?: true

            val isIndoor = club.type.name.equals("INDOOR", ignoreCase = true)
            val isOutdoor = club.type.name.equals("OUTDOOR", ignoreCase = true)

            val typeMatch = when {
                isIndoorSelected && isOutdoorSelected -> true
                !isIndoorSelected && !isOutdoorSelected -> true
                isIndoorSelected -> isIndoor
                isOutdoorSelected -> isOutdoor
                else -> true
            }

            val freeMatch = if (isFreeSelected) club.tags.contains("Free") else true
            boundsMatch && typeMatch && freeMatch
        }
    }

    val sheetState = remember {
        AnchoredDraggableState(
            initialValue = SheetState.Collapsed,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(),
            decayAnimationSpec = exponentialDecay()
        )
    }

    val nestedScrollConnection = remember(sheetState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta < 0 && source == NestedScrollSource.UserInput) Offset(0f, sheetState.dispatchRawDelta(delta)) else Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta > 0 && source == NestedScrollSource.UserInput) Offset(0f, sheetState.dispatchRawDelta(delta)) else Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val toFling = available.y
                return if (toFling < 0 && sheetState.offset > 0f) {
                    sheetState.settle(toFling)
                    Velocity(0f, toFling)
                } else Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                sheetState.settle(available.y)
                return Velocity(0f, available.y)
            }
        }
    }

    val handleClubSelection: (TTClub) -> Unit = { clickedClub ->
        coroutineScope.launch {
            if (sheetState.currentValue != SheetState.Collapsed) sheetState.animateTo(SheetState.Collapsed)
            isDetailsExpanded = false
            detailsOffsetAnimatable.snapTo(0f)
            selectedClub = clickedClub
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = this.maxHeight
        val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
        val screenWidth = this.maxWidth
        val layoutHeightPx = constraints.maxHeight.toFloat()
        val layoutWidthPx = constraints.maxWidth.toFloat()

        val dynamicBottomOffset = 150.dp + systemNavHeight
        val peekHeightPx = with(density) { dynamicBottomOffset.toPx() }

        val topInsetPx = WindowInsets.systemBars.getTop(density).toFloat()
        val expandedTopPx = topInsetPx + with(density) { 12.dp.toPx() }

        SideEffect {
            val anchors = DraggableAnchors {
                SheetState.Expanded at expandedTopPx
                SheetState.HalfExpanded at layoutHeightPx * 0.67f
                SheetState.Collapsed at layoutHeightPx - peekHeightPx
            }
            sheetState.updateAnchors(anchors)
        }

        // 👇 1. Capture the latest state values safely for the flow
        val currentIsDetailsExpanded by rememberUpdatedState(isDetailsExpanded)
        val currentCollapsedOffset by rememberUpdatedState(layoutHeightPx - peekHeightPx)

        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.offset }.collect { offset ->
                if (!offset.isNaN()) {
                    // 👇 2. Only dismiss the compact card if the details screen ISN'T expanded.
                    // This prevents layout jumps (like system bars toggling) from killing the full-screen view.
                    if (!currentIsDetailsExpanded && offset < currentCollapsedOffset - 15f && selectedClub != null) {
                        isDetailsExpanded = false
                        selectedClub = null
                    }
                }
            }
        }

        val targetBottomPadding = when (sheetState.targetValue) {
            SheetState.Expanded -> screenHeight
            SheetState.HalfExpanded -> screenHeight * 0.33f
            SheetState.Collapsed -> dynamicBottomOffset
        }

        val mapBottomPadding by animateDpAsState(
            targetValue = targetBottomPadding,
            animationSpec = spring(stiffness = Spring.StiffnessLow), label = "MapPadding"
        )

        val showFloatingElements = selectedClub == null && sheetState.targetValue != SheetState.Expanded
        val showMapNavBar = sheetState.targetValue != SheetState.Expanded && !isDetailsExpanded && !isAddingTable

        LaunchedEffect(showMapNavBar) {
            onNavBarVisibilityChange(showMapNavBar)
        }

        NativeMap(
            modifier = Modifier.fillMaxSize(),
            locations = visibleClubs,
            selectedClub = selectedClub,
            userLocationTrigger = userLocationTrigger,
            bottomPadding = mapBottomPadding,
            isDark = isDark,
            onMarkerClick = handleClubSelection,
            onBoundsChanged = { newBounds -> mapBounds = newBounds }
        )

        AnimatedVisibility(
            visible = isActive && !isDetailsExpanded && !isPickingLocation,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(400, delayMillis = 100)) + fadeIn(tween(400, delayMillis = 100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    stringResource(SharedRes.string.indoor),
                    isIndoorSelected,
                    brandOrange,
                    cardBg
                ) { isIndoorSelected = !isIndoorSelected }
                FilterChip(
                    stringResource(SharedRes.string.outdoor),
                    isOutdoorSelected,
                    brandOrange,
                    cardBg
                ) { isOutdoorSelected = !isOutdoorSelected }
            }
        }

        val sheetOffsetYRaw = if (sheetState.offset.isNaN()) (layoutHeightPx - peekHeightPx) else sheetState.offset
        val sheetVisibleHeightDp = with(density) { (layoutHeightPx - sheetOffsetYRaw).coerceAtLeast(0f).toDp() }

        AnimatedVisibility(
            visible = isPickingLocation,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = sheetVisibleHeightDp + 16.dp, start = 16.dp, end = 80.dp)
                .fillMaxWidth()
                .zIndex(19f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                shadowElevation = 8.dp,
                modifier = Modifier.height(108.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    Text(
                        text = "Drag the map to place the crosshair exactly over the table.",
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = { isPickingLocation = false },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Cancel", color = brandOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isPickingLocation,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -mapBottomPadding / 2)
                .zIndex(10f)
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(36.dp).offset(y = 2.dp))
                Icon(Icons.Default.Add, contentDescription = null, tint = brandOrange, modifier = Modifier.size(36.dp))
            }
        }

        AnimatedVisibility(
            visible = showFloatingElements && isActive,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = sheetVisibleHeightDp + 16.dp)
        ) {
            FloatingActionButton(
                onClick = { userLocationTrigger++ },
                containerColor = cardBg,
                contentColor = brandOrange,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on me")
            }
        }

        val expandProgress by animateFloatAsState(
            targetValue = if (isAddingTable) 1f else 0f,
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            label = "ExpandProgress"
        )

        val restingBottomPadding = sheetVisibleHeightDp + 76.dp
        val currentBottomPadding = restingBottomPadding * (1f - expandProgress)
        val currentEndPadding = 16.dp * (1f - expandProgress)

        val currentRadius = 16.dp * (1f - expandProgress)
        val currentElevation = 8.dp * (1f - expandProgress)

        val currentWidth = 48.dp + (screenWidth - 48.dp) * expandProgress
        val currentHeight = 48.dp + (screenHeight - 48.dp) * expandProgress

        val formBgColor by animateColorAsState(
            targetValue = if (isAddingTable) AppColors.Background else cardBg,
            label = "fabColor"
        )

        AnimatedVisibility(
            visible = (showFloatingElements && isActive) || isAddingTable,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).zIndex(20f)
        ) {
            Surface(
                shape = RoundedCornerShape(currentRadius),
                color = formBgColor,
                shadowElevation = currentElevation,
                modifier = Modifier
                    .padding(bottom = currentBottomPadding, end = currentEndPadding)
                    .size(width = currentWidth, height = currentHeight)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    if (expandProgress < 1f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(1f - expandProgress)
                                .clickable {
                                    if (!isPickingLocation) {
                                        isPickingLocation = true
                                        coroutineScope.launch { sheetState.animateTo(SheetState.Collapsed) }
                                    } else {
                                        isPickingLocation = false
                                        isAddingTable = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(targetState = isPickingLocation, label = "IconMorph") { picking ->
                                if (picking) {
                                    Icon(Icons.Default.Check, contentDescription = "Confirm", tint = brandOrange)
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = brandOrange)
                                }
                            }
                        }
                    }

                    if (expandProgress > 0f) {
                        Box(modifier = Modifier.fillMaxSize().alpha(expandProgress)) {
                            Box(modifier = Modifier.requiredSize(screenWidth, screenHeight)) {
                                val centerLat = mapBounds?.let { (it.north + it.south) / 2 } ?: 0.0
                                val centerLng = mapBounds?.let { (it.east + it.west) / 2 } ?: 0.0

                                AddTableFullScreen(
                                    brandOrange = brandOrange,
                                    cardBg = cardBg,
                                    systemNavHeight = systemNavHeight,
                                    lat = centerLat,
                                    lng = centerLng,
                                    viewModel = viewModel,
                                    onAdjustLocation = {
                                        isAddingTable = false
                                        isPickingLocation = true // 👇 This brings back the crosshairs and Check icon!
                                    },
                                    onClose = {
                                        isAddingTable = false
                                        isPickingLocation = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        val cardBottomPadding by animateDpAsState(
            targetValue = if (isDetailsExpanded) 0.dp else dynamicBottomOffset + 16.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow), label = ""
        )
        val cardSidePadding by animateDpAsState(
            targetValue = if (isDetailsExpanded) 0.dp else 16.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow), label = ""
        )
        val cardCornerRadius by animateDpAsState(
            targetValue = if (isDetailsExpanded) 0.dp else 16.dp,
            animationSpec = spring(stiffness = Spring.StiffnessLow), label = ""
        )

        AnimatedContent(
            targetState = selectedClub,
            transitionSpec = {
                if (targetState != null && initialState == null) {
                    (slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn())
                        .togetherWith(fadeOut())
                        .using(SizeTransform(clip = false))
                } else if (targetState == null && initialState != null) {
                    fadeIn().togetherWith(slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(tween(250)))
                        .using(SizeTransform(clip = false))
                } else {
                    fadeIn(tween(300)).togetherWith(fadeOut(tween(300))).using(SizeTransform(clip = false))
                }
            },
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = cardBottomPadding, start = cardSidePadding, end = cardSidePadding)
                .zIndex(1f)
                .graphicsLayer(clip = false),
            label = "ClubCardAnimation"
        ) { currentClub ->
            if (currentClub != null) {
                Surface(
                    shape = RoundedCornerShape(cardCornerRadius),
                    color = AppColors.Background,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, detailsOffsetAnimatable.value.roundToInt()) }
                ) {
                    AnimatedContent(
                        targetState = isDetailsExpanded,
                        transitionSpec = {
                            fadeIn(tween(250)) togetherWith fadeOut(tween(250)) using SizeTransform(clip = true) { _, _ ->
                                spring(stiffness = Spring.StiffnessLow)
                            }
                        },
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxWidth(),
                        label = "DetailsMorph"
                    ) { expanded ->
                        if (expanded) {
                            LaunchedEffect(currentClub.id) {
                                viewModel.loadReviewsForClub(currentClub.id)
                            }
                            val currentUserId = tokenStorage.getUserId() ?: ""

                            ClubDetailsFullScreen(
                                club = currentClub,
                                brandOrange = brandOrange,
                                systemNavHeight = systemNavHeight,
                                viewModel = viewModel,
                                currentUserId = currentUserId,
                                onClose = {
                                    isDetailsExpanded = false
                                    coroutineScope.launch {
                                        detailsOffsetAnimatable.snapTo(0f)
                                    }
                                },
                                currentOffset = detailsOffsetAnimatable.value,
                                onDragDelta = { delta ->
                                    coroutineScope.launch {
                                        detailsOffsetAnimatable.snapTo((detailsOffsetAnimatable.value + delta).coerceAtLeast(0f))
                                    }
                                },
                                onDragStopped = { velocity ->
                                    coroutineScope.launch {
                                        val currentOffset = detailsOffsetAnimatable.value

                                        val distanceThreshold = screenHeightPx / 3f
                                        val velocityThreshold = 300f

                                        val shouldDismiss = currentOffset > distanceThreshold || velocity > velocityThreshold

                                        if (shouldDismiss) {
                                            isDetailsExpanded = false
                                            detailsOffsetAnimatable.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                        } else {
                                            detailsOffsetAnimatable.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                        }
                                    }
                                }
                            )
                        } else {
                            ClubCardCompact(
                                club = currentClub,
                                brandOrange = brandOrange,
                                tokenStorage = tokenStorage,
                                onExpand = {
                                    isDetailsExpanded = true
                                    coroutineScope.launch { detailsOffsetAnimatable.snapTo(0f) }
                                },
                                onClose = { selectedClub = null }
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth())
            }
        }

        val maxOffsetPx = layoutHeightPx - peekHeightPx
        val sheetOffsetY = if (sheetState.offset.isNaN()) (layoutHeightPx - peekHeightPx).roundToInt() else sheetState.offset.roundToInt().coerceIn(0, maxOffsetPx.roundToInt())

        AnimatedVisibility(
            visible = isActive && !isDetailsExpanded,
            enter = slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(400, delayMillis = 50)) + fadeIn(tween(400, delayMillis = 50)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.zIndex(10f).offset { IntOffset(x = 0, y = sheetOffsetY) }
        ) {
            Surface(
                modifier = Modifier
                    .anchoredDraggable(state = sheetState, orientation = Orientation.Vertical)
                    .nestedScroll(nestedScrollConnection)
                    .fillMaxWidth()
                    .height(screenHeight),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = sheetBg, shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 12.dp)
                            .width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.5f)).align(Alignment.CenterHorizontally)
                    )
                    NearbyClubsList(visibleClubs, cardBg, brandOrange, systemNavHeight, handleClubSelection)
                }
            }
        }
    }
}

@Composable
fun ClubCardCompact(
    club: TTClub, brandOrange: Color, tokenStorage: TokenStorage,
    onExpand: () -> Unit, onClose: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var showMapChoice by remember { mutableStateOf(false) }
    val buttonHeight = 36.dp

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(club.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${club.distance} • ${club.tables} Tables", color = Color.Gray, fontSize = 14.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showMapChoice) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showMapChoice = false }, modifier = Modifier.height(buttonHeight)) {
                    Text("Cancel", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        uriHandler.openUri("https://maps.apple.com/?q=${club.lat},${club.lng}")
                        showMapChoice = false
                        tokenStorage.saveMapChoice("apple")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2D34)),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.height(buttonHeight), contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Image(painter = painterResource(Res.drawable.apple_logo_white), contentDescription = "Apple", modifier = Modifier.size(20.dp).padding(end = 6.dp))
                    Text("Apple", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        uriHandler.openUri("https://maps.google.com/?q=${club.lat},${club.lng}")
                        showMapChoice = false
                        tokenStorage.saveMapChoice("google")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.height(buttonHeight), contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Image(painter = painterResource(Res.drawable.google_logo_white), contentDescription = "Google", modifier = Modifier.size(20.dp).padding(end = 6.dp))
                    Text("Google", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onExpand,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Details", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (isIosPlatform()) {
                            if (tokenStorage.getMapChoice() != null) {
                                if (tokenStorage.getMapChoice() == "apple") uriHandler.openUri("https://maps.apple.com/?q=${club.lat},${club.lng}")
                                else uriHandler.openUri("https://maps.google.com/?q=${club.lat},${club.lng}")
                            } else showMapChoice = true
                        } else uriHandler.openUri("https://maps.google.com/?q=${club.lat},${club.lng}")
                    },
                    modifier = Modifier.weight(1f).height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Navigate", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FilterChip(
    text: String, isSelected: Boolean, activeColor: Color,
    inactiveColor: Color, onClick: () -> Unit
) {
    val bgColor by animateColorAsState(if (isSelected) activeColor else inactiveColor, label = "ChipColor")
    Surface(
        shape = RoundedCornerShape(20.dp), color = bgColor, contentColor = AppColors.TextPrimary,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onClick() }
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp)
    }
}

@Composable
fun NearbyClubsList(clubs: List<TTClub>, cardBg: Color, brandOrange: Color, systemNavHeight: Dp, onClubClick: (TTClub) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + systemNavHeight), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(SharedRes.string.nearby_clubs), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp)) }
        items(clubs) { club -> ClubCard(club, cardBg, brandOrange, onClick = { onClubClick(club) }) }
    }
}

@Composable
fun ClubCard(club: TTClub, cardBg: Color, brandOrange: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = cardBg, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp).background(Color(0xFF333947), RoundedCornerShape(8.dp)))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(club.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${club.distance} • ${club.tables} Tables", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnimatedVisibilityScope.AddTableFullScreen(
    brandOrange: Color,
    cardBg: Color,
    systemNavHeight: Dp,
    lat: Double,
    lng: Double,
    viewModel: LocationViewModel,
    onAdjustLocation: () -> Unit,
    onClose: () -> Unit
) {
    var isIndoor by remember { mutableStateOf(false) }
    var tableCount by remember { mutableStateOf(1) }
    var isFree by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    var selectedImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val hasUnsavedChanges = isIndoor || tableCount > 1 || !isFree || selectedImages.isNotEmpty()
    var showExitConfirmation by remember { mutableStateOf(false) }

    val offsetY = remember { Animatable(0f) }
    val scrollState = rememberScrollState()

    val attemptClose: () -> Unit = {
        if (hasUnsavedChanges && !isSubmitting) {
            showExitConfirmation = true
            scope.launch { offsetY.animateTo(0f, spring()) }
        } else {
            onClose()
        }
    }
    val currentAttemptClose by rememberUpdatedState(attemptClose)

    val tableNavState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = tableNavState,
        isBackEnabled = true,
        onBackCompleted = { currentAttemptClose() }
    )

    val mediaLauncher = rememberFilePickerLauncher(
        type = PickerType.Image,
        mode = PickerMode.Multiple(maxItems = 5),
        title = "Select Table Photos"
    ) { files ->
        if (files != null) {
            scope.launch {
                val newBytes = files.mapNotNull { it.readBytes() }
                selectedImages = (selectedImages + newBytes).take(5)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val distanceThreshold = screenHeightPx / 3f
        val velocityThreshold = 300f

        val nestedScrollConnection = remember(distanceThreshold, velocityThreshold) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0 && offsetY.value > 0f && source == NestedScrollSource.UserInput) {
                        val newOffset = (offsetY.value + available.y).coerceAtLeast(0f)
                        val consumed = newOffset - offsetY.value
                        scope.launch { offsetY.snapTo(newOffset) }
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && source == NestedScrollSource.UserInput) {
                        scope.launch { offsetY.snapTo(offsetY.value + available.y) }
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val shouldDismiss = offsetY.value > distanceThreshold ||
                            (offsetY.value > 10f && available.y > velocityThreshold)

                    if (shouldDismiss) {
                        currentAttemptClose()
                        return available
                    } else if (offsetY.value > 0f) {
                        scope.launch { offsetY.animateTo(0f, spring()) }
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            val topPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 40.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(250)
                        )
                    )
                    .padding(top = topPadding)
                    .offset { IntOffset(0, offsetY.value.toInt()) }
                    .nestedScroll(nestedScrollConnection)
                    .background(AppColors.Background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                // Top Bar with drag gestures
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount > 0 || offsetY.value > 0f) {
                                        scope.launch { offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f)) }
                                    }
                                },
                                onDragEnd = {
                                    if (offsetY.value > distanceThreshold) {
                                        currentAttemptClose()
                                    } else {
                                        scope.launch { offsetY.animateTo(0f, spring()) }
                                    }
                                }
                            )
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        IconButton(onClick = { currentAttemptClose() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Text(
                        text = "Add New Table",
                        color = AppColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 24.dp)) {

                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onAdjustLocation() }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).background(brandOrange.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Location", tint = brandOrange, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pinned Location", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("${lat.toString().take(8)}, ${lng.toString().take(8)}", color = Color.Gray, fontSize = 14.sp)
                            }
                            Text("Edit", color = brandOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (selectedImages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth().height(160.dp)
                                .background(cardBg, RoundedCornerShape(16.dp))
                                .clickable { mediaLauncher.launch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Add Photo", tint = brandOrange, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Add Table Photos", color = brandOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Optional, but highly recommended!", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        ) {
                            items(selectedImages) { imageBytes ->
                                Box(modifier = Modifier.size(160.dp).clip(RoundedCornerShape(16.dp))) {
                                    AsyncImage(
                                        model = imageBytes,
                                        contentDescription = "Selected Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            if (selectedImages.size < 5) {
                                item {
                                    Box(
                                        modifier = Modifier.size(160.dp).background(cardBg, RoundedCornerShape(16.dp)).clickable { mediaLauncher.launch() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add More", tint = brandOrange, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Table Details", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Type", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(text = "Outdoor", isSelected = !isIndoor, activeColor = brandOrange, inactiveColor = cardBg) { isIndoor = false }
                                FilterChip(text = "Indoor", isSelected = isIndoor, activeColor = brandOrange, inactiveColor = cardBg) { isIndoor = true }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Number of Tables", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                IconButton(
                                    onClick = { if (tableCount > 1) tableCount-- },
                                    modifier = Modifier.size(36.dp).background(AppColors.Background, CircleShape)
                                ) { Text("-", color = Color.White, fontWeight = FontWeight.Bold) }

                                Text("$tableCount", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                                IconButton(
                                    onClick = { if (tableCount < 20) tableCount++ },
                                    modifier = Modifier.size(36.dp).background(brandOrange, CircleShape)
                                ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cost", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(text = "Free", isSelected = isFree, activeColor = brandOrange, inactiveColor = cardBg) { isFree = true }
                                FilterChip(text = "Paid", isSelected = !isFree, activeColor = brandOrange, inactiveColor = cardBg) { isFree = false }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp + systemNavHeight))
                }

                Surface(color = AppColors.Background.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                viewModel.submitNewTable(
                                    lat = lat,
                                    lng = lng,
                                    isIndoor = isIndoor,
                                    count = tableCount,
                                    isFree = isFree,
                                    images = selectedImages,
                                    onSuccess = {
                                        isSubmitting = false
                                        onClose()
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp + systemNavHeight)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("Submit Table", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    containerColor = cardBg,
                    title = {
                        Text("Discard New Table?", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    },
                    text = {
                        Text("You have unsaved changes. Are you sure you want to discard this table?", color = Color.LightGray)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showExitConfirmation = false
                            onClose()
                        }) {
                            Text("Discard", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmation = false }) {
                            Text("Keep Editing", color = brandOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun AnimatedVisibilityScope.AddReviewFullScreen(
    clubName: String,
    brandOrange: Color,
    cardBg: Color,
    systemNavHeight: Dp,
    onSubmit: (tags: List<String>, text: String, images: List<ByteArray>, onSuccess: () -> Unit) -> Unit,
    onClose: () -> Unit
) {
    val tableConditionTags = listOf("Perfect surface", "Sturdy net", "Worn out / Damaged", "Torn net", "Slippery surface")
    val environmentTags = listOf("Spacious", "Wind-protected", "Good lighting", "Cramped space", "Glaring sun", "Poor lighting")
    val amenitiesTags = listOf("Drinking fountain", "Restroom available", "Usually crowded", "Quiet & Chill", "Paid access", "Free to play")

    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var reviewText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var reviewImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }

    val hasUnsavedChanges = selectedTags.isNotEmpty() || reviewText.isNotBlank() || reviewImages.isNotEmpty()
    var showExitConfirmation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val scrollState = rememberScrollState()

    // 👇 1. Get the FocusManager to handle hiding the keyboard
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val attemptClose: () -> Unit = {
        if (hasUnsavedChanges && !isSubmitting) {
            showExitConfirmation = true
            scope.launch { offsetY.animateTo(0f, spring()) }
        } else {
            onClose()
        }
    }

    val currentAttemptClose by rememberUpdatedState(attemptClose)

    val reviewNavState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = reviewNavState,
        isBackEnabled = true,
        onBackCompleted = { currentAttemptClose() }
    )

    val reviewMediaLauncher = rememberFilePickerLauncher(
        type = PickerType.Image,
        mode = PickerMode.Multiple(maxItems = 5)
    ) { files ->
        if (files != null) {
            scope.launch {
                val newBytes = files.mapNotNull { it.readBytes() }
                reviewImages = (reviewImages + newBytes).take(5)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val distanceThreshold = screenHeightPx / 3f
        val velocityThreshold = 300f

        val nestedScrollConnection = remember(distanceThreshold, velocityThreshold) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0 && offsetY.value > 0f && source == NestedScrollSource.UserInput) {
                        val newOffset = (offsetY.value + available.y).coerceAtLeast(0f)
                        val consumed = newOffset - offsetY.value
                        scope.launch { offsetY.snapTo(newOffset) }
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && source == NestedScrollSource.UserInput) {
                        scope.launch { offsetY.snapTo(offsetY.value + available.y) }
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val shouldDismiss = offsetY.value > distanceThreshold ||
                            (offsetY.value > 10f && available.y > velocityThreshold)

                    if (shouldDismiss) {
                        currentAttemptClose()
                        return available
                    } else if (offsetY.value > 0f) {
                        scope.launch { offsetY.animateTo(0f, spring()) }
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            val topPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 40.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(250)
                        )
                    )
                    .padding(top = topPadding)
                    .offset { IntOffset(0, offsetY.value.toInt()) }
                    .nestedScroll(nestedScrollConnection)
                    .background(AppColors.Background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    // 👇 2. Automatically apply bottom padding when the keyboard is visible
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount > 0 || offsetY.value > 0f) {
                                        scope.launch { offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f)) }
                                    }
                                },
                                onDragEnd = {
                                    if (offsetY.value > distanceThreshold) {
                                        currentAttemptClose()
                                    } else {
                                        scope.launch { offsetY.animateTo(0f, spring()) }
                                    }
                                }
                            )
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape) {
                        IconButton(onClick = { currentAttemptClose() }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                focusManager.clearFocus() // Drop focus when submitting
                                onSubmit(selectedTags.toList(), reviewText, reviewImages) { isSubmitting = false; onClose() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                        enabled = hasUnsavedChanges && !isSubmitting
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Submit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        // 👇 3. Clear focus & close keyboard if user taps empty space
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp)
                ) {

                    Text(clubName, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Text("Write Your Review", color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 24.dp))

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Add Photos", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${reviewImages.size} / 5", color = Color.Gray, fontSize = 14.sp)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 32.dp)
                    ) {
                        for (i in 0 until 5) {
                            if (i < reviewImages.size) {
                                Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp))) {
                                    AsyncImage(
                                        model = reviewImages[i], contentDescription = "Preview",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = {
                                            val mutableList = reviewImages.toMutableList()
                                            mutableList.removeAt(i)
                                            reviewImages = mutableList
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            } else if (i == reviewImages.size) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(cardBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .clickable { reviewMediaLauncher.launch() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Add Photo", tint = Color.Gray, modifier = Modifier.size(28.dp))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(Color.Transparent, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }

                    Surface(color = cardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Table Condition", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tableConditionTags.forEach { tag ->
                                    val isSelected = selectedTags.contains(tag)
                                    FilterChip(text = tag, isSelected = isSelected, activeColor = brandOrange, inactiveColor = AppColors.Background, onClick = { selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag })
                                }
                            }
                        }
                    }

                    Surface(color = cardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Playing Area & Environment", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                environmentTags.forEach { tag ->
                                    val isSelected = selectedTags.contains(tag)
                                    FilterChip(text = tag, isSelected = isSelected, activeColor = brandOrange, inactiveColor = AppColors.Background, onClick = { selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag })
                                }
                            }
                        }
                    }

                    Surface(color = cardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Amenities & Vibe", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                amenitiesTags.forEach { tag ->
                                    val isSelected = selectedTags.contains(tag)
                                    FilterChip(text = tag, isSelected = isSelected, activeColor = brandOrange, inactiveColor = AppColors.Background, onClick = { selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag })
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { if (it.length <= 500) reviewText = it },
                        placeholder = { Text("Describe your experience... (optional)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = cardBg,
                            unfocusedContainerColor = cardBg,
                            focusedIndicatorColor = brandOrange, // 👇 Changed from Color.Transparent to brandOrange
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            cursorColor = brandOrange
                        ),
                        shape = RoundedCornerShape(16.dp),
                        supportingText = {
                            Text("${reviewText.length} / 500", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    )

                    // 👇 5. Replaced huge spacer with standard 24dp padding
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Surface(
                    color = AppColors.Background.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                focusManager.clearFocus()
                                onSubmit(selectedTags.toList(), reviewText, reviewImages) { isSubmitting = false; onClose() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp + systemNavHeight).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                        shape = RoundedCornerShape(28.dp),
                        enabled = hasUnsavedChanges && !isSubmitting
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("Submit Review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    containerColor = cardBg,
                    title = {
                        Text("Discard Review?", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    },
                    text = {
                        Text("You have unsaved changes. Are you sure you want to discard this review?", color = Color.LightGray)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showExitConfirmation = false
                            onClose()
                        }) {
                            Text("Discard", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmation = false }) {
                            Text("Keep Editing", color = brandOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun ClubDetailsFullScreen(
    club: TTClub, brandOrange: Color, systemNavHeight: Dp,
    viewModel: LocationViewModel, currentUserId: String, onClose: () -> Unit,
    currentOffset: Float,
    onDragDelta: (Float) -> Unit,
    onDragStopped: (Float) -> Unit
) {
    val reviews by viewModel.clubReviews.collectAsState()
    val hasReviewed =
        remember(reviews, currentUserId) { reviews.any { it.userId == currentUserId } }

    // 👇 Updated to parse and assign `isMine` based on currentUserId
    val allGalleryImages = remember(club.imageUrls, reviews, club.createdBy, currentUserId) {
        val list = mutableListOf<GalleryImage>()
        val isClubCreatorMe = club.createdBy == currentUserId
        club.imageUrls.forEach { url ->
            list.add(
                GalleryImage(
                    url,
                    club.createdBy,
                    isClubCreatorMe
                )
            )
        }
        reviews.forEach { review ->
            val isMine = review.userId == currentUserId
            review.imageUrls.forEach { url -> list.add(GalleryImage(url, review.username, isMine)) }
        }
        list.distinctBy { it.url }
    }

    var fullScreenInitialPage by remember { mutableStateOf<Int?>(null) }
    var isWritingReview by remember { mutableStateOf(false) }

    val galleryNavState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = galleryNavState,
        isBackEnabled = fullScreenInitialPage != null,
        onBackCompleted = { fullScreenInitialPage = null }
    )

    val scope = rememberCoroutineScope()
    var isUploadingGallery by remember { mutableStateOf(false) }
    val galleryLauncher = rememberFilePickerLauncher(
        type = PickerType.Image,
        mode = PickerMode.Multiple(maxItems = 5)
    ) { files ->
        if (files != null && files.isNotEmpty()) {
            scope.launch {
                isUploadingGallery = true
                val newBytes = files.mapNotNull { it.readBytes() }
                viewModel.addLocationImages(club.id, newBytes) { isUploadingGallery = false }
            }
        }
    }

    val aggregatedTags =
        remember(reviews) { reviews.flatMap { it.tags }.groupingBy { it }.eachCount() }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val headerHeightDp = 320.dp
    val headerHeightPx = with(density) { headerHeightDp.toPx() }
    val topInsetPx = WindowInsets.systemBars.getTop(density).toFloat()

    var overscrollAmount by remember { mutableFloatStateOf(0f) }
    var initialSpacerY by remember { mutableFloatStateOf(Float.NaN) }

    val titleStartY = headerHeightPx
    val titleEndY = topInsetPx + with(density) { 20.dp.toPx() }
    val pinDistance = titleStartY - titleEndY

    val progress =
        if (pinDistance > 0) (scrollState.value.toFloat() / pinDistance).coerceIn(0f, 1f) else 1f

    val targetScale = 20f / 26f
    val currentScale = 1f - ((1f - targetScale) * progress)

    val startX = with(density) { 24.dp.toPx() }
    val endX = with(density) { 66.dp.toPx() }

    val currentOffsetState = rememberUpdatedState(currentOffset)
    val currentOnDragDelta = rememberUpdatedState(onDragDelta)
    val currentOnDragStopped = rememberUpdatedState(onDragStopped)

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && currentOffsetState.value > 0f && source == NestedScrollSource.UserInput) {
                    currentOnDragDelta.value(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    currentOnDragDelta.value(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentOffsetState.value > 5f || (available.y > 200f && scrollState.value == 0)) {
                    currentOnDragStopped.value(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (currentOffsetState.value > 0f) {
                    currentOnDragStopped.value(available.y)
                }
                return Velocity.Zero
            }
        }
    }

    var notificationMessage by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        InAppNotification(
            message = notificationMessage,
            onDismiss = { notificationMessage = null },
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        )

        val maxTextWidth = maxWidth - 80.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp + systemNavHeight)
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coords ->
                        val y = coords.boundsInRoot().top
                        if (initialSpacerY.isNaN()) {
                            initialSpacerY = y
                        } else {
                            overscrollAmount = maxOf(0f, y - initialSpacerY)
                        }
                    }
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(headerHeightDp).graphicsLayer {
                    val scrollOffset = scrollState.value.toFloat()
                    alpha = (1f - (scrollOffset / (headerHeightPx * 0.8f))).coerceIn(0f, 1f)
                    translationY = scrollOffset * 0.5f // Standard parallax
                }
            ) {
                val pagerState = rememberPagerState(pageCount = { maxOf(1, allGalleryImages.size) })
                if (allGalleryImages.isNotEmpty()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = allGalleryImages[page].url, contentDescription = "Hero Image",
                            modifier = Modifier.fillMaxSize()
                                .clickable { fullScreenInitialPage = page },
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(allGalleryImages.size) { iteration ->
                            val color =
                                if (pagerState.currentPage == iteration) Color.White else Color.White.copy(
                                    alpha = 0.5f
                                )
                            Box(
                                modifier = Modifier.padding(4.dp).clip(CircleShape)
                                    .background(color).size(6.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF333947)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().offset(y = (-24).dp).background(
                    AppColors.Background,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ).padding(24.dp)
            ) {
                Spacer(modifier = Modifier.height(34.dp))
                Spacer(modifier = Modifier.height(8.dp))
                val locationTypeStr = club.type.name.lowercase().replaceFirstChar { it.uppercase() }
                Text("Distance: ${club.distance}", color = Color.LightGray, fontSize = 14.sp)
                Text(
                    "Type: $locationTypeStr • ${club.tables} Tables",
                    color = brandOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "What People Say",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (aggregatedTags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        aggregatedTags.entries.sortedByDescending { it.value }
                            .forEach { (tag, count) ->
                                Surface(shape = RoundedCornerShape(20.dp), color = brandOrange) {
                                    Text(
                                        text = "$tag ($count)",
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Community Reviews (${reviews.size})",
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!hasReviewed) {
                        TextButton(
                            onClick = { isWritingReview = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Write a Review",
                                color = brandOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (reviews.isEmpty()) {
                    Text("No reviews yet. Be the first to review!", color = Color.Gray)
                } else {
                    reviews.filter { !it.textContent.isNullOrBlank() || it.tags.isNotEmpty() || it.imageUrls.isNotEmpty() }
                        .forEach { review ->
                            val displayName =
                                if (review.userId == currentUserId) "You" else review.username
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                val hue = (review.username.hashCode().absoluteValue % 360).toFloat()
                                val avatarColor =
                                    Color.hsv(hue = hue, saturation = 0.6f, value = 0.9f)
                                Box(
                                    modifier = Modifier.size(40.dp)
                                        .background(avatarColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        displayName.firstOrNull()?.uppercase() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displayName,
                                            color = AppColors.TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        if (review.userId == currentUserId) {
                                            IconButton(
                                                onClick = {
                                                    // TODO: Wire up to open an edit view / backend logic
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit Review",
                                                    tint = brandOrange,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (!review.textContent.isNullOrBlank()) Text(
                                        review.textContent!!,
                                        color = Color.LightGray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    if (review.tags.isNotEmpty()) {
                                        FlowRow(
                                            modifier = Modifier.padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            review.tags.forEach { tag ->
                                                Surface(
                                                    color = brandOrange.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        tag,
                                                        fontSize = 12.sp,
                                                        color = brandOrange,
                                                        modifier = Modifier.padding(
                                                            horizontal = 8.dp,
                                                            vertical = 4.dp
                                                        ),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (review.imageUrls.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                        ) {
                                            items(review.imageUrls) { url ->
                                                val globalIndex =
                                                    allGalleryImages.indexOfFirst { it.url == url }
                                                Box(
                                                    modifier = Modifier.size(60.dp)
                                                        .clip(RoundedCornerShape(8.dp)).clickable {
                                                            if (globalIndex != -1) fullScreenInitialPage =
                                                                globalIndex
                                                        }) {
                                                    AsyncImage(
                                                        model = url,
                                                        contentDescription = "Review Image",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = Color(0xFF333947),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                }
            }
        }

        val topBarBgAlpha = progress.coerceIn(0f, 1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Background.copy(alpha = topBarBgAlpha))
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragCancel = { currentOnDragStopped.value(0f) },
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0 || currentOffsetState.value > 0f) {
                                currentOnDragDelta.value(dragAmount)
                            }
                        },
                        onDragEnd = {
                            currentOnDragStopped.value(0f)
                        }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = AppColors.SurfaceDark, shape = CircleShape) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (hasReviewed) {
                Surface(color = brandOrange, shape = CircleShape) {
                    IconButton(
                        onClick = { galleryLauncher.launch() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isUploadingGallery) CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        else Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Add Photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        Text(
            text = club.name,
            color = AppColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(maxTextWidth).graphicsLayer {
                translationX = startX + (endX - startX) * progress
                translationY = maxOf(titleEndY, titleStartY - scrollState.value) + overscrollAmount
                scaleX = currentScale
                scaleY = currentScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        )

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        AppColors.Background.copy(alpha = 0.9f),
                        AppColors.Background
                    )
                )
            ).padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = systemNavHeight)
        ) {
            if (!hasReviewed) {
                Button(
                    onClick = { isWritingReview = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        "Add Your Review",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            } else {
                Text(
                    "Thanks for reviewing! 🎉",
                    color = brandOrange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        AnimatedVisibility(
            visible = isWritingReview,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.zIndex(100f)
        ) {
            AddReviewFullScreen(
                clubName = club.name,
                brandOrange = brandOrange,
                cardBg = AppColors.SurfaceDark,
                systemNavHeight = systemNavHeight,
                onSubmit = { tags, text, images, onSuccess ->
                    viewModel.submitReview(
                        locationId = club.id,
                        tags = tags,
                        text = text,
                        images = images,
                        onSuccess = onSuccess
                    )
                },
                onClose = { isWritingReview = false }
            )
        }

        var activeImages by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
        LaunchedEffect(fullScreenInitialPage) {
            if (fullScreenInitialPage != null) {
                activeImages = allGalleryImages
            }
        }

        val galleryFullState = rememberNavigationEventState(NavigationEventInfo.None)
        NavigationBackHandler(
            state = galleryFullState,
            isBackEnabled = fullScreenInitialPage != null,
            onBackCompleted = { fullScreenInitialPage = null }
        )

        var showGalleryDialog by remember { mutableStateOf(false) }

        LaunchedEffect(fullScreenInitialPage) {
            if (fullScreenInitialPage != null) {
                showGalleryDialog = true
            }
        }

        // 👇 1. Use standard KMP Dialog
        if (showGalleryDialog) {
            FullScreenDialog(
                onDismissRequest = { fullScreenInitialPage = null }
            ) {
                AnimatedVisibility(
                    visible = fullScreenInitialPage != null,
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
                    exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.zIndex(1000f)
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            if (fullScreenInitialPage == null) {
                                showGalleryDialog = false
                            }
                        }
                    }

                    if (activeImages.isNotEmpty()) {
                        val safePage = fullScreenInitialPage ?: 0
                        val pagerState = rememberPagerState(
                            initialPage = safePage,
                            pageCount = { activeImages.size }
                        )

                        val galleryOffsetY = remember { Animatable(0f) }
                        var isGalleryZoomed by remember { mutableStateOf(false) }

                        // 👇 1. Add new state for immersive mode
                        var isUiVisible by remember { mutableStateOf(true) }

                        // 👇 2. System bars now follow the UI state, not the zoom state
                        SetSystemBarsVisibility(isVisible = isUiVisible)

                        var isMenuReady by remember { mutableStateOf(false) }

                        LaunchedEffect(fullScreenInitialPage) {
                            if (fullScreenInitialPage != null) {
                                isMenuReady = false
                                delay(350)
                                isMenuReady = true
                            }
                        }

                        val isSwipingToDismiss = galleryOffsetY.value > 5f
                        val isPagingEnabled = !isGalleryZoomed && !isSwipingToDismiss

                        // 👇 3. Native menu transitions out when UI is hidden
                        val isTransitioning = !isMenuReady || !isUiVisible || isSwipingToDismiss

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(
                                    alpha = (1f - (galleryOffsetY.value / 1500f)).coerceIn(0f, 1f)
                                ))
                                // Keep this to protect the bottom nav bar area
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            val handleDragEnd = {
                                if (galleryOffsetY.value > 150f) {
                                    scope.launch {
                                        launch { galleryOffsetY.animateTo(2000f, tween(250)) }
                                        delay(100)
                                        fullScreenInitialPage = null
                                    }
                                } else {
                                    scope.launch { galleryOffsetY.animateTo(0f, spring()) }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset { IntOffset(0, galleryOffsetY.value.toInt()) }
                                    .pointerInput(isGalleryZoomed) {
                                        if (!isGalleryZoomed) {
                                            detectVerticalDragGestures(
                                                onDragEnd = { handleDragEnd() },
                                                onVerticalDrag = { change, dragAmount ->
                                                    if (galleryOffsetY.value > 0f || dragAmount > 0f) {
                                                        change.consume()
                                                        val newOffset =
                                                            (galleryOffsetY.value + dragAmount).coerceAtLeast(
                                                                0f
                                                            )
                                                        scope.launch {
                                                            galleryOffsetY.snapTo(
                                                                newOffset
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = isPagingEnabled
                                ) { page ->
                                    val image = activeImages[page]

                                    val scaleAnim = remember { Animatable(1f) }
                                    val offsetXAnim = remember { Animatable(0f) }
                                    val offsetYAnim = remember { Animatable(0f) }

                                    LaunchedEffect(pagerState.currentPage) {
                                        if (pagerState.currentPage != page) {
                                            scaleAnim.snapTo(1f)
                                            offsetXAnim.snapTo(0f)
                                            offsetYAnim.snapTo(0f)
                                        }
                                    }

                                    // 👇 4. Auto-hide UI when zoomed in, but DON'T auto-show when zoomed out
                                    LaunchedEffect(scaleAnim.value) {
                                        val zoomed = scaleAnim.value > 1.01f
                                        isGalleryZoomed = zoomed
                                        if (zoomed && isUiVisible) {
                                            isUiVisible = false
                                        }
                                    }

                                    // 👇 1. Load the painter so we can measure the EXACT image pixels!
                                    val painter =
                                        coil3.compose.rememberAsyncImagePainter(model = image.url)
                                    val latestIntrinsicSize by rememberUpdatedState(painter.intrinsicSize)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = { tapOffset ->
                                                        scope.launch {
                                                            if (scaleAnim.value > 1f) {
                                                                launch {
                                                                    scaleAnim.animateTo(
                                                                        1f,
                                                                        tween(
                                                                            300,
                                                                            easing = FastOutSlowInEasing
                                                                        )
                                                                    )
                                                                }
                                                                launch {
                                                                    offsetXAnim.animateTo(
                                                                        0f,
                                                                        tween(
                                                                            300,
                                                                            easing = FastOutSlowInEasing
                                                                        )
                                                                    )
                                                                }
                                                                launch {
                                                                    offsetYAnim.animateTo(
                                                                        0f,
                                                                        tween(
                                                                            300,
                                                                            easing = FastOutSlowInEasing
                                                                        )
                                                                    )
                                                                }
                                                            } else {
                                                                val targetScale = 2.5f
                                                                val boxWidth = size.width.toFloat()
                                                                val boxHeight =
                                                                    size.height.toFloat()

                                                                // 👇 2. Calculate the exact bounding box of the visual pixels
                                                                var imgWidth = boxWidth
                                                                var imgHeight = boxHeight
                                                                if (latestIntrinsicSize.width > 0 && latestIntrinsicSize.height > 0) {
                                                                    val fitScale = minOf(
                                                                        boxWidth / latestIntrinsicSize.width,
                                                                        boxHeight / latestIntrinsicSize.height
                                                                    )
                                                                    imgWidth =
                                                                        latestIntrinsicSize.width * fitScale
                                                                    imgHeight =
                                                                        latestIntrinsicSize.height * fitScale
                                                                }

                                                                val center = Offset(
                                                                    boxWidth / 2f,
                                                                    boxHeight / 2f
                                                                )
                                                                val targetX =
                                                                    (center.x - tapOffset.x) * targetScale
                                                                val targetY =
                                                                    (center.y - tapOffset.y) * targetScale

                                                                // 👇 3. Clamp using TRUE image size! If zoomed height is smaller than screen, maxPanY becomes 0 (locked!)
                                                                val maxPanX = maxOf(
                                                                    0f,
                                                                    (imgWidth * targetScale - boxWidth) / 2f
                                                                )
                                                                val maxPanY = maxOf(
                                                                    0f,
                                                                    (imgHeight * targetScale - boxHeight) / 2f
                                                                )

                                                                val clampedX = targetX.coerceIn(
                                                                    -maxPanX,
                                                                    maxPanX
                                                                )
                                                                val clampedY = targetY.coerceIn(
                                                                    -maxPanY,
                                                                    maxPanY
                                                                )

                                                                launch {
                                                                    scaleAnim.animateTo(
                                                                        targetScale,
                                                                        tween(
                                                                            300,
                                                                            easing = FastOutSlowInEasing
                                                                        )
                                                                    )
                                                                }
                                                                launch {
                                                                    offsetXAnim.animateTo(
                                                                        clampedX,
                                                                        tween(
                                                                            300,
                                                                            easing = FastOutSlowInEasing
                                                                        )
                                                                    )
                                                                }
                                                                launch {
                                                                    offsetYAnim.animateTo(
                                                                        clampedY,
                                                                        tween(
                                                                            300,
                                                                            easing = FastOutSlowInEasing
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onTap = {
                                                        if (scaleAnim.value <= 1.01f) {
                                                            isUiVisible = !isUiVisible
                                                        }
                                                    }
                                                )
                                            }
                                            .pointerInput(Unit) {
                                                awaitEachGesture {
                                                    awaitFirstDown()
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        val zoom = event.calculateZoom()
                                                        val pan = event.calculatePan()

                                                        if (scaleAnim.value > 1f || event.changes.size > 1) {
                                                            if (event.changes.size > 1) {
                                                                event.changes.forEach { it.consume() }
                                                            }

                                                            val newScale =
                                                                (scaleAnim.value * zoom).coerceIn(
                                                                    1f,
                                                                    5f
                                                                )

                                                            val boxWidth = size.width.toFloat()
                                                            val boxHeight = size.height.toFloat()

                                                            // 👇 4. Apply the same mathematical pixel boundary to manual dragging
                                                            var imgWidth = boxWidth
                                                            var imgHeight = boxHeight
                                                            if (latestIntrinsicSize.width > 0 && latestIntrinsicSize.height > 0) {
                                                                val fitScale = minOf(
                                                                    boxWidth / latestIntrinsicSize.width,
                                                                    boxHeight / latestIntrinsicSize.height
                                                                )
                                                                imgWidth =
                                                                    latestIntrinsicSize.width * fitScale
                                                                imgHeight =
                                                                    latestIntrinsicSize.height * fitScale
                                                            }

                                                            val maxPanX = maxOf(
                                                                0f,
                                                                (imgWidth * newScale - boxWidth) / 2f
                                                            )
                                                            val maxPanY = maxOf(
                                                                0f,
                                                                (imgHeight * newScale - boxHeight) / 2f
                                                            )

                                                            val newX =
                                                                (offsetXAnim.value + pan.x).coerceIn(
                                                                    -maxPanX,
                                                                    maxPanX
                                                                )
                                                            val newY =
                                                                (offsetYAnim.value + pan.y).coerceIn(
                                                                    -maxPanY,
                                                                    maxPanY
                                                                )

                                                            scope.launch {
                                                                scaleAnim.snapTo(newScale)
                                                                offsetXAnim.snapTo(newX)
                                                                offsetYAnim.snapTo(newY)
                                                            }
                                                        }
                                                    } while (event.changes.any { it.pressed })
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // 👇 5. Replace AsyncImage with Image(painter)
                                        Image(
                                            painter = painter,
                                            contentDescription = "Full Screen Image",
                                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                                scaleX = scaleAnim.value
                                                scaleY = scaleAnim.value
                                                translationX = offsetXAnim.value
                                                translationY = offsetYAnim.value
                                            },
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }

                                val uiAlpha by animateFloatAsState(
                                    targetValue = if (isUiVisible) 1f else 0f,
                                    animationSpec = tween(200),
                                    label = "uiAlpha"
                                )

                                // Floating Top UI Elements (Hidden when Zoomed)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            alpha = uiAlpha
                                        } // 👇 Fades out without destroying!
                                        .windowInsetsPadding(
                                            WindowInsets.systemBars.only(
                                                WindowInsetsSides.Top
                                            )
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(color = Color(0xFF333333), shape = CircleShape) {
                                        IconButton(
                                            onClick = {
                                                if (isUiVisible) {
                                                    scope.launch {
                                                        isMenuReady = false
                                                        delay(50)
                                                        fullScreenInitialPage = null
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Surface(color = Color(0xFF333333), shape = CircleShape) {
                                        Text(
                                            text = "${pagerState.currentPage + 1} of ${activeImages.size}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(
                                                horizontal = 16.dp,
                                                vertical = 8.dp
                                            )
                                        )
                                    }

                                    val currentImage =
                                        activeImages.getOrNull(pagerState.currentPage)

                                    NativeImageActionMenu(
                                        isMine = currentImage?.isMine == true,
                                        isTransitioning = isTransitioning, // Pass the state here!
                                        modifier = Modifier,
                                        onDelete = {
                                            currentImage?.url?.let { url ->
                                                viewModel.deleteImage(
                                                    locationId = club.id,
                                                    imageUrl = url
                                                ) {
                                                    fullScreenInitialPage = null
                                                    notificationMessage =
                                                        "Photo deleted successfully"
                                                }
                                            }
                                        },
                                        onReport = { reason ->
                                            currentImage?.url?.let { url ->
                                                viewModel.reportImage(
                                                    locationId = club.id,
                                                    imageUrl = url,
                                                    reason = reason
                                                ) {
                                                    notificationMessage =
                                                        "Photo reported for review"
                                                }
                                            }
                                        }
                                    )
                                }

                                // Bottom Author Bar (Hidden when Zoomed)
                                val currentImage = activeImages.getOrNull(pagerState.currentPage)
                                if (currentImage != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                alpha = uiAlpha
                                            } // 👇 Fades out without destroying!
                                            .windowInsetsPadding(
                                                WindowInsets.systemBars.only(
                                                    WindowInsetsSides.Bottom
                                                )
                                            )
                                            .padding(bottom = 24.dp, top = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = Color(0xFF333333),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 8.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("📸", fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Uploaded by ${currentImage.authorName}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp
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
        }
    }
}