package org.ttproject.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.ttproject.AppColors
import org.ttproject.icon.PremiumAppIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumAppIconSelector(
    currentAppIcon: PremiumAppIcon,
    isUserPremium: Boolean,
    onIconSelected: (PremiumAppIcon) -> Unit,
    onPremiumLockedClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceDark)
            .animateContentSize()
    ) {
        // Trigger Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AppShortcut, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("APP ICON", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.weight(1f))

            Text(currentAppIcon.title, color = AppColors.AccentOrange, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.rotate(rotation))
        }

        // Expanded Grid
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                HorizontalDivider(color = AppColors.TextPrimary.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))

                // Using FlowRow for a beautiful wrap-around grid
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    PremiumAppIcon.entries.forEach { icon ->
                        AppIconGridItem(
                            icon = icon,
                            isSelected = currentAppIcon == icon,
                            isUserPremium = isUserPremium,
                            onClick = {
                                if (icon.isPremium && !isUserPremium) {
                                    onPremiumLockedClick()
                                } else {
                                    onIconSelected(icon)
                                    expanded = false
                                }
                            }
                        )
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
    onClick: () -> Unit
) {
    val isLocked = icon.isPremium && !isUserPremium

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                // If your PNGs already have rounded corners, you can remove this clip!
                // If your PNGs are perfect squares, keep it to give them the iOS squirckle look.
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) AppColors.AccentOrange else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {

            // 👇 THIS REPLACES THE WHOLE BOX/TEXT MOCKUP 👇
            Image(
                painter = painterResource(icon.imageRes),
                contentDescription = icon.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop // Ensures it fills the 64.dp square nicely
            )

            // Locked overlay (Keep this!)
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else if (icon.isPremium) {
                // Premium Crown (Keep this!)
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Premium",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = icon.title,
            color = if (isSelected) AppColors.AccentOrange else AppColors.TextGray,
            fontSize = 10.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 12.sp
        )
    }
}