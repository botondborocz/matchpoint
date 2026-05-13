package org.ttproject.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun NativeImageActionMenu(
    isMine: Boolean,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onReport: (String) -> Unit // 👇 Now accepts the selected reason!
)