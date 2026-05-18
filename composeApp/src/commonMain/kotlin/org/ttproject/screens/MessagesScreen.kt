package org.ttproject.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.AppColors
import org.ttproject.components.MobileTopBar
import org.ttproject.components.PushNotificationManager
import org.ttproject.data.ChatThreadDto
import org.ttproject.data.TokenStorage
import org.ttproject.isIosPlatform
import org.ttproject.util.ClearChatNotificationEffect
import org.ttproject.util.formatMessageTime
import org.ttproject.viewmodel.ChatViewModel
import org.ttproject.viewmodel.MessagesViewModel
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.SheetState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import org.jetbrains.compose.resources.painterResource
import org.ttproject.components.AudioPlayer
import org.ttproject.components.VideoPlayer
import org.ttproject.components.rememberAudioPlayer
import org.ttproject.components.rememberCameraLauncher
import org.ttproject.components.rememberVideoLauncher
import org.ttproject.components.rememberVoiceRecorder
import org.ttproject.data.ReactionDto
import ttproject.composeapp.generated.resources.Res
import ttproject.composeapp.generated.resources.camera
import ttproject.composeapp.generated.resources.image
import ttproject.composeapp.generated.resources.mic
import ttproject.composeapp.generated.resources.video
import kotlin.math.abs

data class ChatThread(
    val id: String,
    val otherUserName: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int,
    val isOnline: Boolean = false
)

data class ReactionMenuData(
    val messageId: String,
    val isMe: Boolean,
    val bounds: Rect,
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
    val initialTouch: Offset,
    val reactionBounds: Rect?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = koinViewModel(),
    playAnimation: Boolean = true,
    bottomNavPadding: Dp,
    onNavigateToChat: (String, String, String?, String) -> Unit
) {
    val chatThreads by viewModel.filteredThreads.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLoading by viewModel.isLoading.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()

    PushNotificationManager { fcmToken ->
        viewModel.savePushToken(fcmToken)
    }

    val listVisibleState = remember(playAnimation) {
        MutableTransitionState(!playAnimation).apply { targetState = true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomNavPadding + 0.dp)
    ) {
        MobileTopBar(
            showSearch = true,
            onSearchClick = { isSearchExpanded = true }
        )

        if (isLoading && chatThreads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.AccentOrange)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 0.dp, bottom = 10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // --- ALWAYS RENDER THE SEARCH BAR ---
                item {
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LaunchedEffect(Unit) {
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    placeholder = {
                                        Text(
                                            "Search username...",
                                            color = AppColors.TextGray
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AppColors.AccentOrange,
                                        unfocusedBorderColor = AppColors.TextGray.copy(alpha = 0.5f),
                                        focusedTextColor = AppColors.TextPrimary,
                                        unfocusedTextColor = AppColors.TextPrimary
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            isSearchExpanded = false
                                            viewModel.updateSearchQuery("")
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Search",
                                                tint = AppColors.TextPrimary
                                            )
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // --- CONDITIONALLY RENDER THE LIST OR EMPTY STATES ---
                if (chatThreads.isEmpty()) {
                    item {
                        if (searchQuery.isNotBlank()) {
                            EmptySearchState(searchQuery)
                        } else {
                            EmptyMessagesState()
                        }
                    }
                } else {
                    itemsIndexed(chatThreads, key = { _, thread -> thread.id }) { index, thread ->
                        Column {
                            ChatListItem(
                                thread = thread,
                                onClick = {
                                    keyboardController?.hide()
                                    isSearchExpanded = false
                                    viewModel.updateSearchQuery("")
                                    onNavigateToChat(
                                        thread.id,
                                        thread.otherUserName,
                                        thread.otherUserImageUrl,
                                        thread.theme
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatViewModel = koinViewModel<ChatViewModel>(),
    chatId: String,
    otherUsername: String,
    otherUserImageUrl: String?,
    initialThemeName: String,
    bottomNavPadding: Dp,
    onBack: () -> Unit
) {
    LaunchedEffect(otherUsername) {
        viewModel.fetchOtherUserProfile(otherUsername)
    }

    val otherUserProfile by viewModel.otherUserProfile.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var forceShowIcons by remember { mutableStateOf(false) }

    LaunchedEffect(messageText) {

        if (messageText.text.isBlank()) {
            forceShowIcons = false
        }
    }

    var replyingToMessageId by remember { mutableStateOf<String?>(null) }
    val replyingToMessage = remember(replyingToMessageId, messages) {
        messages.find { it.id == replyingToMessageId }
    }

    var isUserProfileSheetOpen by remember { mutableStateOf(false) }
    val userProfileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val chatThemes = org.ttproject.ChatThemeManager.themes

    var currentTheme by remember(initialThemeName) {
        mutableStateOf(chatThemes.find { it.name == initialThemeName } ?: chatThemes[0])
    }

    var isThemeSheetOpen by remember { mutableStateOf(false) }
    val themeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedReactionMessageId by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val tokenStorage: TokenStorage = koinInject()

    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(replyingToMessageId) {
        if (replyingToMessageId != null) {
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val scope = rememberCoroutineScope()
    val mediaLauncher = rememberFilePickerLauncher(
        type = PickerType.ImageAndVideo,
        mode = PickerMode.Multiple(maxItems = 5),
        title = "Select Media"
    ) { files ->
        if (files != null && files.isNotEmpty()) {
            scope.launch {
                val byteArrays = files.mapNotNull { it.readBytes() }
                if (byteArrays.isNotEmpty()) {
                    viewModel.sendImagesMessage(chatId, byteArrays, replyingToMessageId)
                    replyingToMessageId = null
                }
            }
        }
    }

    val videoLauncher = rememberVideoLauncher { videoBytes ->
        if (videoBytes != null) {
            viewModel.sendVideoMessage(chatId, videoBytes, replyingToMessageId)
            replyingToMessageId = null
        }
    }

    val cameraLauncher = rememberCameraLauncher { imageBytes ->
        if (imageBytes != null) {
            viewModel.sendImagesMessage(chatId, listOf(imageBytes), replyingToMessageId)
            replyingToMessageId = null
        }
    }

    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var fullScreenImages by remember { mutableStateOf<List<String>?>(null) }
    var fullScreenInitialPage by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    ClearChatNotificationEffect(chatId = chatId)
    LaunchedEffect(chatId) { viewModel.markMessagesAsRead() }

    var previousMessageCount by remember { mutableStateOf(messages.size) }
    LaunchedEffect(messages.size) {
        if (messages.size > previousMessageCount) {
            listState.animateScrollToItem(0)
        }
        previousMessageCount = messages.size
    }

    val emojis = remember { listOf("❤️", "😂", "😮", "😢", "🏓", "👍") }
    var reactionMenuData by remember { mutableStateOf<ReactionMenuData?>(null) }
    var reactionSheetMessageId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var activeReactionDragPosition by remember { mutableStateOf<Offset?>(null) }
    var hoveredReactionIndex by remember { mutableStateOf(-1) }

    val haptic = LocalHapticFeedback.current
    var previousHoveredIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(hoveredReactionIndex) {
        if (hoveredReactionIndex != -1 && hoveredReactionIndex != previousHoveredIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        previousHoveredIndex = hoveredReactionIndex
    }

    // Voice Recording State
    val voiceRecorder = rememberVoiceRecorder(onPermissionDenied = { /* Show Toast */ })
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingDuration = 0L
            while (true) {
                kotlinx.coroutines.delay(1000)
                recordingDuration++
            }
        }
    }

    val audioPlayer = rememberAudioPlayer()
    var currentlyPlayingUrl by remember { mutableStateOf<String?>(null) }

    Box(
        // 👇 FIX 1: Removed windowInsetsPadding so the theme background goes edge-to-edge
        modifier = Modifier.fillMaxSize()
    ) {
        // Render Image or Gradient Background spanning full screen
        if (currentTheme.backgroundImage != null) {
            Image(
                painter = painterResource(currentTheme.backgroundImage as org.jetbrains.compose.resources.DrawableResource),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(currentTheme.backgroundBrush))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // 👇 THE FIX: Unites Keyboard (IME) and System Navigation Bars (Buttons or Gesture bars)
                // This automatically adapts to iOS/Android and different device settings!
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // --- ANCHORED TOP BAR ---
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isUserProfileSheetOpen = true }
                            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!otherUserImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = otherUserImageUrl, contentDescription = "Profile picture",
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(getInitials(otherUsername), color = AppColors.AccentOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(otherUsername, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            if (isIosPlatform()) Icons.Filled.ArrowBackIosNew else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = currentTheme.myBubbleColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isThemeSheetOpen = true }) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Change Theme",
                            tint = currentTheme.myBubbleColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            HorizontalDivider(color = AppColors.TextGray.copy(alpha = 0.2f))

            val knownMessageIds = remember { mutableSetOf<String>() }
            var isInitialBatchProcessed by remember { mutableStateOf(false) }

            LaunchedEffect(messages) {
                knownMessageIds.addAll(messages.map { it.id })
                isInitialBatchProcessed = true
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    reverseLayout = true
                ) {
                    val currentUserId = tokenStorage.getUserId() ?: ""
                    val displayMessages = messages.reversed()

                    itemsIndexed(displayMessages, key = { _, msg -> msg.id }) { index, msg ->
                        val isMe = msg.senderId == currentUserId
                        val isSelected = selectedMessageId == msg.id
                        val olderMessage = displayMessages.getOrNull(index + 1)
                        val newerMessage = displayMessages.getOrNull(index - 1)

                        val showTimeHeader = olderMessage == null || isTimeGapGreater(
                            olderMessage.createdAt,
                            msg.createdAt,
                            30
                        )
                        val newerShowsHeader = newerMessage != null && isTimeGapGreater(
                            msg.createdAt,
                            newerMessage.createdAt,
                            30
                        )

                        val visuallyConnectToOlder =
                            olderMessage?.senderId == msg.senderId && !showTimeHeader
                        val visuallyConnectToNewer =
                            newerMessage?.senderId == msg.senderId && !newerShowsHeader

                        val playAnimation = remember(msg.id) {
                            if (!isInitialBatchProcessed) false else !knownMessageIds.contains(msg.id) && index < 5
                        }

                        val repliedMessage =
                            msg.replyToMessageId?.let { replyId -> messages.find { it.id == replyId } }
                        val repliedText = repliedMessage?.content
                        val repliedSender =
                            if (repliedMessage?.senderId == currentUserId) "You" else otherUsername

                        Box(modifier = Modifier.zIndex(displayMessages.size - index.toFloat())) {
                            AnimatedMessageBubble(
                                text = msg.content,
                                isMe = isMe,
                                time = msg.createdAt,
                                playAnimation = playAnimation,
                                showTimeHeader = showTimeHeader,
                                isOlderSame = visuallyConnectToOlder,
                                isNewerSame = visuallyConnectToNewer,
                                isSelected = isSelected,
                                repliedText = repliedText,
                                repliedSender = repliedSender,
                                reactions = msg.reactions,
                                myBubbleColor = currentTheme.myBubbleColor,
                                otherBubbleColor = currentTheme.otherBubbleColor,
                                isHighlighted = msg.id == highlightedMessageId,
                                onQuoteClick = {
                                    msg.replyToMessageId?.let { targetId ->
                                        val targetIndex = displayMessages.indexOfFirst { it.id == targetId }

                                        if (targetIndex != -1) {
                                            coroutineScope.launch {
                                                val viewportHeight = listState.layoutInfo.viewportSize.height
                                                listState.animateScrollToItem(
                                                    index = targetIndex,
                                                    scrollOffset = -(viewportHeight / 4)
                                                )
                                                highlightedMessageId = targetId
                                                kotlinx.coroutines.delay(1000)
                                                if (highlightedMessageId == targetId) highlightedMessageId = null
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    selectedMessageId = if (isSelected) null else msg.id
                                },
                                onImageClick = { index, urls ->
                                    fullScreenImages = urls
                                    fullScreenInitialPage = index
                                },
                                onReactionClick = { reactionSheetMessageId = msg.id },
                                onLongPress = { bounds, topStart, topEnd, bottomStart, bottomEnd, initialTouch, reactionBounds ->
                                    reactionMenuData = ReactionMenuData(
                                        msg.id,
                                        isMe,
                                        bounds,
                                        topStart,
                                        topEnd,
                                        bottomStart,
                                        bottomEnd,
                                        initialTouch,
                                        reactionBounds
                                    )
                                },
                                onLongPressDrag = { globalPos ->
                                    activeReactionDragPosition = globalPos
                                },
                                onLongPressEnd = { hasDragged ->
                                    if (hasDragged) {
                                        if (reactionMenuData != null && hoveredReactionIndex != -1) {
                                            val msgId = reactionMenuData!!.messageId
                                            val selectedEmoji = emojis[hoveredReactionIndex]
                                            val targetMsg = messages.find { it.id == msgId }
                                            val currentUserId = tokenStorage.getUserId() ?: ""

                                            val hasReacted =
                                                targetMsg?.reactions?.any { it.userId == currentUserId && it.emoji == selectedEmoji } == true

                                            if (hasReacted) {
                                                viewModel.removeReaction(msgId)
                                            } else {
                                                viewModel.sendReaction(msgId, selectedEmoji)
                                            }
                                            reactionMenuData = null
                                        }
                                        activeReactionDragPosition = null
                                        hoveredReactionIndex = -1
                                    } else {
                                        activeReactionDragPosition = null
                                    }
                                },
                                currentlyPlayingUrl = currentlyPlayingUrl,
                                isAudioPlaying = audioPlayer.isPlaying,
                                audioPlayer = audioPlayer,
                                onVoiceClick = { voiceUrl ->
                                    if (currentlyPlayingUrl == voiceUrl) {
                                        if (audioPlayer.isPlaying) {
                                            audioPlayer.pause()
                                        } else {
                                            audioPlayer.resume()
                                        }
                                    } else {
                                        audioPlayer.stop()
                                        audioPlayer.play(voiceUrl)
                                        currentlyPlayingUrl = voiceUrl
                                    }
                                },
                                onSwipeToReply = { replyingToMessageId = msg.id }
                            )
                        }
                    }
                }

                val showScrollToBottom by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 300
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp, end = 12.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        enter = fadeIn() + androidx.compose.animation.scaleIn(),
                        exit = fadeOut() + androidx.compose.animation.scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(currentTheme.otherBubbleColor)
                                .clickable {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to newest",
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // --- THE KEYBOARD-AWARE INPUT AREA ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 👇 FIX 3: Exactly 10dp of spacing from the bottom nav bar
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (org.ttproject.isDark) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.05f))
                    .border(1.dp, currentTheme.myBubbleColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .animateContentSize()
            ) {
                AnimatedVisibility(
                    visible = replyingToMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    if (replyingToMessage != null) {
                        ReplyPreview(
                            messageContent = replyingToMessage.content,
                            themeColor = currentTheme.myBubbleColor,
                            onCancel = { replyingToMessageId = null }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (isRecordingVoice) {
                        IconButton(
                            onClick = { isRecordingVoice = false; voiceRecorder.cancelRecording() },
                            modifier = Modifier.size(36.dp)
                        ) { Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red, modifier = Modifier.size(20.dp)) }

                        Row(
                            modifier = Modifier.weight(1f).height(38.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 1f, targetValue = 0.2f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse)
                            )
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red.copy(alpha = alpha)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Recording...", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            val minutes = recordingDuration / 60
                            val seconds = recordingDuration % 60
                            Text(text = "${minutes}:${seconds.toString().padStart(2, '0')}", color = currentTheme.myBubbleColor, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(currentTheme.myBubbleColor)
                                .clickable {
                                    isRecordingVoice = false
                                    val audioBytes = voiceRecorder.stopRecording()
                                    if (audioBytes != null && recordingDuration > 0) {
                                        viewModel.sendVoiceMessage(chatId, audioBytes, replyingToMessageId)
                                        replyingToMessageId = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Voice",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp).offset(x = 2.dp)
                            )
                        }
                    } else {
                        val showMediaIcons = messageText.text.isBlank() || forceShowIcons

                        androidx.compose.animation.AnimatedVisibility(
                            visible = !showMediaIcons,
                            enter = androidx.compose.animation.expandHorizontally() + fadeIn(),
                            exit = androidx.compose.animation.shrinkHorizontally() + fadeOut()
                        ) {
                            IconButton(onClick = { forceShowIcons = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Expand Media", tint = currentTheme.myBubbleColor, modifier = Modifier.size(18.dp))
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showMediaIcons,
                            enter = androidx.compose.animation.expandHorizontally() + fadeIn(),
                            exit = androidx.compose.animation.shrinkHorizontally() + fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(38.dp)
                            ) {
                                IconButton(onClick = { cameraLauncher.launch() }, modifier = Modifier.size(36.dp)) {
                                    Icon(painterResource(Res.drawable.camera), contentDescription = "Camera", tint = currentTheme.myBubbleColor, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { videoLauncher.launch() }, modifier = Modifier.size(36.dp)) {
                                    Icon(painterResource(Res.drawable.video), contentDescription = "Video", tint = currentTheme.myBubbleColor, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { mediaLauncher.launch() }, modifier = Modifier.size(36.dp)) {
                                    Icon(painterResource(Res.drawable.image), contentDescription = "Gallery", tint = currentTheme.myBubbleColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        BasicTextField(
                            value = messageText,
                            onValueChange = { messageText = it; forceShowIcons = false },
                            maxLines = if (showMediaIcons) 1 else 6,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(inputFocusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        forceShowIcons = false
                                        messageText = messageText.copy(selection = TextRange(messageText.text.length))
                                    } else {
                                        forceShowIcons = true
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 16.sp),
                            cursorBrush = SolidColor(currentTheme.myBubbleColor),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (messageText.text.isEmpty()) {
                                        Text("Message", color = AppColors.TextGray, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        val hasText = messageText.text.isNotBlank()
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(currentTheme.myBubbleColor)
                                .clickable {
                                    if (hasText) {
                                        viewModel.sendMessage(messageText.text, replyingToMessageId)
                                        messageText = TextFieldValue("")
                                        replyingToMessageId = null
                                    } else {
                                        voiceRecorder.startRecording { isRecordingVoice = true }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasText) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp).offset(x = 2.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(Res.drawable.mic),
                                    contentDescription = "Record Voice",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isThemeSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isThemeSheetOpen = false },
                sheetState = themeSheetState,
                containerColor = AppColors.Background
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text(
                        "Chat Theme",
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(color = AppColors.TextGray.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(chatThemes) { theme ->
                            val isSelected = currentTheme.name == theme.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) AppColors.SurfaceDark else Color.Transparent)
                                    .clickable {
                                        currentTheme = theme
                                        viewModel.updateChatTheme(chatId, theme.name)
                                        coroutineScope.launch { themeSheetState.hide(); isThemeSheetOpen = false }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(theme.backgroundBrush)
                                        .border(2.dp, if (isSelected) theme.myBubbleColor else Color.Transparent, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = theme.name,
                                    color = AppColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = theme.myBubbleColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isUserProfileSheetOpen) {
            OtherUserProfileSheet(
                username = otherUsername,
                imageUrl = otherUserProfile?.imageUrl ?: otherUserImageUrl,
                bio = otherUserProfile?.bio,
                age = otherUserProfile?.age,
                skillLevel = otherUserProfile?.skillLevel,
                blade = otherUserProfile?.blade,
                rubberFh = otherUserProfile?.rubberFh,
                rubberBh = otherUserProfile?.rubberBh,
                sheetState = userProfileSheetState,
                onDismiss = { isUserProfileSheetOpen = false }
            )
        }

        if (reactionSheetMessageId != null) {
            ModalBottomSheet(
                onDismissRequest = { reactionSheetMessageId = null },
                sheetState = sheetState,
                containerColor = AppColors.Background
            ) {
                val targetMessage = messages.find { it.id == reactionSheetMessageId }
                if (targetMessage != null && targetMessage.reactions.isNotEmpty()) {
                    val currentUserId = tokenStorage.getUserId() ?: ""
                    val realReactionsList = targetMessage.reactions.map { dto ->
                        val isMyReaction = dto.userId == currentUserId
                        ReactionDetail(
                            userId = dto.userId,
                            username = if (isMyReaction) "You" else otherUsername,
                            profileImageUrl = if (isMyReaction) null else otherUserImageUrl,
                            emoji = dto.emoji,
                            isMe = isMyReaction
                        )
                    }

                    ReactionsBottomSheet(
                        reactions = realReactionsList,
                        onRemoveReaction = { reaction ->
                            viewModel.removeReaction(targetMessage.id)
                            coroutineScope.launch {
                                sheetState.hide()
                                reactionSheetMessageId = null
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = reactionMenuData != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.zIndex(100f)
        ) {
            reactionMenuData?.let { state ->
                val density = LocalDensity.current

                var overlayBounds by remember { mutableStateOf(Offset.Zero) }
                var overlaySize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            overlayBounds = coordinates.boundsInRoot().topLeft
                            overlaySize = coordinates.size
                        }
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { reactionMenuData = null })
                            }
                    ) {
                        val localBounds = state.bounds.translate(-overlayBounds)

                        val cornerRadiusPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = localBounds,
                                    topLeft = CornerRadius(state.topStart.toPx()),
                                    topRight = CornerRadius(state.topEnd.toPx()),
                                    bottomRight = CornerRadius(state.bottomEnd.toPx()),
                                    bottomLeft = CornerRadius(state.bottomStart.toPx())
                                )
                            )
                            state.reactionBounds?.let { rBounds ->
                                val localReactionBounds = rBounds.translate(-overlayBounds)
                                val badgeRadius = with(density) { 14.dp.toPx() }
                                addRoundRect(
                                    RoundRect(
                                        rect = localReactionBounds,
                                        cornerRadius = CornerRadius(badgeRadius)
                                    )
                                )
                            }
                        }

                        clipPath(cornerRadiusPath, clipOp = ClipOp.Difference) {
                            drawRect(Color.Black.copy(alpha = 0.65f))
                        }
                    }

                    val menuWidthDp = 270.dp
                    val menuHeightDp = 56.dp
                    val menuWidthPx = with(density) { menuWidthDp.toPx() }
                    val menuHeightPx = with(density) { menuHeightDp.toPx() }

                    val screenWidthPx = overlaySize.width.toFloat()
                    val localBounds = state.bounds.translate(-overlayBounds)

                    val safeMarginPx = with(density) { 16.dp.toPx() }
                    val minMenuX = safeMarginPx
                    val maxMenuX = maxOf(minMenuX, screenWidthPx - menuWidthPx - safeMarginPx)

                    val idealX = if (state.isMe) localBounds.right - menuWidthPx else localBounds.left
                    val menuX = idealX.coerceIn(minMenuX, maxMenuX)

                    val isSpaceAbove = localBounds.top > menuHeightPx + 50f
                    val menuY = if (isSpaceAbove) localBounds.top - menuHeightPx - 20f else localBounds.bottom + 20f

                    val transformOrigin = if (state.isMe) {
                        TransformOrigin(1f, if (isSpaceAbove) 1f else 0f)
                    } else {
                        TransformOrigin(0f, if (isSpaceAbove) 1f else 0f)
                    }

                    LaunchedEffect(activeReactionDragPosition, menuX, menuY) {
                        if (activeReactionDragPosition != null && reactionMenuData != null) {
                            val dragPos = activeReactionDragPosition!!
                            val initialPos = reactionMenuData!!.initialTouch

                            val isSpaceAbove = localBounds.top > menuHeightPx + 50f
                            val dragDistanceY = dragPos.y - initialPos.y
                            val swipeThreshold = with(density) { 10.dp.toPx() }

                            val hasSwipedTowardsMenu = if (isSpaceAbove) {
                                dragDistanceY < -swipeThreshold
                            } else {
                                dragDistanceY > swipeThreshold
                            }

                            if (hasSwipedTowardsMenu) {
                                val localX = dragPos.x - menuX
                                if (localX >= -50f && localX <= menuWidthPx + 50f) {
                                    val itemWidth = menuWidthPx / emojis.size
                                    val clampedX = localX.coerceIn(0f, menuWidthPx - 1f)
                                    hoveredReactionIndex = (clampedX / itemWidth).toInt()
                                } else {
                                    hoveredReactionIndex = -1
                                }
                            } else {
                                hoveredReactionIndex = -1
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(menuX.toInt(), menuY.toInt()) }
                            .animateEnterExit(
                                enter = androidx.compose.animation.scaleIn(
                                    transformOrigin = transformOrigin,
                                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy)
                                ),
                                exit = androidx.compose.animation.scaleOut(transformOrigin = transformOrigin)
                            )
                            .width(menuWidthDp)
                            .height(menuHeightDp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    val itemWidth = menuWidthPx / emojis.size
                                    hoveredReactionIndex = (down.position.x / itemWidth).toInt().coerceIn(0, emojis.size - 1)

                                    var isTracking = true
                                    while (isTracking) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.first()

                                        if (change.pressed) {
                                            val localY = change.position.y
                                            if (localY > -100f && localY < menuHeightPx + 100f) {
                                                hoveredReactionIndex = (change.position.x / itemWidth).toInt().coerceIn(0, emojis.size - 1)
                                            } else {
                                                hoveredReactionIndex = -1
                                            }
                                        } else {
                                            isTracking = false
                                            if (hoveredReactionIndex != -1 && hoveredReactionIndex in emojis.indices) {
                                                val selectedEmoji = emojis[hoveredReactionIndex]
                                                val targetMsg = messages.find { it.id == state.messageId }
                                                val currentUserId = tokenStorage.getUserId() ?: ""
                                                val hasReacted = targetMsg?.reactions?.any { it.userId == currentUserId && it.emoji == selectedEmoji } == true

                                                if (hasReacted) {
                                                    viewModel.removeReaction(state.messageId)
                                                } else {
                                                    viewModel.sendReaction(state.messageId, selectedEmoji)
                                                }
                                                reactionMenuData = null
                                            }
                                            hoveredReactionIndex = -1
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppColors.SurfaceDark, RoundedCornerShape(32.dp))
                        )
                        val targetMessage = messages.find { it.id == state.messageId }
                        val currentUserId = tokenStorage.getUserId() ?: ""

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            emojis.forEachIndexed { index, emoji ->
                                val isHovered = hoveredReactionIndex == index

                                val scale by animateFloatAsState(
                                    targetValue = if (isHovered) 1.6f else 1f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    ),
                                    label = "emojiScale"
                                )

                                val offsetY by animateFloatAsState(
                                    targetValue = if (isHovered) -15f else 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    ),
                                    label = "emojiOffset"
                                )

                                val hasReacted = targetMessage?.reactions?.any { it.userId == currentUserId && it.emoji == emoji } == true

                                Box(
                                    modifier = Modifier.fillMaxHeight().width(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 26.sp,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationY = offsetY
                                        }
                                    )

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 6.dp)
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (hasReacted) currentTheme.myBubbleColor else Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        var activeImages by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(fullScreenImages) {
            if (fullScreenImages != null) {
                activeImages = fullScreenImages!!
            }
        }

        if (fullScreenImages != null && activeImages.isNotEmpty()) {
            val pagerState = rememberPagerState(
                initialPage = fullScreenInitialPage,
                pageCount = { activeImages.size }
            )

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { fullScreenImages = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        var scale by remember { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }

                        val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            val maxPanX = (2000f * (scale - 1)) / 2
                            val maxPanY = (2000f * (scale - 1)) / 2
                            offset = Offset(
                                (offset.x + offsetChange.x).coerceIn(-maxPanX, maxPanX),
                                (offset.y + offsetChange.y).coerceIn(-maxPanY, maxPanY)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                                            else { scale = 2.5f }
                                        },
                                        onTap = { if (scale == 1f) fullScreenImages = null }
                                    )
                                }
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        do {
                                            val event = awaitPointerEvent()
                                            if (event.changes.size > 1 || scale > 1f) {
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                event.changes.forEach { it.consume() }

                                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                                val maxPanX = (size.width * (scale - 1)) / 2
                                                val maxPanY = (size.height * (scale - 1)) / 2

                                                offset = Offset(
                                                    (offset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                    (offset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val url = activeImages.getOrNull(page) ?: ""
                            val isVideo = url.contains(".mp4")

                            if (isVideo) {
                                VideoPlayer(
                                    modifier = Modifier.fillMaxSize(),
                                    url = url
                                )
                            } else {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Image $page",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { fullScreenImages = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    if (activeImages.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${activeImages.size}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedMessageBubble(
    text: String,
    isMe: Boolean,
    time: String,
    playAnimation: Boolean,
    showTimeHeader: Boolean,
    isOlderSame: Boolean,
    isNewerSame: Boolean,
    isSelected: Boolean,
    repliedText: String?,
    repliedSender: String?,
    reactions: List<ReactionDto>,
    myBubbleColor: Color,
    otherBubbleColor: Color,
    currentlyPlayingUrl: String?,   // 👈 NEW
    isAudioPlaying: Boolean,        // 👈 NEW
    audioPlayer: AudioPlayer,
    onVoiceClick: (String) -> Unit,
    isHighlighted: Boolean,
    onQuoteClick: () -> Unit,
    onClick: () -> Unit,
    onImageClick: (Int, List<String>) -> Unit,
    onReactionClick: () -> Unit,
    onLongPress: (Rect, Dp, Dp, Dp, Dp, Offset, Rect?) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: (Boolean) -> Unit,
    onSwipeToReply: () -> Unit
) {
    // 👇 2. Remove the 'hasAnimated' rememberSaveable entirely.
    // Instead, strictly obey the parent's command using Animatable.
    val alphaAnim = remember { androidx.compose.animation.core.Animatable(if (playAnimation) 0.01f else 1f) }
    val offsetAnim = remember { androidx.compose.animation.core.Animatable(if (playAnimation) 100f else 0f) }

    // 👇 1. ADD THE SCALE ANIMATABLE
    val scaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }

    // 👇 2. ADD THE POP EFFECT
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            // Quick swell up...
            scaleAnim.animateTo(1.05f, tween(150))
            // ...and bounce back down!
            scaleAnim.animateTo(1f, androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ))
        }
    }

    // 👇 1. ADD THE HIGHLIGHT ALPHA ANIMATABLE
    val highlightAlpha = remember { androidx.compose.animation.core.Animatable(0f) }

    // 👇 2. ADD THE BACKGROUND FLASH EFFECT
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            // Quick fade into a translucent highlight
            highlightAlpha.animateTo(0.25f, tween(200))
            // Hold it for a split second so the user's eye catches it after the scroll finishes
            kotlinx.coroutines.delay(250)
            // Smoothly fade back to transparent
            highlightAlpha.animateTo(0f, tween(600))
        }
    }

    LaunchedEffect(playAnimation) {
        if (playAnimation) {
            launch { alphaAnim.animateTo(1f, tween(250)) }
            launch { offsetAnim.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy)) }
        } else {
            // Instantly snap to visible if scrolling history
            launch { alphaAnim.snapTo(1f) }
            launch { offsetAnim.snapTo(0f) }
        }
    }

    val swipeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val isVoice = text.startsWith("[VOICE]")
    val voiceUrl = if (isVoice) text.substringAfter("[VOICE]") else ""
    val isActiveVoice = currentlyPlayingUrl == voiceUrl
    val isSeekingAllowed = isActiveVoice && isAudioPlaying
//    Column(
//        modifier = Modifier
//            .fillMaxWidth() // 👈 Ensure it takes the full width so the flash goes edge-to-edge!
//            .background(myBubbleColor.copy(alpha = highlightAlpha.value)) // 👈 Flashes the theme color
//            .graphicsLayer {
//                translationX = if (isMe) (offsetAnim.value / 2) else (-offsetAnim.value / 2)
//                translationY = offsetAnim.value
//                this.alpha = alphaAnim.value
//                // scaleX and scaleY removed!
//            }
//    ) {
//        // --- THE CENTERED TIMESTAMP ---
    Column(
        // 👇 THE FIX: Bypass the graphicsLayer cache bug by using physical layout modifiers!
        modifier = Modifier
            // 👇 1. Move BOTH the offset and alpha into the GPU layer!
            // Do NOT use Modifier.offset() here.
            .graphicsLayer {
                // 👇 3. Use the .value of the explicit animations
                translationX = if (isMe) (offsetAnim.value / 2) else (-offsetAnim.value / 2)
                translationY = offsetAnim.value
                this.alpha = alphaAnim.value
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
    ) {
        // --- THE CENTERED TIMESTAMP ---
        if (showTimeHeader) {
            Text(
                text = formatMessageTime(time),
                color = AppColors.TextGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp, bottom = 8.dp)
            )
        } else {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Text(
                    text = formatMessageTime(time),
                    color = AppColors.TextGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 👇 1. Wrap the ChatBubble in a Box to layer the Icon behind it
        Box(
            modifier = Modifier.fillMaxWidth(),
            // Align to the right for "Me", left for "Them"
            contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
        ) {

            // 👇 2. THE REPLY ICON (Reveals dynamically based on swipe distance)
            val iconAlpha = (abs(swipeOffset.value) / 120f).coerceIn(0f, 1f)

            if (iconAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .padding(
                            start = if (isMe) 16.dp else 0.dp,
                            end = if (isMe) 0.dp else 16.dp
                        )
                        .graphicsLayer {
                            this.alpha = iconAlpha
                            // Add a subtle pop-in scaling effect
                            val scale = 0.5f + (0.5f * iconAlpha)
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.SurfaceDark), // Matches the reaction menu style
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 👇 3. THE CHAT BUBBLE
            ChatBubble(
                text = text,
                isMe = isMe,
                isOlderSame = isOlderSame,
                isNewerSame = isNewerSame,
                isSelected = isSelected,
                repliedText = repliedText,
                repliedSender = repliedSender,
                reactions = reactions,
                myBubbleColor = myBubbleColor,
                otherBubbleColor = otherBubbleColor,
                onQuoteClick = onQuoteClick,
                onClick = onClick,
                onImageClick = onImageClick,
                onReactionClick = onReactionClick,
                onLongPress = onLongPress,
                onLongPressDrag = onLongPressDrag,
                onLongPressEnd = onLongPressEnd,
                currentlyPlayingUrl = currentlyPlayingUrl,
                isAudioPlaying = isAudioPlaying,
                audioPlayer = audioPlayer,
                onVoiceClick = onVoiceClick,
                modifier = Modifier
                    .graphicsLayer { translationX = swipeOffset.value }
                    .pointerInput(isSeekingAllowed) { // 👈 Re-bind when playing state changes
                        if (isSeekingAllowed) {
                            // When playing, we don't want to swipe.
                            // The internal ChatBubble will handle seeking.
                            return@pointerInput
                        }

                        // --- EXISTING SWIPE LOGIC ---
                        var hasTriggeredHaptic = false
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(swipeOffset.value) > 120f) onSwipeToReply()
                                coroutineScope.launch {
                                    swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                                hasTriggeredHaptic = false
                            },
                            onDragCancel = {
                                coroutineScope.launch { swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                hasTriggeredHaptic = false
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = if (isMe) {
                                    (swipeOffset.value + dragAmount).coerceIn(-200f, 0f)
                                } else {
                                    (swipeOffset.value + dragAmount).coerceIn(0f, 200f)
                                }
                                coroutineScope.launch { swipeOffset.snapTo(newOffset) }

                                if (abs(newOffset) > 120f && !hasTriggeredHaptic) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasTriggeredHaptic = true
                                }
                            }
                        )
                    }            )
        }
        Spacer(modifier = Modifier.height(if (isNewerSame) 4.dp else 16.dp))
    }
}

@Composable
fun ChatBubble(
    text: String,
    isMe: Boolean,
    isOlderSame: Boolean,
    isNewerSame: Boolean,
    isSelected: Boolean,
    // 👇 Accept the new parameters
    repliedText: String?,
    repliedSender: String?,
    reactions: List<ReactionDto>,
    myBubbleColor: Color,
    otherBubbleColor: Color,
    currentlyPlayingUrl: String?,      // 👈 NEW
    isAudioPlaying: Boolean,           // 👈 NEW
    audioPlayer: AudioPlayer,
    onVoiceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onQuoteClick: () -> Unit,
    onClick: () -> Unit,
    onImageClick: (Int, List<String>) -> Unit,
    onReactionClick: () -> Unit,
    onLongPress: (Rect, Dp, Dp, Dp, Dp, Offset, Rect?) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: (Boolean) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressDrag by rememberUpdatedState(onLongPressDrag)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnVoiceClick by rememberUpdatedState(onVoiceClick)

    val haptic = LocalHapticFeedback.current
    val topStart = if (!isMe && isOlderSame) 4.dp else 16.dp
    val bottomStart = if (!isMe && isNewerSame) 4.dp else 16.dp
    val topEnd = if (isMe && isOlderSame) 4.dp else 16.dp
    val bottomEnd = if (isMe && isNewerSame) 4.dp else 16.dp

    val baseColor = if (isMe) myBubbleColor else otherBubbleColor
    val targetColor = if (isSelected) {
        Color(
            red = baseColor.red * 0.85f, green = baseColor.green * 0.85f,
            blue = baseColor.blue * 0.85f, alpha = baseColor.alpha
        )
    } else baseColor

    val animatedBackgroundColor by androidx.compose.animation.animateColorAsState(targetColor)
    var bubbleBounds by remember { mutableStateOf(Rect.Zero) }
    var reactionBounds by remember { mutableStateOf<Rect?>(null) }
    val bubbleShape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.TopEnd else Alignment.TopStart
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Box(contentAlignment = Alignment.BottomEnd) {

                // --- 1. DEFINE MEDIA TYPES ---
                val isSingleImage = text.startsWith("[IMAGE]") && !text.startsWith("[IMAGES]")
                val isMultiImage = text.startsWith("[IMAGES]")
                val isVideo = text.startsWith("[VIDEO]")
                val isVoice = text.startsWith("[VOICE]")
                val voiceUrl = if (isVoice) text.substringAfter("[VOICE]") else ""

                // Images and Videos go edge-to-edge (No padding, No background)
                val isMediaNoPadding = isSingleImage || isMultiImage || isVideo
                val isAnyMedia = isMediaNoPadding || isVoice

                val videoThumbnailUrl = if (isVideo) text.substringAfter("[VIDEO]").split(",").firstOrNull() else null
                val imageUrls = remember(text) {
                    when {
                        isMultiImage -> text.substringAfter("[IMAGES]").split(",")
                        isSingleImage -> listOf(text.substringAfter("[IMAGE]"))
                        isVideo -> listOf(text.substringAfter("[VIDEO]").split(",").last())
                        else -> emptyList()
                    }
                }

                // --- 2. THE UNIFIED CONTAINER ---
                // This single Column now handles background, padding, bounds, AND gestures
                Column(
                    modifier = Modifier
                        .maxWidthPercent(0.80f) // 👈 KMP SAFE AND BLAZING FAST
                        .onGloballyPositioned { coordinates ->
                            bubbleBounds = coordinates.boundsInRoot()
                        }
                        .then(
                            // Apply background only to Text and Voice messages
                            if (isMediaNoPadding) Modifier.clip(bubbleShape)
                            else Modifier.background(animatedBackgroundColor, bubbleShape).clip(bubbleShape)
                        )
                        .pointerInput(Unit) {
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var isTap = false
                                var isLongPress = false
                                try {
                                    withTimeout(400L) {
                                        var current = down
                                        while (current.pressed) {
                                            val event = awaitPointerEvent()
                                            current = event.changes.first()
                                            val distance = (current.position - down.position).getDistance()
                                            if (distance > slop) return@withTimeout
                                        }
                                        current.consume()
                                        isTap = true
                                    }
                                } catch (e: androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException) {
                                    isLongPress = true
                                }

                                if (isTap) {
                                    if (isVoice) {
                                        currentOnVoiceClick(voiceUrl)
                                    } else if (isMediaNoPadding) {
                                        val x = down.position.x
                                        val y = down.position.y
                                        val w = size.width
                                        val h = size.height
                                        val index = when (imageUrls.size) {
                                            1 -> 0
                                            2 -> if (x < w / 2) 0 else 1
                                            3 -> if (y < h / 2) 0 else if (x < w / 2) 1 else 2
                                            else -> if (y < h / 2) (if (x < w / 2) 0 else 1) else (if (x < w / 2) 2 else 3)
                                        }
                                        currentOnImageClick(index, imageUrls)
                                    } else {
                                        currentOnClick()
                                    }
                                } else if (isLongPress) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val globalTouch = bubbleBounds.topLeft + down.position
                                    onLongPress(bubbleBounds, topStart, topEnd, bottomStart, bottomEnd, globalTouch, reactionBounds)
                                    var tracking = true
                                    var hasDragged = false
                                    while (tracking) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.first()
                                        if (change.pressed) {
                                            val distance = (change.position - down.position).getDistance()
                                            if (distance > slop) hasDragged = true
                                            onLongPressDrag(bubbleBounds.topLeft + change.position)
                                            change.consume()
                                        } else {
                                            tracking = false
                                            onLongPressEnd(hasDragged)
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                        .padding(if (isMediaNoPadding) 0.dp else 12.dp)
                ) {
                    // 👇 THE QUOTE PREVIEW UI
                    if (repliedText != null && repliedSender != null) {
                        val isDarkMode = org.ttproject.isDark
                        val isQuotedImage = repliedText.startsWith("[IMAGE")
                        val displayRepliedText = when {
                            repliedText.startsWith("[VOICE]") -> "🎤 Voice Message"
                            repliedText.startsWith("[VIDEO]") -> "🎥 Video"
                            repliedText.startsWith("[IMAGE") -> "📸 Photo"
                            else -> repliedText
                        }
                        val quoteBgColor = if (isAnyMedia) Color.Black.copy(alpha = 0.6f) else if (isDarkMode) Color.Black.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f)

                        Row(
                            modifier = Modifier
                                .then(if (isAnyMedia) Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp) else Modifier.padding(bottom = 6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(quoteBgColor)
                                .clickable { onQuoteClick() }
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(if (isMe) Color.White else myBubbleColor))
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(text = repliedSender, color = if (isMe) Color.White else myBubbleColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(text = displayRepliedText, color = if (isAnyMedia || isMe) Color.White.copy(alpha = 0.85f) else AppColors.TextPrimary.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // 👇 3. RENDER DYNAMIC COLLAGE OR TEXT
                    if (isVoice) {
                        // We removed the Box wrapper here so the Column itself defines the bounds
                        VoiceMessageContent(
                            voiceUrl = voiceUrl,
                            isMe = isMe,
                            player = audioPlayer,
                            activeUrl = currentlyPlayingUrl,
                            isAudioPlaying = isAudioPlaying,
                            themeColor = if (isMe) Color.White else myBubbleColor,
                            onPlayToggle = { url -> onVoiceClick(url) }
                        )
                    } else if (isAnyMedia) {
                        Box(modifier = Modifier.widthIn(max = 280.dp).clip(bubbleShape)) {
                            when (imageUrls.size) {
                                1 -> {
                                    MediaThumbnail(
                                        url = imageUrls[0],
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                                        isVideo = imageUrls[0].contains(".mp4"),
                                        videoThumbnailUrl = videoThumbnailUrl,
                                        bubbleShape = bubbleShape,
                                    )
                                }
                                2 -> {
                                    Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                        MediaThumbnail(
                                            url = imageUrls[0],
                                            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 2.dp),
                                            isVideo = imageUrls[0].contains(".mp4"),
                                            videoThumbnailUrl = videoThumbnailUrl,
                                            bubbleShape = bubbleShape,
                                        )
                                        MediaThumbnail(
                                            url = imageUrls[1],
                                            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp),
                                            isVideo = imageUrls[1].contains(".mp4"),
                                            videoThumbnailUrl = videoThumbnailUrl,
                                            bubbleShape = bubbleShape,
                                        )
                                    }
                                }
                                3 -> {
                                    Column(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                                        MediaThumbnail(
                                            url = imageUrls[0],
                                            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 2.dp),
                                            isVideo = imageUrls[0].contains(".mp4"),
                                            videoThumbnailUrl = videoThumbnailUrl,
                                            bubbleShape = bubbleShape,
                                        )
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp)) {
                                            MediaThumbnail(
                                                url = imageUrls[1],
                                                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 2.dp),
                                                isVideo = imageUrls[1].contains(".mp4"),
                                                videoThumbnailUrl = videoThumbnailUrl,
                                                bubbleShape = bubbleShape,
                                            )
                                            MediaThumbnail(
                                                url = imageUrls[2],
                                                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp),
                                                isVideo = imageUrls[2].contains(".mp4"),
                                                videoThumbnailUrl = videoThumbnailUrl,
                                                bubbleShape = bubbleShape,
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    // 4 or more images (2x2 grid)
                                    Column(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 2.dp)) {
                                            MediaThumbnail(
                                                url = imageUrls[0],
                                                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 2.dp),
                                                isVideo = imageUrls[0].contains(".mp4"),
                                                videoThumbnailUrl = videoThumbnailUrl,
                                                bubbleShape = bubbleShape,
                                            )
                                            MediaThumbnail(
                                                url = imageUrls[1],
                                                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp),
                                                isVideo = imageUrls[1].contains(".mp4"),
                                                videoThumbnailUrl = videoThumbnailUrl,
                                                bubbleShape = bubbleShape,
                                            )
                                        }
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp)) {
                                            MediaThumbnail(
                                                url = imageUrls[2],
                                                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 2.dp),
                                                isVideo = imageUrls[2].contains(".mp4"),
                                                videoThumbnailUrl = videoThumbnailUrl,
                                                bubbleShape = bubbleShape,
                                            )
                                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp)) {
                                                MediaThumbnail(
                                                    url = imageUrls[3],
                                                    modifier = Modifier.fillMaxSize(),
                                                    isVideo = imageUrls[3].contains(".mp4"),
                                                    videoThumbnailUrl = videoThumbnailUrl,
                                                    bubbleShape = bubbleShape,
                                                )
                                                // Show +X overlay if there are more than 4 images
                                                if (imageUrls.size > 4) {
                                                    // Note: We don't add a clickable modifier here because
                                                    // the MediaThumbnail underneath will catch the tap!
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.5f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "+${imageUrls.size - 4}",
                                                            color = Color.White,
                                                            fontSize = 24.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (isSelected) {
                                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
                            }
                        }
                    } else {
                        Text(text = text, color = if (isMe) Color.White else AppColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }

                // --- THE FLOATING REACTION BADGE ---
                if (reactions.isNotEmpty()) {
                    // 👇 1. Group the reactions by emoji!
                    // This creates a Map where the Key is the Emoji (String) and the Value is a List of ReactionDtos.
                    val groupedReactions = reactions.groupBy { it.emoji }

                    Box(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(0, 0) {
                                    placeable.placeRelative(
                                        x = -placeable.width + 6.dp.roundToPx(),
                                        y = -placeable.height / 2 + 4.dp.roundToPx()
                                    )
                                }
                            }
                            .zIndex(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.Background) // The cutout border
                                .padding(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        reactionBounds = coordinates.boundsInRoot()
                                    }
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppColors.SurfaceDark)
                                    .clickable { onReactionClick() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp), // Space between different emojis
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 👇 2. Loop through the grouped map instead of the raw list
                                groupedReactions.forEach { (emoji, reactionList) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp) // Space between emoji and number
                                    ) {
                                        Text(text = emoji, fontSize = 12.sp)

                                        // 👇 3. Only show the number if more than 1 person used this emoji!
                                        if (reactionList.size > 1) {
                                            Text(
                                                text = reactionList.size.toString(),
                                                color = AppColors.TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } // End of Bubble & Reaction Box

            // 👇 4. We add physical spacing HERE so the hanging reaction doesn't clip into the next message
            if (reactions.isNotEmpty()) { // ✅ NEW: Check the list size!
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun ChatListItem(thread: ChatThreadDto, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // --- AVATAR BOX ---
            Box(modifier = Modifier.size(52.dp)) {
                // The main circular container
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(AppColors.SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    // 👇 NEW: Check if there is an image URL
                    if (!thread.otherUserImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = thread.otherUserImageUrl,
                            contentDescription = "Profile picture of ${thread.otherUserName}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop // Keeps the image perfectly circular without squishing
                        )
                    } else {
                        // FALLBACK INITIALS
                        Text(
                            text = getInitials(thread.otherUserName),
                            color = AppColors.AccentOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                // Online Indicator (Stays exactly the same)
                if (thread.isOnline) {
                    Box(modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp).clip(CircleShape).background(Color(0xFF4CAF50)).padding(2.dp)) {
                        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF4CAF50)))
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // --- TEXT CONTENT ---
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(thread.otherUserName, color = AppColors.TextPrimary, fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageTime(thread.timestamp),
                        color = if (thread.unreadCount > 0) AppColors.AccentOrange else AppColors.TextGray,
                        fontSize = 12.sp,
                        fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val displayLastMessage = when {
                        thread.lastMessage.startsWith("[VOICE]") -> "🎤 Voice Message"
                        thread.lastMessage.startsWith("[VIDEO]") -> "🎥 Video"
                        thread.lastMessage.startsWith("[IMAGE") -> "📸 Photo"
                        else -> thread.lastMessage
                    }
                    Text(displayLastMessage, color = if (thread.unreadCount > 0) AppColors.TextPrimary else Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (thread.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(AppColors.AccentOrange), contentAlignment = Alignment.Center) {
                            Text(thread.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp)) // Push it down a bit from the search bar
        Text("🔍", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("No results found", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "We couldn't find any chats matching \"$query\".",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmptyMessagesState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("💬", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("No Messages Yet", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Swipe on players nearby or join a table to start a conversation.", color = Color.Gray, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ReplyPreview(
    messageContent: String,
    themeColor: Color, // 👈 Accept the theme color
    onCancel: () -> Unit
) {
    val isDarkMode = org.ttproject.isDark
    val bgColor = if (isDarkMode) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f)
    val displayMessage = when {
        messageContent.startsWith("[VOICE]") -> "🎤 Voice Message"
        messageContent.startsWith("[VIDEO]") -> "🎥 Video"
        messageContent.startsWith("[IMAGE") -> "📸 Photo"
        else -> messageContent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor) // 👈 Translucent to blend with gradient!
            .border(
                width = 1.dp,
                color = themeColor.copy(alpha = 0.3f), // 👈 Theme matched border
                shape = RoundedCornerShape(8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(themeColor)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text("Replying to", color = themeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) // 👈 Theme matched title
            Text(
                text = displayMessage,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel Reply", tint = AppColors.TextGray)
        }
    }
}

fun getInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+"))
    return if (parts.size >= 2) "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
    else if (name.isNotEmpty()) name.take(2).uppercase() else "?"
}

fun isTimeGapGreater(olderTime: String, newerTime: String, minutes: Int): Boolean {
    return try {
        // 👇 1. Use Instant.parse() instead of .toInstant()
        val olderInstant = Instant.parse(olderTime)
        val newerInstant = Instant.parse(newerTime)

        // 2. Calculate the exact time difference
        val timeDifference = newerInstant - olderInstant

        // 3. Check if it's strictly greater than 1 hour
        timeDifference > minutes.minutes

    } catch (e: Exception) {
        true
    }
}

// 👇 1. The Data Class
data class ReactionDetail(
    val userId: String,
    val username: String,
    val profileImageUrl: String?,
    val emoji: String,
    val isMe: Boolean
)

// 👇 2. The Bottom Sheet Content
@Composable
fun ReactionsBottomSheet(
    reactions: List<ReactionDetail>,
    onRemoveReaction: (ReactionDetail) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Reactions",
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        HorizontalDivider(color = AppColors.TextGray.copy(alpha = 0.1f))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(reactions, key = { it.userId }) { reaction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 👇 If it's the current user, make the row clickable to delete!
                        .clickable(enabled = reaction.isMe) {
                            onRemoveReaction(reaction)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(AppColors.SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!reaction.profileImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = reaction.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(getInitials(reaction.username), color = AppColors.AccentOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Name & "Tap to remove" hint
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (reaction.isMe) "You" else reaction.username,
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (reaction.isMe) {
                            Text("Tap to remove", color = AppColors.TextGray, fontSize = 12.sp)
                        }
                    }

                    // The Emoji
                    Text(text = reaction.emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherUserProfileSheet(
    username: String,
    imageUrl: String?,
    // NOTE: Pass the actual fetched user data here if you have it in your ViewModel!
    bio: String? = null,
    age: Int? = 0,
    skillLevel: String? = "Unknown",
    blade: String? = "Unknown",
    rubberFh: String? = "Unknown",
    rubberBh: String? = "Unknown",
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    // 👇 Your dynamic frosted glass background logic!
    val isDarkMode = org.ttproject.isDark
    val cardBgColor = if (isDarkMode) Color.Black.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- HEADER ---
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = getInitials(username),
                        color = AppColors.AccentOrange,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = username,
                color = AppColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            if (!bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bio,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardBgColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- BASIC INFO ---
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("BASIC INFO", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(cardBgColor).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "AGE", color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = age.toString(), color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(cardBgColor).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "LEVEL", color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = skillLevel ?: "?", color = AppColors.AccentOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- GEAR ---
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GEAR", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Blade
            ReadOnlyGearItem("BLADE", blade ?: "?", cardBgColor) { Text("🏓", fontSize = 16.sp) }
            Spacer(modifier = Modifier.height(8.dp))
            // FH
            ReadOnlyGearItem("FOREHAND", rubberFh ?: "?", cardBgColor) { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF4B4B))) }
            Spacer(modifier = Modifier.height(8.dp))
            // BH
            ReadOnlyGearItem("BACKHAND", rubberBh ?: "?", cardBgColor) { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.Black)) }
        }
    }
}

@Composable
private fun ReadOnlyGearItem(label: String, value: String, bgColor: Color, iconContent: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) { iconContent() }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, color = AppColors.TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MediaThumbnail(
    url: String,
    modifier: Modifier,
    isVideo: Boolean,
    videoThumbnailUrl: String?,
    bubbleShape: RoundedCornerShape
    // 👈 1. Removed onClick parameter!
) {
    Box(
        modifier = modifier.then(if (isVideo) Modifier.background(Color.Black) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val displayUrl = if (isVideo) (videoThumbnailUrl ?: url) else url

        if (displayUrl != null) {
            AsyncImage(
                model = displayUrl,
                contentDescription = "Media",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isVideo) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(bubbleShape),
                // 👈 2. Removed .clickable here! The parent handles it now.
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)))
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp).offset(x = 2.dp)
                    )
                }
            }
        }
        // 👈 3. Removed the else { Box(clickable) } completely!
    }
}

@Composable
fun VoiceMessageContent(
    voiceUrl: String,
    isMe: Boolean,
    player: AudioPlayer,
    activeUrl: String?,
    isAudioPlaying: Boolean,
    themeColor: Color,
    onPlayToggle: (String) -> Unit
) {
    val isActive = activeUrl == voiceUrl
    // 0.0 to 1.0 progress
    val progress = if (isActive && player.duration > 0) {
        (player.currentPosition.toFloat() / player.duration).coerceIn(0f, 1f)
    } else 0f

    // Time Logic: Show total duration if idle, show remaining time if playing
    val displayTime = remember(isActive, player.currentPosition, player.duration) {
        val totalMs = player.duration.coerceAtLeast(0L)
        val currentMs = player.currentPosition

        val timeToShow = if (!isActive || currentMs == 0L) totalMs else (totalMs - currentMs)

        val secs = (timeToShow / 1000) % 60
        val mins = (timeToShow / 1000) / 60
        "${mins}:${secs.toString().padStart(2, '0')}"
    }

    Row(
        modifier = Modifier.width(220.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle Button
        IconButton(
            onClick = { onPlayToggle(voiceUrl) },
            modifier = Modifier.size(36.dp).background(
                if (isMe) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f),
                CircleShape
            )
        ) {
            Icon(
                imageVector = if (isActive && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isMe) Color.White else AppColors.TextPrimary
            )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Slider(
                value = progress,
                onValueChange = {
                    if (isActive && isAudioPlaying) { // 👈 Only allow seeking if active
                        player.seekTo((it * player.duration).toLong())
                    }
                },
                // THE FIX: Set enabled to false if not playing.
                // This stops the slider from consuming horizontal drags!
                enabled = isActive && isAudioPlaying,
                modifier = Modifier
                    .height(24.dp)
                    .alpha(if (isActive) 1f else 0.5f),
                colors = SliderDefaults.colors(
                    thumbColor = if (isMe) Color.White else themeColor,
                    activeTrackColor = if (isMe) Color.White else themeColor,
                    inactiveTrackColor = if (isMe) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(0.3f),
                    // Hide the thumb dot when not playing to make it look like a progress bar
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = if (isMe) Color.White else themeColor,
                    disabledInactiveTrackColor = if (isMe) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(0.3f)
                )
            )

            Text(
                text = displayTime,
                fontSize = 10.sp,
                color = if (isMe) Color.White.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.align(Alignment.End).offset(y = (-4).dp)
            )
        }
    }
}

// A lightning-fast, KMP-safe modifier to constrain max width by percentage
fun Modifier.maxWidthPercent(percent: Float) = this.layout { measurable, constraints ->
    // Calculate 80% of whatever the parent container's max width is
    val maxAllowedWidth = (constraints.maxWidth * percent).toInt()

    // Force the child to measure itself within this new boundary
    val placeable = measurable.measure(
        constraints.copy(maxWidth = maxAllowedWidth)
    )

    // Place it!
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}