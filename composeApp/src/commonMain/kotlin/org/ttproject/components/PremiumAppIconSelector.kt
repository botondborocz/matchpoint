package org.ttproject.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.ttproject.AppColors
import org.ttproject.icon.PremiumAppIcon
import org.ttproject.icon.availableAppIcons
import org.ttproject.shared.resources.*
import org.ttproject.shared.resources.Res as SharedRes


@Composable
fun PremiumAppIconSelector(
    currentAppIcon: PremiumAppIcon,
    isUserPremium: Boolean,
    onIconSelected: (PremiumAppIcon) -> Unit,
    onPremiumLockedClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "APP IKON", // Matches the style of "PRÉMIUM TÉMÁK"
            color = AppColors.TextGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val chunkedIcons = availableAppIcons.chunked(3)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            chunkedIcons.forEach { rowIcons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowIcons.forEach { icon ->
                        Box(modifier = Modifier.weight(1f)) {
                            AppIconGridItem(
                                icon = icon,
                                isSelected = currentAppIcon == icon,
                                isUserPremium = isUserPremium,
                                onClick = {
                                    if (icon.isPremium && !isUserPremium) {
                                        onPremiumLockedClick()
                                    } else {
                                        onIconSelected(icon)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    val emptySlots = 3 - rowIcons.size
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
private fun AppIconGridItem(
    icon: PremiumAppIcon,
    isSelected: Boolean,
    isUserPremium: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = icon.isPremium && !isUserPremium

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) AppColors.AccentOrange
                    else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            // 1. The Icon Image
            Image(
                painter = painterResource(icon.imageRes),
                contentDescription = icon.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 2. Dark Overlay if Locked
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }

            // 3. Badges (Lock or Crown)
            if (isLocked) {
                // Circular Lock Badge (Top Right to match Themes)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else if (icon.isPremium) {
                // Crown Badge for unlocked premium icons (Bottom Right)
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Premium",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Label Text
        Text(
            text = icon.getLocalizedTitle(),
            color = if (isSelected) AppColors.AccentOrange
            else if (isLocked) AppColors.TextGray
            else AppColors.TextPrimary,
            fontSize = 11.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 12.sp
        )
    }
}

@Composable
fun PremiumAppIcon.getLocalizedTitle(): String {
    return when (this) {
        PremiumAppIcon.DEFAULT -> stringResource(SharedRes.string.icon_default)
        PremiumAppIcon.LIGHT -> stringResource(SharedRes.string.icon_light)
        PremiumAppIcon.GOLD_ELITE -> stringResource(SharedRes.string.icon_gold_elite)
        PremiumAppIcon.GOLD_ELITE_DARK -> stringResource(SharedRes.string.icon_gold_elite_dark)
        PremiumAppIcon.STEALTH -> stringResource(SharedRes.string.icon_stealth)
        PremiumAppIcon.STEALTH_DARK -> stringResource(SharedRes.string.icon_stealth_dark)
        PremiumAppIcon.CYBER -> stringResource(SharedRes.string.icon_cyber)
        PremiumAppIcon.CYBER_DARK -> stringResource(SharedRes.string.icon_cyber_dark)
        PremiumAppIcon.SYNTHWAVE -> stringResource(SharedRes.string.icon_synthwave)
        PremiumAppIcon.SYNTHWAVE_DARK -> stringResource(SharedRes.string.icon_synthwave_dark)
        PremiumAppIcon.SYNTHWAVE_EXTRA -> stringResource(SharedRes.string.icon_synthwave_extra)
        PremiumAppIcon.ICE -> stringResource(SharedRes.string.icon_ice)
        PremiumAppIcon.ICE_DARK -> stringResource(SharedRes.string.icon_ice_dark)
        PremiumAppIcon.ICE_EXTRA -> stringResource(SharedRes.string.icon_ice_extra)
        PremiumAppIcon.CHAMPIONSHIP -> stringResource(SharedRes.string.icon_championship)
        PremiumAppIcon.CHAMPIONSHIP_DARK -> stringResource(SharedRes.string.icon_championship_dark)
        PremiumAppIcon.ADRENALIN -> stringResource(SharedRes.string.icon_adrenalin)
        PremiumAppIcon.ADRENALIN_DARK -> stringResource(SharedRes.string.icon_adrenalin_dark)
    }
}