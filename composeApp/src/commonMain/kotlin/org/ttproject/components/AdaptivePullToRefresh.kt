package org.ttproject.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.ttproject.isIosPlatform
import org.ttproject.util.triggerRefreshHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptivePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isIosPlatform()) {
        // --- 🍎 iOS NATIVE FEEL ---
        // Content physically slides down to reveal a spinner behind it.
        val state = rememberPullToRefreshState()

        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                triggerRefreshHaptic()
            }
        }

        Box(
            modifier = modifier.pullToRefresh(
                isRefreshing = isRefreshing,
                state = state,
                onRefresh = onRefresh
            )
        ) {
            // 1. The iOS Spinner (Sitting BEHIND the content)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp), // The height of the refresh area
                contentAlignment = Alignment.Center
            ) {
                // Smoothly fade in the spinner as the user pulls down
                val alpha by animateFloatAsState(
                    targetValue = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f),
                    label = "spinner_alpha"
                )
                if (alpha > 0f) {
                    PlatformSpinner(
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { this.alpha = alpha }
                    )
                }
            }

            // 2. The Content (Pushed DOWN by the gesture)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Max pull distance is 60dp.
                        // It stays pushed down while the data is loading.
                        val maxDistance = 60.dp.toPx()
                        translationY = if (isRefreshing) {
                            maxDistance
                        } else {
                            (state.distanceFraction * maxDistance).coerceAtMost(maxDistance)
                        }
                    }
            ) {
                content()
            }
        }
    } else {
        // --- 🤖 ANDROID MATERIAL FEEL ---
        // The standard Floating Card overlay.
        val state = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = state,
            modifier = modifier
        ) {
            content()
        }
    }
}