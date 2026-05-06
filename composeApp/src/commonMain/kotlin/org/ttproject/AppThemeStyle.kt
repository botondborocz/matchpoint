package org.ttproject

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppThemeStyle(
    val title: String,
    val darkBg: String, val darkSurface: String, val darkAccent: String,
    val lightBg: String, val lightSurface: String, val lightAccent: String,
    val isPremium: Boolean
) {
    // 0. Az Alapértelmezett (Ingyenes) Téma
    DEFAULT(
        title = "Alapértelmezett",
        darkBg = SharedTheme.hexBackground,
        darkSurface = SharedTheme.hexSurfaceDark,
        darkAccent = SharedTheme.hexAccentOrange,
        lightBg = SharedTheme.hexBackgroundLight,
        lightSurface = SharedTheme.hexSurfaceLight,
        lightAccent = SharedTheme.hexAccentOrangeLight,
        isPremium = false
    ),

    // 1. VIP (Sötét: Mélyfekete -> Sötétszürke kártyák | Világos: Fehér -> Krémfehér kártyák)
    VIP("Elit / VIP", "#000000", "#1A1A1A", "#D4AF37", "#F8F9FA", "#FFFFFF", "#B8860B", true),

    // 2. ADRENALIN (Sötét: Éjsötét -> Grafitszürke)
    ADRENALIN("Adrenalin", "#121212", "#242424", "#E63946", "#F8F9FA", "#FFFFFF", "#D00000", true),

    // 3. MATRIX (Sötét: Fekete -> Zöldes-fekete kártyák)
    MATRIX("Mátrix", "#0D1117", "#161B22", "#00FF41", "#F0FFF4", "#FFFFFF", "#15803D", true),

    // 4. ARCTIC (Sötét: Mélykék -> Kékes-szürke kártyák)
    ARCTIC("Sarkvidék", "#0B101E", "#151E32", "#00E5FF", "#E0F7FA", "#FFFFFF", "#0097A7", true),

    // 5. NEON (Sötét: Padlizsán -> Éjlila kártyák)
    NEON("Neon Éjszaka", "#1A0B2E", "#2D164C", "#FF007F", "#FDF2F8", "#FFFFFF", "#BE185D", true),

    // 6. STEALTH (Sötét: Tiszta fekete -> Minimál szürke)
    STEALTH("Lopakodó", "#000000", "#141414", "#E0E0E0", "#EFEFEF", "#FFFFFF", "#424242", true),

    // 7. TOKYO (Sötét: Sötétkék -> Liláskék kártyák)
    TOKYO("Tokiói Fények", "#0A192F", "#112240", "#8A2BE2", "#F5F3FF", "#FFFFFF", "#6D28D9", true),

    // 8. CLASSIC (Sötét: Mélyzöld -> Asztalzöld kártyák)
    CLASSIC("Bajnokság", "#0F1A15", "#1B2D24", "#FCD34D", "#ECFDF5", "#FFFFFF", "#059669", true),

    // 9. ROYAL (Sötét: Királykék -> Palaszürke kártyák)
    ROYAL("MatchPoint", "#0F172A", "#1E293B", "#3B82F6", "#F0F9FF", "#FFFFFF", "#1D4ED8", true),

    // 10. VOLT (Sötét: Sötétlila -> Szürkéslila kártyák)
    VOLT("Kinetikus", "#181824", "#252536", "#FFF000", "#FEFCE8", "#FFFFFF", "#A16207", true)
}

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.DEFAULT }