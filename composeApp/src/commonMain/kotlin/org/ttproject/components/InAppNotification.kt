package org.ttproject.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.ttproject.AppColors

@Composable
fun InAppNotification(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Cancel,
    iconColor: Color = Color(0xFFEF5350)
) {
    // Auto-dismiss timer
    LaunchedEffect(message) {
        if (message != null) {
            delay(3000L) // Show for 3 seconds
            onDismiss()
        }
    }

    // Put it at the highest Z-index so it floats over everything
    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(2000f),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(400)
            ) + fadeIn(tween(400)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeOut(tween(300))
        ) {
            // The Sleek Notification Pill
            Surface(
                shape = CircleShape,
                color = AppColors.SurfaceDark, // Use your dark theme color
                modifier = Modifier
                    .padding(top = 16.dp)
                    .shadow(8.dp, CircleShape)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Notification Icon",
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}