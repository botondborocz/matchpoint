package org.ttproject
//
//import androidx.compose.ui.graphics.Color
//import org.jetbrains.compose.resources.DrawableResource
//import ttproject.composeapp.generated.resources.Res as AppRes
//import ttproject.composeapp.generated.resources.logo_square_medium
//import ttproject.composeapp.generated.resources.logo_square_medium_light
//import ttproject.composeapp.generated.resources.logo_square_medium_custom1
//import ttproject.composeapp.generated.resources.logo_square_medium_custom2
//import ttproject.composeapp.generated.resources.logo_square_medium_custom3
//import ttproject.composeapp.generated.resources.logo_square_medium_custom4
//import ttproject.composeapp.generated.resources.logo_square_medium_custom5
//import ttproject.composeapp.generated.resources.logo_square_medium_custom6
//import ttproject.composeapp.generated.resources.logo_square_medium_custom7
//import ttproject.composeapp.generated.resources.logo_square_medium_custom8
//
//enum class PremiumAppIcon(
//    val title: String,
//    val alias: String,
//    val isPremium: Boolean,
//    val imageRes: DrawableResource // 👈 Point to your shared KMP images
//) {
//    DEFAULT("Default", "DefaultIcon", false, AppRes.drawable.logo_square_medium),
//    LIGHT("Light", "LightIcon", false, AppRes.drawable.logo_square_medium_light),
//    GOLD_ELITE("The Gold", "GoldIcon", true, AppRes.drawable.logo_square_medium_custom1),
//    STEALTH("Stealth", "StealthIcon", true, AppRes.drawable.logo_square_medium_custom2),
//    CYBER("Cyber", "CyberIcon", true, AppRes.drawable.logo_square_medium_custom3),
//    SYNTHWAVE("Synthwave", "SynthwaveIcon", true, AppRes.drawable.logo_square_medium_custom4),
//    ICE("Ice", "IceIcon", true, AppRes.drawable.logo_square_medium_custom5),
//    CHAMPIONSHIP("Championship", "ChampionshipIcon", true, AppRes.drawable.logo_square_medium_custom6),
//    CARBON("Carbon", "CarbonIcon", true, AppRes.drawable.logo_square_medium_custom7),
//    ADRENALIN("Adrenalin", "AdrenalinIcon", true, AppRes.drawable.logo_square_medium_custom8),
//}
//
//// TODO CONVERT PNG TO WEBP TO SAVE SPACE, ESPECIALLY FOR APP ICONS. MOST PLATFORMS SUPPORT WEBP AND IT CAN SIGNIFICANTLY REDUCE THE SIZE OF YOUR ASSETS WITHOUT LOSING QUALITY.
//
//interface AppIconManager {
//    fun changeIcon(icon: PremiumAppIcon)
//    fun getCurrentIconAlias(): String?
//}