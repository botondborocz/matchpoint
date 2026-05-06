package org.ttproject

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import org.ttproject.SharedTheme
import org.ttproject.util.LocalThemeMode
import org.ttproject.util.ThemeMode

val isDark: Boolean
    @Composable
    get() = when (LocalThemeMode.current) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

object AppColors {
//    val Background: Color
//        @Composable
//        get() {
//            val targetColor = if (isDark) SharedTheme.hexBackground.toColor() else SharedTheme.hexBackgroundLight.toColor()
//            val animatedColor by animateColorAsState(
//                targetValue = targetColor,
//                animationSpec = tween(durationMillis = 500), // 500ms fade!
//                label = "BackgroundAnimation"
//            )
//            return animatedColor
//        }
//    val SurfaceDark: Color
//        @Composable
//        get() {
//            val targetColor = if (isDark) SharedTheme.hexSurfaceDark.toColor() else SharedTheme.hexSurfaceLight.toColor()
//            val animatedColor by animateColorAsState(
//                targetValue = targetColor,
//                animationSpec = tween(durationMillis = 500),
//                label = "SurfaceAnimation"
//            )
//            return animatedColor
//        }
    val Background: Color
    @Composable
    get() {
        val theme = LocalAppThemeStyle.current // 👇 Lekérjük az aktív témát
        val targetColor = if (isDark) theme.darkBg.toColor() else theme.lightBg.toColor()
        val animatedColor by animateColorAsState(targetColor, tween(500), label = "BgAnim")
        return animatedColor
    }

    val SurfaceDark: Color
        @Composable
        get() {
            val theme = LocalAppThemeStyle.current
            val targetColor = if (isDark) theme.darkSurface.toColor() else theme.lightSurface.toColor()
            val animatedColor by animateColorAsState(targetColor, tween(500), label = "SurfaceAnim")
            return animatedColor
        }

    val AccentOrange: Color // A név maradhat, de mostantól a dinamikus akcentus színt adja!
        @Composable
        get() {
            val theme = LocalAppThemeStyle.current
            val targetColor = if (isDark) theme.darkAccent.toColor() else theme.lightAccent.toColor()
            val animatedColor by animateColorAsState(targetColor, tween(500), label = "AccentAnim")
            return animatedColor
        }
    val AccentCyan: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexAccentCyan.toColor() else SharedTheme.hexAccentCyanLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "AccentCyanAnimation"
            )
            return animatedColor
        }
    val TextPrimary: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexTextPrimary.toColor() else SharedTheme.hexTextPrimaryLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "TextPrimaryAnimation"
            )
            return animatedColor
        }
    val TextSecondary: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexTextSecondary.toColor() else SharedTheme.hexTextSecondaryLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "TextSecondaryAnimation"
            )
            return animatedColor
        }
    val TextGray: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexTextGray.toColor() else SharedTheme.hexTextGrayLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "TextGrayAnimation"
            )
            return animatedColor
        }
    val ProBadgeBg: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexProBadgeBg.toColor() else SharedTheme.hexProBadgeBgLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "ProBadgeAnimation"
            )
            return animatedColor
        }
//    val AccentOrange: Color
//        @Composable
//        get() {
//            val targetColor = if (isDark) SharedTheme.hexAccentOrange.toColor() else SharedTheme.hexAccentOrangeLight.toColor()
//            val animatedColor by animateColorAsState(
//                targetValue = targetColor,
//                animationSpec = tween(durationMillis = 500),
//                label = "AccentOrangeAnimation"
//            )
//            return animatedColor
//        }
    val ErrorText: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexErrorText.toColor() else SharedTheme.hexErrorTextLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "ErrorTextAnimation"
            )
            return animatedColor
        }
    val SuccessText: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexSuccessText.toColor() else SharedTheme.hexSuccessTextLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "SuccessTextAnimation"
            )
            return animatedColor
        }
    val ButtonBackground: Color
        @Composable
        get() {
            val targetColor = if (isDark) SharedTheme.hexButtonBackground.toColor() else SharedTheme.hexButtonBackgroundLight.toColor()
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 500),
                label = "ButtonBackgroundAnimation"
            )
            return animatedColor
        }
}
//    val Background = if(isDark) SharedTheme.hexBackground.toColor() else Color.White
//    val SurfaceDark = SharedTheme.hexSurfaceDark.toColor()
//    val AccentCyan = SharedTheme.hexAccentCyan.toColor()
//    val TextPrimary = SharedTheme.hexTextPrimary.toColor()
//    val TextSecondary = SharedTheme.hexTextSecondary.toColor()
//    val TextGray = SharedTheme.hexTextGray.toColor()
//    val ProBadgeBg = SharedTheme.hexProBadgeBg.toColor()
//    val AccentOrange = SharedTheme.hexAccentOrange.toColor()
//    val ErrorText = SharedTheme.hexErrorText.toColor()
//    val SuccessText = SharedTheme.hexSuccessText.toColor()
//    val ButtonBackground = SharedTheme.hexButtonBackground.toColor()
//}

fun String.toColor(): Color {
    return Color(removePrefix("#").toLong(16) or 0x00000000FF000000)
}

