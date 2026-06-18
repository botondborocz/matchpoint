package org.ttproject.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.ttproject.AppColors
import org.ttproject.MainNavItems
import org.ttproject.NavRoute
import org.ttproject.shared.resources.Res as SharedRes
import org.ttproject.AppIcon
import org.ttproject.isDark
import org.ttproject.shared.resources.pro_badge
import ttproject.composeapp.generated.resources.Res
import ttproject.composeapp.generated.resources.matchpoint_logo_long_dark
import ttproject.composeapp.generated.resources.matchpoint_logo_long_light
import org.koin.compose.koinInject
import org.ttproject.getPlatform

@Composable
fun DesktopSidebar(
    currentRoute: NavRoute,
    onNavigate: (NavRoute) -> Unit,
    tokenStorage: org.ttproject.data.TokenStorage = koinInject()
) {
    val isAndroid = getPlatform().name.contains("Android", ignoreCase = true)
    val defaultExpanded = !isAndroid
    val savedExpanded = tokenStorage.getSidebarExpanded()
    var isExpanded by remember { mutableStateOf(savedExpanded ?: defaultExpanded) }

    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 260.dp else 80.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .background(AppColors.SurfaceDark)
            .systemBarsPadding() // Prevents overlap with system UI
            .padding(vertical = 24.dp)
    ) {
        // --- Header / Logo Space ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Fixed height prevents vertical jumping
                .padding(horizontal = 16.dp)
        ) {
            // By placing the Menu icon in a 48dp Box, it aligns perfectly with the nav icons
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = {
                    val newValue = !isExpanded
                    isExpanded = newValue
                    tokenStorage.saveSidebarExpanded(newValue)
                }) {
                    Icon(Icons.Default.Menu, "Toggle Sidebar", tint = AppColors.TextPrimary)
                }
            }

            // Pure fade animation so bounds don't snap
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(300, delayMillis = 100)), // Wait for width to open
                exit = fadeOut(tween(100)) // Hide quickly before width crushes it
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDark) {
                        Image(
                            painter = painterResource(Res.drawable.matchpoint_logo_long_dark),
                            contentDescription = "App Logo",
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    else {
                        Image(
                            painter = painterResource(Res.drawable.matchpoint_logo_long_light),
                            contentDescription = "App Logo",
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Navigation Links Area ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val activeIndex = MainNavItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

            val indicatorOffsetY by animateDpAsState(
                targetValue = (activeIndex * 56).dp + 12.dp,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )

            // The Animated Orange Line
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = 16.dp, y = indicatorOffsetY)
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.AccentOrange)
            )

            // The Menu Items
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MainNavItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val label = stringResource(item.title)
                    val iconTint = if (isSelected) AppColors.AccentOrange else AppColors.TextSecondary
                    val textColor = if (isSelected) AppColors.AccentOrange else AppColors.TextSecondary

                    val contentOffsetX by animateDpAsState(
                        targetValue = if (isSelected && isExpanded) 6.dp else 0.dp,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AppColors.Background else Color.Transparent)
                            .clickable { onNavigate(item.route) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.offset(x = contentOffsetX)
                        ) {
                            // Fixed 48dp Box automatically centers the icon when sidebar shrinks to 80dp
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                when (val icon = item.icon) {
                                    is AppIcon.Vector -> Icon(icon.value, label, tint = iconTint, modifier = Modifier.size(20.dp))
                                    is AppIcon.Drawable -> Icon(painterResource(icon.value), label, tint = iconTint, modifier = Modifier.size(20.dp))
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn(tween(300, delayMillis = 100)),
                                exit = fadeOut(tween(100))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = label,
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false // CRUCIAL
                                    )
                                    if (item.isPro) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AppColors.AccentOrange.copy(alpha = 0.1f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(stringResource(SharedRes.string.pro_badge), color = AppColors.AccentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Profile Section Bottom ---
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = AppColors.TextSecondary.copy(alpha = 0.2f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // Fixed height prevents vertical jumping
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { /* Handle Profile Click */ }
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(AppColors.Background),
                    contentAlignment = Alignment.Center
                ) {
                    Text("JD", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(300, delayMillis = 100)),
                exit = fadeOut(tween(100))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("John", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text("Doe", color = AppColors.AccentOrange, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    Icon(Icons.Default.Settings, "Settings", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp).padding(end = 12.dp))
                }
            }
        }
    }
}