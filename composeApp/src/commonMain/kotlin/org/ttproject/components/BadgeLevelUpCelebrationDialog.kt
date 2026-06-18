package org.ttproject.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.jetbrains.compose.resources.stringResource
import org.ttproject.AppColors
import org.ttproject.shared.resources.Res as SharedRes
import getBadgeColor
import getTierPrefix
import HexagonCanvas
import org.ttproject.shared.resources.badge_levelup_dismiss
import org.ttproject.shared.resources.badge_levelup_subtitle
import org.ttproject.shared.resources.badge_levelup_title
import org.ttproject.shared.resources.badge_name_addedTables
import org.ttproject.shared.resources.badge_name_currentStreak
import org.ttproject.shared.resources.badge_name_invitedFriends
import org.ttproject.shared.resources.badge_name_profileSwipes
import org.ttproject.shared.resources.badge_name_sentMessages
import org.ttproject.shared.resources.badge_name_successfulMatches
import org.ttproject.shared.resources.badge_name_uploadedPhotos
import org.ttproject.shared.resources.badge_name_writtenReviews

@Composable
fun getBadgeIcon(key: String): ImageVector {
    return when (key) {
        "addedTables" -> Icons.Default.Place
        "uploadedPhotos" -> Icons.Default.CameraAlt
        "writtenReviews" -> Icons.Default.RateReview
        "successfulMatches" -> Icons.Default.Bolt
        "sentMessages" -> Icons.Default.Message
        "profileSwipes" -> Icons.Default.Radar
        "currentStreak" -> Icons.Default.LocalFireDepartment
        "invitedFriends" -> Icons.Default.Campaign
        else -> Icons.Default.MilitaryTech
    }
}

@Composable
fun getBadgeName(key: String): String {
    val resId = when (key) {
        "addedTables" -> SharedRes.string.badge_name_addedTables
        "uploadedPhotos" -> SharedRes.string.badge_name_uploadedPhotos
        "writtenReviews" -> SharedRes.string.badge_name_writtenReviews
        "successfulMatches" -> SharedRes.string.badge_name_successfulMatches
        "sentMessages" -> SharedRes.string.badge_name_sentMessages
        "profileSwipes" -> SharedRes.string.badge_name_profileSwipes
        "currentStreak" -> SharedRes.string.badge_name_currentStreak
        "invitedFriends" -> SharedRes.string.badge_name_invitedFriends
        else -> null
    }
    return resId?.let { stringResource(it) } ?: key
}

@Composable
fun BadgeLevelUpCelebrationDialog(
    badgeKey: String,
    newLevel: Int,
    onDismiss: () -> Unit
) {
    val badgeColor = getBadgeColor(newLevel, isCompleted = true)
    val badgeName = getBadgeName(badgeKey)
    val tierPrefix = getTierPrefix(newLevel - 1)
    val badgeIcon = getBadgeIcon(badgeKey)

    val infiniteTransition = rememberInfiniteTransition(label = "levelupGlow")

    // Animated glow pulse
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Floating animation for small particles
    val particleOffset1 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particleOffset1"
    )
    val particleOffset2 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particleOffset2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5000f) // Keep it over everything
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AppColors.SurfaceDark,
                            Color(0xFF0F172A)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* prevent click-through */ }
                }
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Level Up Header text with neon glow text shadow
            Text(
                text = stringResource(SharedRes.string.badge_levelup_title),
                color = AppColors.AccentOrange,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(
                        color = AppColors.AccentOrange,
                        blurRadius = 15f
                    )
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Glowing Badge Container
            Box(
                modifier = Modifier
                    .size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background radial glow
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(glowScale)
                        .alpha(glowAlpha)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    badgeColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // The primary Hexagon Badge
                Box(
                    modifier = Modifier
                        .size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HexagonCanvas(color = badgeColor, strokeWidth = 4f)
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = badgeName,
                        tint = badgeColor,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Decorative particles
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(x = (-60).dp, y = particleOffset1.dp)
                        .background(badgeColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(x = 60.dp, y = particleOffset2.dp)
                        .background(badgeColor.copy(alpha = 0.6f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .offset(x = 20.dp, y = (-70).dp)
                        .background(AppColors.AccentOrange, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // congratulations text
            Text(
                text = stringResource(SharedRes.string.badge_levelup_subtitle, tierPrefix, badgeName),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Premium gradient action button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(52.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AppColors.AccentOrange,
                                Color(0xFFFF8A50)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Text(
                    text = stringResource(SharedRes.string.badge_levelup_dismiss),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
