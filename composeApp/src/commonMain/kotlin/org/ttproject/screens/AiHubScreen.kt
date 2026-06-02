package org.ttproject.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ttproject.AppColors
import org.ttproject.util.LiveActivityController

@Composable
fun AiHubScreen(
    onNavigateToAiChat: () -> Unit,
    onNavigateToVideoAnalysis: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // State to show the user the UI is busy
    var isProcessing by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
    ) {
        Text(
            text = "AI Coaching Hub",
            color = AppColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
        )

        AiFeatureCard(
            title = "Ask the AI Coach",
            description = "Get instant biomechanical advice, tactics, and drills.",
            icon = Icons.Default.Chat,
            onClick = onNavigateToAiChat
        )

        AiFeatureCard(
            title = "Video Analysis",
            description = "Upload your serve for frame-by-frame AI breakdown.",
            icon = Icons.Default.Analytics,
            onClick = onNavigateToVideoAnalysis
        )

        AiFeatureCard(
            title = if (isProcessing) "Processing Video..." else "Auto-Cutter",
            description = "Automatically extract your best points from match footage.",
            icon = Icons.Default.VideoLibrary,
            onClick = {
                if (!isProcessing) {
                    isProcessing = true
                    scope.launch {
                        // Simulate a 30-second task
                        for (i in 1..100) {
                            LiveActivityController.updateProgress(
                                percent = i,
                                message = "Analyzing frame $i of 100..."
                            )
                            delay(300) // 300ms * 100 = 30 seconds
                        }

                        LiveActivityController.updateProgress(
                            percent = 100,
                            message = "Video ready!",
                            isComplete = true
                        )
                        isProcessing = false
                    }
                }
            }
        )
    }
}

@Composable
fun AiFeatureCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceDark)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.AccentOrange.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.AccentOrange)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = AppColors.TextGray, fontSize = 14.sp)
        }
    }
}

