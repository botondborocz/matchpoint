package org.ttproject

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ttproject.composeapp.generated.resources.Res
import ttproject.composeapp.generated.resources.abstract_paddle
import ttproject.composeapp.generated.resources.abstract_paddle_2
import ttproject.composeapp.generated.resources.paddels
import ttproject.composeapp.generated.resources.paddles_2
import ttproject.composeapp.generated.resources.pro_arena

// 👇 1. Helper function that mirrors your AppColors animation logic
@Composable
fun animatedThemeColor(darkColor: Color, lightColor: Color): Color {
    val targetColor = if (isDark) darkColor else lightColor
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "ThemeColorAnimation"
    )
    return animatedColor
}

// 👇 2. The Intelligent Data Class
data class ChatTheme(
    val name: String,
    val darkBgColors: List<Color>,
    val lightBgColors: List<Color>,
    val darkMyBubble: Color,
    val lightMyBubble: Color,
    val darkOtherBubble: Color,
    val lightOtherBubble: Color = Color.White, // White is the standard for modern Light UI
    val backgroundImage: Any? = null // 👈 Add this (can be PainterResource or URL)
) {
    // These properties animate dynamically whenever called from a Compose context!
    val backgroundBrush: Brush
        @Composable
        get() {
            val animatedColors = darkBgColors.mapIndexed { index, darkColor ->
                val lightColor = lightBgColors.getOrElse(index) { lightBgColors.last() }
                animatedThemeColor(darkColor, lightColor)
            }

            // 👇 THE FIX: If there is only 1 color, duplicate it so Compose doesn't crash!
            val safeColors = if (animatedColors.size == 1) {
                listOf(animatedColors.first(), animatedColors.first())
            } else {
                animatedColors
            }

            return Brush.verticalGradient(safeColors)
        }

    val myBubbleColor: Color
        @Composable get() = animatedThemeColor(darkMyBubble, lightMyBubble)

    val otherBubbleColor: Color
        @Composable get() = animatedThemeColor(darkOtherBubble, lightOtherBubble)
}

// 👇 3. The 15 Pre-Defined Themes
object ChatThemeManager {
    val themes = listOf(
        ChatTheme(
            name = "Default",
            darkBgColors = listOf(SharedTheme.hexBackground.toColor()),
            lightBgColors = listOf(SharedTheme.hexBackgroundLight.toColor()),
            darkMyBubble = SharedTheme.hexAccentOrange.toColor(),
            lightMyBubble = SharedTheme.hexAccentOrangeLight.toColor(),
            darkOtherBubble = SharedTheme.hexSurfaceDark.toColor(),
            lightOtherBubble = SharedTheme.hexSurfaceLight.toColor()
        ),
        ChatTheme(
            name = "Pro Arena",
            // Deep shadowy teal and black to match the arena floor and dark lighting
            darkBgColors = listOf(Color(0xFF071A15), Color(0xFF000000)),
            // Crisp light gray/blue to reflect the glowing stadium signs
            lightBgColors = listOf(Color(0xFFE8F0F2), Color(0xFFCFD8DC)),
            // The rich blue of the ping pong tables under the spotlights
            darkMyBubble = Color(0xFF1E56A0),
            lightMyBubble = Color(0xFF154380),
            // A dark, slightly transparent teal-gray for contrast
            darkOtherBubble = Color(0xFF172A2B).copy(alpha = 0.8f),
            backgroundImage = Res.drawable.pro_arena
        ),
        ChatTheme(
            name = "Ping Pong Table",
            // Deep table blue for dark mode backgrounds
            darkBgColors = listOf(Color(0xFF1F3A5F), Color(0xFF11223A)),
            // Warm wood and brick tones for light mode backgrounds
            lightBgColors = listOf(Color(0xFFF3E5D8), Color(0xFFE6D2BA)),
            // The vibrant red from the paddle
            darkMyBubble = Color(0xFFE53935),
            lightMyBubble = Color(0xFFC62828),
            // A muted version of the table blue for the other person's bubble
            darkOtherBubble = Color(0xFF284872).copy(alpha = 0.8f),
            backgroundImage = Res.drawable.paddles_2
        ),
        ChatTheme(
            name = "Abstract Paddle",
            // Deep, dark cyber-spaces (almost black with a hint of navy)
            darkBgColors = listOf(Color(0xFF090A0F), Color(0xFF111424)),
            // Icy, high-contrast grays for light mode
            lightBgColors = listOf(Color(0xFFE3E8F0), Color(0xFFCFD8E3)),
            // The intense neon red/pink glowing from the center of the paddle
            darkMyBubble = Color(0xFFFF1744),
            lightMyBubble = Color(0xFFD50000),
            // A dark glass-like bubble tinted with the neon cyan/blue from the image
            darkOtherBubble = Color(0xFF0B192C).copy(alpha = 0.8f),
            backgroundImage = Res.drawable.abstract_paddle_2
        ),
        ChatTheme(
            name = "Midnight",
            darkBgColors = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)),
            lightBgColors = listOf(Color(0xFFE6E6FA), Color(0xFFD8BFD8), Color(0xFFE0B0FF)),
            darkMyBubble = Color(0xFF8A2387),
            lightMyBubble = Color(0xFF9C27B0),
            darkOtherBubble = Color(0xFF302B63).copy(alpha = 0.6f)
        ),
        ChatTheme(
            name = "Ocean",
            darkBgColors = listOf(Color(0xFF000428), Color(0xFF004e92)),
            lightBgColors = listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2)),
            darkMyBubble = Color(0xFF00B4DB),
            lightMyBubble = Color(0xFF00838F),
            darkOtherBubble = Color(0xFF004e92).copy(alpha = 0.6f)
        ),
        ChatTheme(
            name = "Sunset",
            darkBgColors = listOf(Color(0xFF23074d), Color(0xFFcc5333)),
            lightBgColors = listOf(Color(0xFFFFF0E5), Color(0xFFFFD1B3)),
            darkMyBubble = Color(0xFFFF416C),
            lightMyBubble = Color(0xFFFF416C),
            darkOtherBubble = Color(0xFF4A154B).copy(alpha = 0.6f)
        ),
        ChatTheme(
            name = "Forest",
            darkBgColors = listOf(Color(0xFF0f2027), Color(0xFF203a43), Color(0xFF2c5364)),
            lightBgColors = listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9), Color(0xFFA5D6A7)),
            darkMyBubble = Color(0xFF11998e),
            lightMyBubble = Color(0xFF2E7D32),
            darkOtherBubble = Color(0xFF203a43).copy(alpha = 0.6f)
        ),
        ChatTheme(
            name = "Cyberpunk",
            darkBgColors = listOf(Color(0xFF120E1F), Color(0xFF25082E), Color(0xFF1A0B2E)),
            lightBgColors = listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFFCE93D8)),
            darkMyBubble = Color(0xFF00F0FF),
            lightMyBubble = Color(0xFFD500F9),
            darkOtherBubble = Color(0xFFD000FF).copy(alpha = 0.4f)
        ),
        ChatTheme(
            name = "Matcha",
            darkBgColors = listOf(Color(0xFF1E2A24), Color(0xFF2B3A32)),
            lightBgColors = listOf(Color(0xFFF1F8E9), Color(0xFFDCEDC8)),
            darkMyBubble = Color(0xFF86A873),
            lightMyBubble = Color(0xFF558B2F),
            darkOtherBubble = Color(0xFF2B3A32).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Lavender",
            darkBgColors = listOf(Color(0xFF2A233C), Color(0xFF403058)),
            lightBgColors = listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7)),
            darkMyBubble = Color(0xFFB088F9),
            lightMyBubble = Color(0xFF8E24AA),
            darkOtherBubble = Color(0xFF403058).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Coffee",
            darkBgColors = listOf(Color(0xFF2C1E16), Color(0xFF4A3022)),
            lightBgColors = listOf(Color(0xFFEFEBE9), Color(0xFFD7CCC8)),
            darkMyBubble = Color(0xFFC49A76),
            lightMyBubble = Color(0xFF5D4037),
            darkOtherBubble = Color(0xFF4A3022).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Ruby",
            darkBgColors = listOf(Color(0xFF2D0A0E), Color(0xFF5E131E)),
            lightBgColors = listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2)),
            darkMyBubble = Color(0xFFE23E57),
            lightMyBubble = Color(0xFFC62828),
            darkOtherBubble = Color(0xFF5E131E).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Abyss",
            darkBgColors = listOf(Color(0xFF050505), Color(0xFF121417)),
            lightBgColors = listOf(Color(0xFFECEFF1), Color(0xFFCFD8DC)),
            darkMyBubble = Color(0xFF3B82F6),
            lightMyBubble = Color(0xFF1565C0),
            darkOtherBubble = Color(0xFF1F2937).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Cherry Blossom",
            darkBgColors = listOf(Color(0xFF331922), Color(0xFF572A3C)),
            lightBgColors = listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0)),
            darkMyBubble = Color(0xFFFFA6C9),
            lightMyBubble = Color(0xFFAD1457),
            darkOtherBubble = Color(0xFF572A3C).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Neon Mint",
            darkBgColors = listOf(Color(0xFF0D211C), Color(0xFF12352B)),
            lightBgColors = listOf(Color(0xFFE0F2F1), Color(0xFFB2DFDB)),
            darkMyBubble = Color(0xFF00E676),
            lightMyBubble = Color(0xFF00897B),
            darkOtherBubble = Color(0xFF12352B).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Volcano",
            darkBgColors = listOf(Color(0xFF290A0A), Color(0xFF3D1C1C)),
            lightBgColors = listOf(Color(0xFFFBE9E7), Color(0xFFFFCCBC)),
            darkMyBubble = Color(0xFFFF5722),
            lightMyBubble = Color(0xFFD84315),
            darkOtherBubble = Color(0xFF3D1C1C).copy(alpha = 0.8f)
        ),
        ChatTheme(
            name = "Royal Gold",
            darkBgColors = listOf(Color(0xFF1A1A1A), Color(0xFF2A2A2A)),
            lightBgColors = listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3)),
            darkMyBubble = Color(0xFFFFD700),
            lightMyBubble = Color(0xFFFF8F00),
            darkOtherBubble = Color(0xFF333333).copy(alpha = 0.8f)
        )
    )
}