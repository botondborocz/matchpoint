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