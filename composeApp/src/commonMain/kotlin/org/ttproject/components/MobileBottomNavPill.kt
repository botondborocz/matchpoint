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
    val glowColor = AppColors.AccentOrange

    // --- DYNAMIC LIGHTING & MATERIAL PHYSICS ---
    // The base color of the pill (Off-white for light mode, dark for dark mode)
    val surfaceColor = if (isDark) AppColors.SurfaceDark else Color(0xFFF3F4F6)

    // Shadows (Heavy and dark for Dark Mode, soft and diffused for Light Mode)
    val shadowSpot = if (isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.12f)
    val shadowAmbient = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.04f)

    // Glass Edges (Catching the light on top, receding into shadow on bottom)
    val borderTopColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color.White
    val borderBottomColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.08f)

    // Surface Curvature (Makes the flat pill look slightly rounded)
    val surfaceGradientTop = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.6f)
    val surfaceGradientBottom = if (isDark) Color.Black.copy(alpha = 0.3f) else Color.Transparent

    val chatThreads by messagesViewModel.filteredThreads.collectAsState()
    val unreadChatsCount = chatThreads.count { it.unreadCount > 0 }

    LaunchedEffect(currentRoute) {
        messagesViewModel.loadConnections()
    }

    // 1. Get the raw bottom inset size from the system
    val density = LocalDensity.current
    val bottomInsetDp = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    // 2. Calculate dynamic padding.
    // We want a minimum of 16.dp distance from the screen edge,
    // but if the system inset is already huge (like iOS), we only add a tiny bit of extra breathing room (e.g., 4.dp).
    val dynamicBottomPadding = if (bottomInsetDp > 20.dp) 4.dp else 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 3. Apply the system insets FIRST
            .windowInsetsPadding(WindowInsets.navigationBars)
            // 4. Apply our adjusted padding
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
                // 1. DYNAMIC SHADOW
                .shadow(
                    elevation = if (isDark) 20.dp else 16.dp,
                    shape = RoundedCornerShape(36.dp),
                    spotColor = shadowSpot,
                    ambientColor = shadowAmbient
                )
                .clip(RoundedCornerShape(36.dp))
                // 2. BASE COLOR
                .background(surfaceColor)
                // 3. CURVATURE SHADING
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(surfaceGradientTop, Color.Transparent, surfaceGradientBottom)
                    )
                )
                // 4. GLASS EDGE BORDER
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

            // --- THE SLIDING GLOW LINE ---
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Outer glow
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
                // Bright core
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
                            badgeCount = if (item.route == NavRoute.Messages) unreadChatsCount else 0
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
    badgeCount: Int = 0
) {
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isSelected) (-4).dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "tabOffset"
    )

    Box(contentAlignment = Alignment.Center) {
        // 5. AMBIENT ICON GLOW
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = animatedOffsetY)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = if (isDark) 0.15f else 0.08f), // Softer glow in light mode
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
            // Dynamic tint for inactive state (Dark gray for light mode, light gray for dark mode)
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