package org.ttproject.screens

import MiniBadge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import mapMetricsToBadges
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.AppColors
import org.ttproject.data.Player
import org.ttproject.shared.resources.find
import org.ttproject.shared.resources.Res as SharedRes
import org.ttproject.shared.resources.find_your_match
import org.ttproject.shared.resources.find_your_match_unauth_title
import org.ttproject.shared.resources.login
import org.ttproject.shared.resources.register
import org.ttproject.viewmodel.MatchUiState
import org.ttproject.viewmodel.MatchViewModel
import kotlin.math.abs

@Composable
fun MatchScreen(
    viewModel: MatchViewModel = koinViewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToMessages: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var isLikesPopupOpen by remember { mutableStateOf(false) }
    var isPaywallDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        viewModel.loadPlayers(showLoading = false)
        viewModel.loadLikesFeed()
        viewModel.checkPremiumStatus()
    }

    val uiState by viewModel.uiState.collectAsState()
    val matchedPlayer by viewModel.matchedPlayer.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val likedMePlayers by viewModel.likedMePlayers.collectAsState()
    val isPremiumUser by viewModel.isPremiumUser.collectAsState()

    val cardGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF3B4CCA), Color(0xFF151C2C))
    )

    val isErrorState = uiState is MatchUiState.Error
    val errorMessage = (uiState as? MatchUiState.Error)?.message ?: ""

    val isUnauth = isErrorState && (
            errorMessage.contains("token", ignoreCase = true) ||
                    errorMessage.contains("401") ||
                    errorMessage.contains("unauthorized", ignoreCase = true)
            )

    val isGeneralError = isErrorState && !isUnauth

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {

        // --- MAIN WORKSPACE INTERFACE ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isErrorState || isLikesPopupOpen) Modifier.blur(16.dp) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // --- HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AppColors.AccentOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(SharedRes.string.find_your_match),
                        color = AppColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 🌟 FIX: Removed "if (likedMePlayers.isNotEmpty())" wrapper.
                // The floating counter pill is now permanently visible to give free users a clear upgrade path!
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.SurfaceDark)
                        .clickable { isLikesPopupOpen = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "❤️ ${likedMePlayers.size}",
                            color = AppColors.AccentOrange,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- PERSISTENT CARD STACK DISPLAY AREA ---
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val componentWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

                val maxAvailableWidth = maxWidth - 32.dp
                val maxAvailableHeight = maxHeight - 32.dp
                val targetRatio = 3f / 4f

                val availableRatio = maxAvailableWidth / maxAvailableHeight

                val (cardWidth, cardHeight) = if (availableRatio > targetRatio) {
                    val height = maxAvailableHeight
                    val width = height * targetRatio
                    width to height
                } else {
                    val width = minOf(maxAvailableWidth, 400.dp)
                    val height = width / targetRatio
                    width to height
                }

                val dynamicCardModifier = Modifier.size(width = cardWidth, height = cardHeight)

                when (uiState) {
                    is MatchUiState.Error -> {
                        MatchCard(
                            player = Player("dummy", "Table Tennis Fan", "Advanced", age = 28, elo = 1500, distanceKm = 5, isPremium = false),
                            backgroundBrush = cardGradient,
                            modifier = dynamicCardModifier
                        )
                    }

                    is MatchUiState.Loading -> {
                        CircularProgressIndicator(color = AppColors.AccentOrange)
                    }

                    is MatchUiState.Success -> {
                        val players = (uiState as MatchUiState.Success).players

                        if (players.isEmpty()) {
                            Text("No more matches nearby!", color = AppColors.TextPrimary)
                        } else {
                            val topPlayer = players.firstOrNull()
                            val offsetX = remember(topPlayer?.id) { Animatable(0f) }
                            val offsetY = remember(topPlayer?.id) { Animatable(0f) }
                            val coroutineScope = rememberCoroutineScope()

                            val triggerSwipe = { directionRight: Boolean, screenWidthPx: Float ->
                                coroutineScope.launch {
                                    val targetX = if (directionRight) screenWidthPx * 1.5f else -screenWidthPx * 1.5f
                                    val targetY = 200f
                                    launch { offsetY.animateTo(targetY, tween(250)) }
                                    offsetX.animateTo(targetX, tween(250))

                                    if (topPlayer != null) {
                                        viewModel.onPlayerSwiped(topPlayer, isLiked = directionRight)
                                    }
                                }
                            }

                            Box(contentAlignment = Alignment.Center) {
                                if (players.size > 1) {
                                    MatchCard(
                                        player = players[1],
                                        backgroundBrush = cardGradient,
                                        modifier = dynamicCardModifier.graphicsLayer {
                                            scaleX = 0.95f
                                            scaleY = 0.95f
                                        }
                                    )
                                }

                                SwipeableMatchCard(
                                    player = topPlayer!!,
                                    backgroundBrush = cardGradient,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    componentWidthPx = componentWidthPx,
                                    onSwipeComplete = { directionRight ->
                                        triggerSwipe(directionRight, componentWidthPx)
                                    },
                                    modifier = dynamicCardModifier
                                )
                            }
                        }
                    }
                }
            }

            // --- BOTTOM ACTION ROW BAR ---
            if (uiState is MatchUiState.Success) {
                val players = (uiState as MatchUiState.Success).players
                val topPlayer = players.firstOrNull()
                val coroutineScope = rememberCoroutineScope()

                val triggerSwipe = { directionRight: Boolean ->
                    coroutineScope.launch {
                        if (topPlayer != null) {
                            viewModel.onPlayerSwiped(topPlayer, isLiked = directionRight)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ActionButtonsRow(
                        onLike = { triggerSwipe(true) },
                        onPass = { triggerSwipe(false) },
                        canUndo = canUndo,
                        onUndo = {
                            if (isPremiumUser) {
                                viewModel.undoLastSwipe()
                            } else {
                                isPaywallDialogOpen = true
                            }
                        }
                    )
                }
            }
        }

        // --- THE LIKES YOU OVERLAY POPUP MODAL ---
        AnimatedVisibility(
            visible = isLikesPopupOpen,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.95f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LikesYouPopupOverlay(
                players = likedMePlayers,
                isPremiumUser = isPremiumUser,
                cardGradient = cardGradient,
                onDismiss = { isLikesPopupOpen = false },
                onSelectPlayer = { player ->
                    isLikesPopupOpen = false
                },
                onUpgradeClick = { viewModel.togglePremiumStatus() }
            )
        }

        // --- UNAUTH OVERLAY ---
        AnimatedVisibility(
            visible = isUnauth,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.95f),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E2532))
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(text = stringResource(SharedRes.string.find_your_match_unauth_title), color = AppColors.AccentOrange, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 30.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(SharedRes.string.find), color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(AppColors.AccentOrange).clickable { onNavigateToLogin() }, contentAlignment = Alignment.Center) {
                        Text(text = stringResource(SharedRes.string.login) + " / " + stringResource(SharedRes.string.register), color = Color(0xFF11151E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- GENERAL ERROR OVERLAY ---
        AnimatedVisibility(
            visible = isGeneralError,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF151C2C))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .padding(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Error", tint = AppColors.ErrorText, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Oops!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage, color = Color.LightGray, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(AppColors.AccentOrange).clickable { viewModel.loadPlayers() }.padding(horizontal = 32.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TRY AGAIN", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- "IT'S A MATCH" CELEBRATION OVERLAY ---
        AnimatedVisibility(
            visible = matchedPlayer != null,
            enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            matchedPlayer?.let { player ->
                MatchCelebrationOverlay(player = player, onKeepSwiping = { viewModel.dismissMatchPopup() }, onSendMessage = { onNavigateToMessages(); viewModel.dismissMatchPopup() })
            }
        }

        if (isPaywallDialogOpen) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { isPaywallDialogOpen = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF111622))
                        .border(1.dp, AppColors.AccentOrange.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unlock Premium",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Undo your last swipe and see who liked you with our Premium plan!",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.togglePremiumStatus()
                            isPaywallDialogOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go Premium (Debug)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { isPaywallDialogOpen = false }) {
                        Text("Maybe Later", color = Color.LightGray)
                    }
                }
            }
        }
    }
}

// ==================================================================================
// 🌟 POPUP MODAL COMPOSE LAYOUT COMPONENT WITH DUMMY PAYWALL MOCKING
// ===================================================================================
@Composable
fun LikesYouPopupOverlay(
    players: List<Player>,
    isPremiumUser: Boolean,
    cardGradient: Brush,
    onDismiss: () -> Unit,
    onSelectPlayer: (Player) -> Unit,
    onUpgradeClick: () -> Unit
) {
    // 🌟 FIX: If a free user has 0 database likes, generate a premium blurred
    // teaser wall of fake players so they see an alluring pool of hidden matches!
    val displayPlayers = remember(players, isPremiumUser) {
        if (!isPremiumUser && players.isEmpty()) {
            listOf(
                Player("mock1", "Hidden Spin Master", "Pro", age = 24, elo = 1820, distanceKm = 4, hasSwipedMeRight = true, isPremium = false),
                Player("mock2", "Lefty Attacker", "Intermediate", age = 22, elo = 1410, distanceKm = 7, hasSwipedMeRight = true, isPremium = false),
                Player("mock3", "Chopper Fanatic", "Advanced", age = 29, elo = 1650, distanceKm = 11, hasSwipedMeRight = true, isPremium = false),
                Player("mock4", "Topspin Driver", "Intermediate", age = 26, elo = 1380, distanceKm = 3, hasSwipedMeRight = true, isPremium = false)
            )
        } else {
            players
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF111622))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "People Who Liked You",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (displayPlayers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No likes yet! Keep swiping.", color = Color.Gray, fontSize = 16.sp)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayPlayers.size) { index ->
                        val player = displayPlayers[index]

                        Box(
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.SurfaceDark)
                                .clickable(enabled = isPremiumUser) { onSelectPlayer(player) }
                        ) {
                            MatchCardContent(player = player, backgroundBrush = cardGradient)
                        }
                    }
                }

                // PAYWALL BLOCKER CONTENT: Overlays locked cells cleanly
                if (!isPremiumUser) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1C2232))
                                .border(1.dp, AppColors.AccentOrange.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                .padding(24.dp)
                        ) {
                            Text(text = "See Who Likes You!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Unlock names, metrics and match instantly with premium features.",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onUpgradeClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Go Premium", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================================================================================
// BASE SWIPE LAYER AND ACCENT COMPONENTS (Maintained for structural parity)
// ===================================================================================
@Composable
fun SwipeableMatchCard(
    player: Player,
    backgroundBrush: Brush,
    offsetX: Animatable<Float, *>,
    offsetY: Animatable<Float, *>,
    componentWidthPx: Float,
    onSwipeComplete: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val swipeThresholdPx = componentWidthPx / 3f
    val rotation = (offsetX.value / componentWidthPx) * 30f
    val alpha = 1f - (abs(offsetX.value) / componentWidthPx)

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = rotation
                this.alpha = alpha.coerceIn(0f, 1f)
            }
            .pointerInput(player.id) {
                detectDragGestures(
                    onDragEnd = {
                        if (abs(offsetX.value) > swipeThresholdPx) {
                            onSwipeComplete(offsetX.value > 0)
                        } else {
                            coroutineScope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                            coroutineScope.launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                )
            }
    ) {
        MatchCardContent(player, backgroundBrush)
    }
}

@Composable
fun MatchCard(player: Player, backgroundBrush: Brush, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        MatchCardContent(player, backgroundBrush)
    }
}

@Composable
fun MatchCardContent(player: Player, backgroundBrush: Brush) {
    val hasImage = !player.imageUrl.isNullOrBlank()
    // Forces blurring logic across BOTH regular card stacks and popup cells dynamically
    val shouldBlurCard = player.hasSwipedMeRight && !player.isPremium

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .then(if (hasImage) Modifier else Modifier.background(backgroundBrush))
            .then(if (shouldBlurCard) Modifier.blur(22.dp) else Modifier)
    ) {
        if (hasImage) {
            AsyncImage(model = player.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.8f)))))
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!hasImage) {
                    Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (shouldBlurCard) "Someone Special!" else "${player.username}, ",
                        color = Color.White,
                        fontSize = if (shouldBlurCard) 22.sp else 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!shouldBlurCard) {
                        Text(text = "${player.age}", color = Color.White.copy(alpha = 0.8f), fontSize = 24.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (shouldBlurCard) "Match instantly with Premium!" else "${player.distanceKm} km away",
                        color = if (shouldBlurCard) AppColors.AccentOrange else Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = if (shouldBlurCard) FontWeight.Bold else FontWeight.Medium
                    )
                }

                if (!shouldBlurCard) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TagChip(player.skillLevel)
                        if (player.badgeMetrics != null) {
                            val allBadges = mapMetricsToBadges(player.badgeMetrics)
                            val completedBadges = allBadges.filter { it.currentLevel > 0 }.sortedByDescending { it.currentLevel }
                            if (completedBadges.isNotEmpty()) {
                                Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.3f)))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { completedBadges.take(4).forEach { badge -> MiniBadge(badge) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagChip(text: String) {
    Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ActionButtonsRow(onLike: () -> Unit, onPass: () -> Unit, canUndo: Boolean, onUndo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (canUndo) AppColors.ButtonBackground else AppColors.ButtonBackground.copy(alpha = 0.4f)).border(1.dp, if (canUndo) Color.Yellow.copy(alpha = 0.6f) else Color.Transparent, CircleShape).clickable(enabled = canUndo) { onUndo() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Refresh, contentDescription = "Undo", tint = if (canUndo) Color.Yellow else Color.Gray, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(20.dp))
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(AppColors.ButtonBackground).border(2.dp, AppColors.ErrorText.copy(alpha = 0.5f), CircleShape).clickable { onPass() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, contentDescription = "Pass", tint = AppColors.ErrorText, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(20.dp))
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(AppColors.ButtonBackground).border(2.dp, AppColors.SuccessText.copy(alpha = 0.5f), CircleShape).clickable { onLike() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = "Like", tint = AppColors.SuccessText, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun MatchCelebrationOverlay(player: Player, onKeepSwiping: () -> Unit, onSendMessage: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xE6151C2C)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "IT'S A MATCH!", color = AppColors.AccentOrange, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = AppColors.AccentOrange, blurRadius = 20f)))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "You and ${player.username} liked each other.", color = AppColors.TextPrimary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.Gray).border(3.dp, AppColors.AccentOrange, CircleShape))
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(AppColors.TextPrimary.copy(alpha=0.5f)).border(3.dp, AppColors.AccentOrange, CircleShape))
            }
            Spacer(modifier = Modifier.height(48.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(AppColors.AccentOrange).clickable { onSendMessage() }.padding(horizontal = 48.dp, vertical = 16.dp)) { Text("SEND MESSAGE", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).border(2.dp, AppColors.TextPrimary, RoundedCornerShape(24.dp)).clickable { onKeepSwiping() }.padding(horizontal = 48.dp, vertical = 16.dp)) { Text("KEEP SWIPING", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) }
        }
    }
}