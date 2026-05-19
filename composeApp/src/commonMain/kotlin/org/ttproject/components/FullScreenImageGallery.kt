package org.ttproject.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenImageGallery(
    images: List<String>,
    initialPage: Int,
    isMessageFromMe: Boolean,
    onDismiss: () -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onReport: ((String, String) -> Unit)? = null
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { images.size }
    )

    val scope = rememberCoroutineScope()

    val galleryOffsetY = remember { Animatable(0f) }
    var isGalleryZoomed by remember { mutableStateOf(false) }
    var isUiVisible by remember { mutableStateOf(true) }

    // 1. Give the dialog 350ms to finish its entrance animation before loading the UIKitView
    var isMenuReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        isMenuReady = true
    }

    // 2. Compute the exact moment the native view needs to hide
    val isSwipingToDismiss = galleryOffsetY.value > 5f
    val isPagingEnabled = !isGalleryZoomed && !isSwipingToDismiss
    val isTransitioning = !isMenuReady || !isUiVisible || isSwipingToDismiss

    FullScreenDialog(
        onDismissRequest = onDismiss
    ) {
        SetSystemBarsVisibility(isVisible = isUiVisible)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(
                    alpha = (1f - (galleryOffsetY.value / 1500f)).coerceIn(0f, 1f)
                ))
        ) {

            val handleDragEnd = {
                if (galleryOffsetY.value > 150f) {
                    scope.launch {
                        launch { galleryOffsetY.animateTo(2000f, tween(250)) }
                        delay(100)
                        onDismiss()
                    }
                } else {
                    scope.launch { galleryOffsetY.animateTo(0f, spring()) }
                }
            }

            // --- THE IMAGE PAGER & ZOOM MATH ---
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
                                        val newOffset = (galleryOffsetY.value + dragAmount).coerceAtLeast(0f)
                                        scope.launch { galleryOffsetY.snapTo(newOffset) }
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
                    val url = images.getOrNull(page) ?: ""
                    val isVideo = url.contains(".mp4")

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

                    LaunchedEffect(scaleAnim.value) {
                        val zoomed = scaleAnim.value > 1.01f
                        isGalleryZoomed = zoomed
                        if (zoomed && isUiVisible) {
                            isUiVisible = false
                        }
                    }

                    val painter = coil3.compose.rememberAsyncImagePainter(model = url)
                    val latestIntrinsicSize by rememberUpdatedState(painter.intrinsicSize)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        scope.launch {
                                            if (scaleAnim.value > 1f) {
                                                launch { scaleAnim.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
                                                launch { offsetXAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                                                launch { offsetYAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                                            } else {
                                                val targetScale = 2.5f
                                                val boxWidth = size.width.toFloat()
                                                val boxHeight = size.height.toFloat()

                                                var imgWidth = boxWidth
                                                var imgHeight = boxHeight
                                                if (latestIntrinsicSize.width > 0 && latestIntrinsicSize.height > 0) {
                                                    val fitScale = minOf(
                                                        boxWidth / latestIntrinsicSize.width,
                                                        boxHeight / latestIntrinsicSize.height
                                                    )
                                                    imgWidth = latestIntrinsicSize.width * fitScale
                                                    imgHeight = latestIntrinsicSize.height * fitScale
                                                }

                                                val center = Offset(boxWidth / 2f, boxHeight / 2f)
                                                val targetX = (center.x - tapOffset.x) * targetScale
                                                val targetY = (center.y - tapOffset.y) * targetScale

                                                val maxPanX = maxOf(0f, (imgWidth * targetScale - boxWidth) / 2f)
                                                val maxPanY = maxOf(0f, (imgHeight * targetScale - boxHeight) / 2f)

                                                launch { scaleAnim.animateTo(targetScale, tween(300, easing = FastOutSlowInEasing)) }
                                                launch { offsetXAnim.animateTo(targetX.coerceIn(-maxPanX, maxPanX), tween(300, easing = FastOutSlowInEasing)) }
                                                launch { offsetYAnim.animateTo(targetY.coerceIn(-maxPanY, maxPanY), tween(300, easing = FastOutSlowInEasing)) }
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
                                            if (event.changes.size > 1) event.changes.forEach { it.consume() }

                                            val newScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                                            val boxWidth = size.width.toFloat()
                                            val boxHeight = size.height.toFloat()

                                            var imgWidth = boxWidth
                                            var imgHeight = boxHeight
                                            if (latestIntrinsicSize.width > 0 && latestIntrinsicSize.height > 0) {
                                                val fitScale = minOf(
                                                    boxWidth / latestIntrinsicSize.width,
                                                    boxHeight / latestIntrinsicSize.height
                                                )
                                                imgWidth = latestIntrinsicSize.width * fitScale
                                                imgHeight = latestIntrinsicSize.height * fitScale
                                            }

                                            val maxPanX = maxOf(0f, (imgWidth * newScale - boxWidth) / 2f)
                                            val maxPanY = maxOf(0f, (imgHeight * newScale - boxHeight) / 2f)

                                            val newX = (offsetXAnim.value + pan.x).coerceIn(-maxPanX, maxPanX)
                                            val newY = (offsetYAnim.value + pan.y).coerceIn(-maxPanY, maxPanY)

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
                        if (isVideo) {
                            VideoPlayer(modifier = Modifier.fillMaxSize(), url = url)
                        } else {
                            androidx.compose.foundation.Image(
                                painter = painter,
                                contentDescription = "Full Screen Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scaleAnim.value
                                        scaleY = scaleAnim.value
                                        translationX = offsetXAnim.value
                                        translationY = offsetYAnim.value
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }

            // --- 3. YOUR NATIVE GALLERY TOP BAR ---
            // Wraps AnimatedVisibility so the container fades,
            // and passes `isTransitioning` down so your UIKitView knows when to hide!
            androidx.compose.animation.AnimatedVisibility(
                visible = isUiVisible && !isSwipingToDismiss,
                enter = androidx.compose.animation.fadeIn(tween(200)),
                exit = androidx.compose.animation.fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                GalleryTopBar(
                    modifier = Modifier.fillMaxWidth(),
                    currentIndex = pagerState.currentPage + 1,
                    totalImages = images.size,
                    isMine = isMessageFromMe,
                    // 👇 This makes the magic happen in your NativeImageActionMenu!
                    isTransitioning = isTransitioning,
                    onClose = {
                        scope.launch {
                            isMenuReady = false
                            delay(50)
                            onDismiss()
                        }
                    },
                    onDelete = {
                        val currentUrl = images.getOrNull(pagerState.currentPage) ?: ""
                        onDelete?.invoke(currentUrl)
                    },
                    onReport = { reason ->
                        val currentUrl = images.getOrNull(pagerState.currentPage) ?: ""
                        onReport?.invoke(currentUrl, reason)
                    }
                )
            }
        }
    }
}