package org.ttproject.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun NativeImageActionMenu(
    isMine: Boolean,
    isTransitioning: Boolean = false, // 👇 Added parameter
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onReport: (String) -> Unit
)

@Composable
expect fun GalleryTopBar(
    modifier: Modifier,
    currentIndex: Int,
    totalImages: Int,
    isMine: Boolean,
    isTransitioning: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onReport: (String) -> Unit
)

@Composable
expect fun GalleryBottomBar(
    modifier: Modifier,
    authorName: String,
    isTransitioning: Boolean
)