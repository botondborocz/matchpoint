package org.ttproject.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.ttproject.AppColors
import org.ttproject.AppIcon
import org.ttproject.MainNavItems
import org.ttproject.NavRoute
import org.ttproject.NavigationItem
import org.ttproject.isDark
import org.ttproject.viewmodel.MessagesViewModel

@Composable
fun MobileBottomNavPill(
    currentRoute: NavRoute,
    onNavigate: (NavRoute) -> Unit,
    messagesViewModel: MessagesViewModel = koinViewModel()
) {
    // --- DESIGN TOGGLE ---
    // Set to true for the new Pill background, false for the original Glow Line
    val usePillActiveState = false

    val glowColor = AppColors.AccentOrange

    // --- DYNAMIC LIGHTING & MATERIAL PHYSICS ---
    val surfaceColor = if (isDark) AppColors.SurfaceDark else Color(0xFFF3F4F6)
    val shadowSpot = if (isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.12f)
    val shadowAmbient = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.04f)
    val borderTopColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.White
    val borderBottomColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.08f)
    val surfaceGradientTop = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.6f)
    val surfaceGradientBottom = if (isDark) Color.Black.copy(alpha = 0.3f) else Color.Transparent

    val chatThreads by messagesViewModel.filteredThreads.collectAsState()
    val unreadChatsCount = chatThreads.count { it.unreadCount > 0 }

    LaunchedEffect(currentRoute) {
        messagesViewModel.loadConnections()
    }

    val density = LocalDensity.current
    val bottomInsetDp = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val dynamicBottomPadding = if (bottomInsetDp > 20.dp) 4.dp else 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = dynamicBottomPadding
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(
                    elevation = if (isDark) 20.dp else 16.dp,
                    shape = RoundedCornerShape(36.dp),
                    spotColor = shadowSpot,
                    ambientColor = shadowAmbient
                )
                .clip(RoundedCornerShape(36.dp))
                .background(surfaceColor)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(surfaceGradientTop, Color.Transparent, surfaceGradientBottom)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(borderTopColor, Color.Transparent, borderBottomColor)
                    ),
                    shape = RoundedCornerShape(36.dp)
                )
        ) {
            val totalWidth = maxWidth
            val itemCount = MainNavItems.size
            val itemWidth = totalWidth / itemCount

            val selectedIndex = MainNavItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = tween(durationMillis = 300),
                label = "indicatorOffset"
            )

            // --- ACTIVE STATE INDICATOR (SLIDING) ---
            if (usePillActiveState) {
                // THE SLIDING PILL
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp) // Leaves a little gap so pills don't touch
                            .height(52.dp)
                            .background(
                                color = glowColor.copy(alpha = if (isDark) 0.15f else 0.12f),
                                shape = RoundedCornerShape(26.dp)
                            )
                    )
                }
            } else {
                // THE SLIDING GLOW LINE (Original)
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .width(44.dp)
                            .height(6.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        glowColor.copy(alpha = if (isDark) 0.4f else 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .padding(bottom = 3.dp)
                            .width(28.dp)
                            .height(3.dp)
                            .background(
                                color = glowColor,
                                shape = CircleShape
                            )
                    )
                }
            }

            // --- THE ICONS ROW ---
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainNavItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val label = stringResource(item.title)
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onNavigate(item.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        StandardTabItemPill(
                            item = item,
                            isSelected = isSelected,
                            label = label,
                            activeColor = glowColor,
                            isDark = isDark,
                            badgeCount = if (item.route == NavRoute.Messages) unreadChatsCount else 0,
                            isPillStyle = usePillActiveState // Pass the toggle down
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StandardTabItemPill(
    item: NavigationItem,
    isSelected: Boolean,
    label: String,
    activeColor: Color,
    isDark: Boolean,
    badgeCount: Int = 0,
    isPillStyle: Boolean = false // New parameter to adjust behavior
) {
    // If using the pill style, keep the icon centered. If using the line, keep the upward jump.
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isSelected && !isPillStyle) (-4).dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "tabOffset"
    )

    Box(contentAlignment = Alignment.Center) {
        // AMBIENT ICON GLOW (Only show if we are NOT using the Pill style, to prevent clashing)
        if (isSelected && !isPillStyle) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = animatedOffsetY)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = if (isDark) 0.15f else 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val inactiveTint = if (isDark) AppColors.TextSecondary else Color(0xFF6B7280)
            val tint = if (isSelected) activeColor else inactiveTint
            val iconSize = 24.dp

            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = AppColors.AccentOrange,
                            contentColor = Color.White,
                            modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                        ) {
                            Text(text = badgeCount.toString())
                        }
                    }
                },
                modifier = Modifier.offset(y = animatedOffsetY)
            ) {
                when (val icon = item.icon) {
                    is AppIcon.Vector -> Icon(
                        imageVector = icon.value,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(iconSize)
                    )
                    is AppIcon.Drawable -> Icon(
                        painter = painterResource(icon.value),
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            Text(
                text = label,
                fontSize = 10.sp,
                color = tint,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}