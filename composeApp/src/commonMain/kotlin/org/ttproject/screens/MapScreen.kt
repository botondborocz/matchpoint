package org.ttproject.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.ttproject.AppColors
import org.ttproject.shared.resources.free
import org.ttproject.shared.resources.indoor
import org.ttproject.shared.resources.nearby_clubs
import org.ttproject.shared.resources.outdoor
import org.ttproject.util.LocalThemeMode
import org.ttproject.util.ThemeMode
import kotlin.math.roundToInt
import org.ttproject.shared.resources.Res as SharedRes
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Velocity
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.data.LocationType
import org.ttproject.data.TokenStorage
import org.ttproject.isIosPlatform
import org.ttproject.viewmodel.LocationViewModel
import org.ttproject.viewmodel.LocationsUiState
import ttproject.composeapp.generated.resources.Res
import ttproject.composeapp.generated.resources.apple_logo_white
import ttproject.composeapp.generated.resources.google_logo_white

data class TTClub(
    val id: String, val name: String, val distance: String, val tables: Int,
    val rating: Double, val lat: Double, val lng: Double, val tags: List<String>, val type: LocationType
)

enum class SheetState { Expanded, HalfExpanded, Collapsed }

data class MapBounds(val north: Double, val south: Double, val east: Double, val west: Double)

@Composable
expect fun NativeMap(
    modifier: Modifier, locations: List<TTClub>, selectedClub: TTClub?,
    userLocationTrigger: Int, bottomPadding: Dp, isDark: Boolean,
    onMarkerClick: (TTClub) -> Unit, onBoundsChanged: (MapBounds) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
                    id = loc.id?.toString() ?: "", name = loc.name, distance = "Calculating...",
                    tables = loc.tableCount, rating = 0.0, lat = loc.latitude, lng = loc.longitude,
                    tags = listOf(loc.type.name, if (loc.isFree) "Free" else "Paid"), type = loc.type
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

    var isAddingTable by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
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
            selectedClub = clickedClub
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = this.maxHeight
        val screenWidth = this.maxWidth
        val layoutHeightPx = constraints.maxHeight.toFloat()
        val layoutWidthPx = constraints.maxWidth.toFloat()

        val dynamicBottomOffset = 90.dp + systemNavHeight
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

        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.offset }.collect { offset ->
                if (!offset.isNaN()) {
                    val collapsedOffset = layoutHeightPx - peekHeightPx
                    if (offset < collapsedOffset - 15f && selectedClub != null) {
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
            visible = isActive && !isDetailsExpanded,
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

        // 👇 1. Calculate exactly how far up the sheet is currently covering the screen
        val sheetOffsetYRaw = if (sheetState.offset.isNaN()) (layoutHeightPx - peekHeightPx) else sheetState.offset
        val sheetVisibleHeightDp = with(density) { (layoutHeightPx - sheetOffsetYRaw).coerceAtLeast(0f).toDp() }

        // 👇 2. "CENTER ON ME" BUTTON
        AnimatedVisibility(
            visible = showFloatingElements && isActive, // Hides when adding table
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
                // 👇 Lock the standard FAB to 16.dp radius and 8.dp shadow to match our custom button
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on me")
            }
        }

        // 👇 3. THE MORPHING "ADD TABLE" BUTTON
        val expandProgress by animateFloatAsState(
            targetValue = if (isAddingTable) 1f else 0f,
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            label = "ExpandProgress"
        )

        val restingBottomPadding = sheetVisibleHeightDp + 76.dp
        val currentBottomPadding = restingBottomPadding * (1f - expandProgress)
        val currentEndPadding = 16.dp * (1f - expandProgress)

        // 👇 THE FIX: Changed from 24.dp to 16.dp so it perfectly matches the FAB above it!
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
                shape = RoundedCornerShape(currentRadius), // Will now be 16.dp when resting!
                color = formBgColor,
                shadowElevation = currentElevation,
                modifier = Modifier
                    .padding(bottom = currentBottomPadding, end = currentEndPadding)
                    .size(width = currentWidth, height = currentHeight)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    // BUTTON STATE
                    if (expandProgress < 1f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(1f - expandProgress)
                                .clickable { isAddingTable = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Club", tint = brandOrange)
                        }
                    }

                    // FULL SCREEN STATE
                    if (expandProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(expandProgress)
                        ) {
                            Box(modifier = Modifier.requiredSize(screenWidth, screenHeight)) {
                                AddTableFullScreen(
                                    brandOrange = brandOrange,
                                    cardBg = cardBg,
                                    systemNavHeight = systemNavHeight,
                                    onClose = { isAddingTable = false }
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
            // 👇 1. Added BottomCenter alignment here to keep the master block symmetrical
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
                    color = cardBg,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = isDetailsExpanded,
                        transitionSpec = {
                            fadeIn(tween(250)) togetherWith fadeOut(tween(250)) using SizeTransform(clip = true) { _, _ ->
                                spring(stiffness = Spring.StiffnessLow)
                            }
                        },
                        // 👇 2. Force TopCenter alignment during morphing to stop the left-edge snap!
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxWidth(),
                        label = "DetailsMorph"
                    ) { expanded ->
                        if (expanded) {
                            ClubDetailsFullScreen(
                                club = currentClub,
                                brandOrange = brandOrange,
                                onClose = { isDetailsExpanded = false }
                            )
                        } else {
                            ClubCardCompact(
                                club = currentClub,
                                brandOrange = brandOrange,
                                tokenStorage = tokenStorage,
                                onExpand = { isDetailsExpanded = true },
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

    // 👇 3. Force fillMaxWidth here so the width matches the expanded view perfectly
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333947)),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClubDetailsFullScreen(club: TTClub, brandOrange: Color, onClose: () -> Unit) {
    // Master list of tags
    val tags = listOf("Good Net", "Bring Own Net", "Smooth Surface", "Damaged Table", "Spacious", "Often Crowded", "Friendly Vibe")
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var reviewText by remember { mutableStateOf("") }

    // Dummy Aggregated Tag Data
    val aggregatedTags = mapOf(
        "Good Net" to 42,
        "Spacious" to 18,
        "Friendly Vibe" to 12
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(club.name, color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp).background(Color(0xFF333947), RoundedCornerShape(16.dp))) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // GALLERY WITH "ADD PHOTO" BUTTON
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(120.dp, 100.dp)
                        .background(Color(0xFF333947), RoundedCornerShape(12.dp))
                        .clickable { /* TODO: Launch Image Picker for Gallery */ },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Add Photo", tint = brandOrange)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add Photo", color = brandOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(4) {
                Box(
                    modifier = Modifier.size(160.dp, 100.dp).background(Color(0xFF2A2D34), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Image", color = Color.Gray)
                }
            }
        }

        // AGGREGATOR: What players are saying
        Text("What players say", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            aggregatedTags.forEach { (tag, count) ->
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF2A2D34)) {
                    Text(
                        text = "$tag ($count)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp, color = AppColors.TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DETAILED REVIEW FEED
        Text("Detailed Reviews", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        Surface(color = Color(0xFF2A2D34), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Great spot!", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text("Reviewed by Player 1 • 2 days ago", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

                Text("Really liked playing here. Just be warned that table #2 has a slight dead spot on the right side.", color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = Color(0xFF333947), shape = RoundedCornerShape(12.dp)) { Text("Good Net", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                    Surface(color = Color(0xFF333947), shape = RoundedCornerShape(12.dp)) { Text("Damaged Table", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF333947), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(24.dp))

        // ADD REVIEW SECTION
        Text("Write a Review", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))

        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { tag ->
                val isSelected = selectedTags.contains(tag)
                FilterChip(
                    text = tag,
                    isSelected = isSelected,
                    activeColor = brandOrange,
                    inactiveColor = Color(0xFF333947),
                    onClick = {
                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = reviewText,
            onValueChange = { reviewText = it },
            placeholder = { Text("Add more details... (Optional)", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 100.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2A2D34),
                unfocusedContainerColor = Color(0xFF2A2D34),
                focusedIndicatorColor = brandOrange,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = { /* TODO: Launch Image Picker */ },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(Icons.Default.Image, contentDescription = "Attach Photos", tint = brandOrange, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Attach Photos", color = brandOrange, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onClose() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
            shape = RoundedCornerShape(12.dp),
            enabled = selectedTags.isNotEmpty() || reviewText.isNotBlank()
        ) {
            Text("Submit Review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
fun AddTableFullScreen(brandOrange: Color, cardBg: Color, systemNavHeight: Dp, onClose: () -> Unit) {
    var isIndoor by remember { mutableStateOf(false) }
    var tableCount by remember { mutableStateOf(1) }
    var isFree by remember { mutableStateOf(true) }
    var notesText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.Background // Uses the main app background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        ) {
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add New Table", color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp).background(Color(0xFF333947), RoundedCornerShape(16.dp))) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // PHOTO SECTION (Big and inviting)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(cardBg, RoundedCornerShape(16.dp))
                        .clickable { /* TODO: Launch Image Picker */ },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Add Photo", tint = brandOrange, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add Table Photos", color = brandOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Optional, but highly recommended!", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Table Details", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                // TYPE TOGGLE
                Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Type", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(text = "Outdoor", isSelected = !isIndoor, activeColor = brandOrange, inactiveColor = Color(0xFF333947)) { isIndoor = false }
                            FilterChip(text = "Indoor", isSelected = isIndoor, activeColor = brandOrange, inactiveColor = Color(0xFF333947)) { isIndoor = true }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // COUNT STEPPER
                Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Number of Tables", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(
                                onClick = { if (tableCount > 1) tableCount-- },
                                modifier = Modifier.size(36.dp).background(Color(0xFF333947), CircleShape)
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

                // COST TOGGLE
                Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Cost", color = Color.LightGray, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(text = "Free", isSelected = isFree, activeColor = brandOrange, inactiveColor = Color(0xFF333947)) { isFree = true }
                            FilterChip(text = "Paid", isSelected = !isFree, activeColor = brandOrange, inactiveColor = Color(0xFF333947)) { isFree = false }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Notes (Optional)", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                // NOTES
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    placeholder = { Text("Bring your own net, usually crowded at 6pm, etc...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg,
                        focusedIndicatorColor = brandOrange,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Extra spacer so content isn't hidden behind the submit button
                Spacer(modifier = Modifier.height(80.dp + systemNavHeight))
            }

            // SUBMIT BUTTON (Pinned to bottom)
            Surface(
                color = AppColors.Background.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { /* TODO: Submit to backend */ onClose() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + systemNavHeight)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Submit Table", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}