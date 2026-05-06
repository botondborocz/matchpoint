package org.ttproject.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ttproject.AppColors
import org.ttproject.AppThemeStyle
import org.ttproject.toColor

@Composable
fun PremiumThemeSelector(
    currentThemeStyle: AppThemeStyle,
    isUserPremium: Boolean = false,
    onThemeSelected: (AppThemeStyle) -> Unit,
    onPremiumLockedClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PRÉMIUM TÉMÁK",
            color = AppColors.TextGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(AppThemeStyle.values()) { theme ->
                val isSelected = currentThemeStyle == theme
                val isLocked = theme.isPremium && !isUserPremium

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(90.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp, 120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (isLocked) onPremiumLockedClick()
                                else onThemeSelected(theme)
                            }
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) AppColors.AccentOrange else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val lightPath = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(w, 0f)
                                lineTo(0f, h)
                                close()
                            }
                            drawPath(lightPath, color = theme.lightBg.toColor())

                            val darkPath = Path().apply {
                                moveTo(w, 0f)
                                lineTo(w, h)
                                lineTo(0f, h)
                                close()
                            }
                            drawPath(darkPath, color = theme.darkBg.toColor())

                            if (isLocked) {
                                drawRect(color = Color.Black.copy(alpha = 0.5f))
                            }

                            drawCircle(
                                color = theme.darkAccent.toColor(),
                                radius = 24f,
                                center = center,
                                alpha = if (isLocked) 0.5f else 1f
                            )
                        }

                        if (isLocked) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Prémium",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = theme.title,
                        color = if (isSelected) AppColors.AccentOrange
                        else if (isLocked) AppColors.TextGray
                        else AppColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}