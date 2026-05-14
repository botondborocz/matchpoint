package org.ttproject.components

import androidx.compose.runtime.Composable

@Composable
expect fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
)