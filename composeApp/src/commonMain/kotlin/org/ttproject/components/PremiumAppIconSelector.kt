package org.ttproject.components

import androidx.compose.foundation.Image
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
import org.ttproject.AppColors
import org.ttproject.icon.PremiumAppIcon
import org.ttproject.icon.availableAppIcons

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

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(availableAppIcons) { icon ->
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
                    }
                )
            }
        }
    }
}

@Composable
private fun AppIconGridItem(
    icon: PremiumAppIcon,
    isSelected: Boolean,
    isUserPremium: Boolean,
    onClick: () -> Unit
) {
    val isLocked = icon.isPremium && !isUserPremium


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
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
            text = icon.title,
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