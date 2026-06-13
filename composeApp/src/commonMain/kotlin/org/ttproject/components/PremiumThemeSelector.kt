package org.ttproject.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.ttproject.AppColors
import org.ttproject.AppThemeStyle
import org.ttproject.toColor
import org.ttproject.shared.resources.*
import org.ttproject.shared.resources.Res as SharedRes


@Composable
fun PremiumThemeSelector(
    currentThemeStyle: AppThemeStyle,
    isUserPremium: Boolean = false,
    onThemeSelected: (AppThemeStyle) -> Unit,
    onPremiumLockedClick: () -> Unit
) {
    val allThemes = AppThemeStyle.values()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "TÉMÁK ÉS SZÍNEK",
            color = AppColors.TextGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val chunkedThemes = allThemes.toList().chunked(3)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            chunkedThemes.forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowThemes.forEach { theme ->
                        Box(modifier = Modifier.weight(1f)) {
                            ThemeCardItem(
                                theme = theme,
                                isSelected = currentThemeStyle == theme,
                                isLocked = theme.isPremium && !isUserPremium,
                                onThemeSelected = onThemeSelected,
                                onPremiumLockedClick = onPremiumLockedClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    val emptySlots = 3 - rowThemes.size
                    if (emptySlots > 0) {
                        repeat(emptySlots) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCardItem(
    theme: AppThemeStyle,
    isSelected: Boolean,
    isLocked: Boolean,
    onThemeSelected: (AppThemeStyle) -> Unit,
    onPremiumLockedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(3f / 4f)
                .fillMaxWidth()
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
            text = theme.getLocalizedTitle(),
            color = if (isSelected) AppColors.AccentOrange
            else if (isLocked) AppColors.TextGray
            else AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppThemeStyle.getLocalizedTitle(): String {
    return when (this) {
        AppThemeStyle.DEFAULT -> stringResource(SharedRes.string.theme_default)
        AppThemeStyle.VIP -> stringResource(SharedRes.string.theme_vip)
        AppThemeStyle.ADRENALIN -> stringResource(SharedRes.string.theme_adrenalin)
        AppThemeStyle.MATRIX -> stringResource(SharedRes.string.theme_matrix)
        AppThemeStyle.ARCTIC -> stringResource(SharedRes.string.theme_arctic)
        AppThemeStyle.NEON -> stringResource(SharedRes.string.theme_neon)
        AppThemeStyle.STEALTH -> stringResource(SharedRes.string.theme_stealth)
        AppThemeStyle.TOKYO -> stringResource(SharedRes.string.theme_tokyo)
        AppThemeStyle.CLASSIC -> stringResource(SharedRes.string.theme_classic)
        AppThemeStyle.ROYAL -> stringResource(SharedRes.string.theme_royal)
        AppThemeStyle.VOLT -> stringResource(SharedRes.string.theme_volt)
        AppThemeStyle.SUNSET -> stringResource(SharedRes.string.theme_sunset)
    }
}