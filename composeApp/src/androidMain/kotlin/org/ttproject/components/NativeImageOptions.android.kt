package org.ttproject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.ttproject.AppColors

@Composable
actual fun NativeImageActionMenu(
    isMine: Boolean,
    modifier: Modifier,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppColors.SurfaceDark)
        ) {
            if (isMine) {
                DropdownMenuItem(
                    text = { Text("Delete Photo", color = Color(0xFFE57373)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE57373)) },
                    onClick = { expanded = false; onDelete() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Report Photo", color = AppColors.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = AppColors.TextGray) },
                    onClick = { expanded = false; onReport() }
                )
            }
        }
    }
}