package org.ttproject.icon

import org.jetbrains.compose.resources.DrawableResource
import org.ttproject.shared.resources.Res as SharedRes
import org.ttproject.shared.resources.logo_square_medium
import org.ttproject.shared.resources.logo_square_medium_light
import org.ttproject.shared.resources.logo_square_medium_custom1
import org.ttproject.shared.resources.logo_square_medium_custom2
import org.ttproject.shared.resources.logo_square_medium_custom3
import org.ttproject.shared.resources.logo_square_medium_custom4
import org.ttproject.shared.resources.logo_square_medium_custom5
import org.ttproject.shared.resources.logo_square_medium_custom6
import org.ttproject.shared.resources.logo_square_medium_custom7
import org.ttproject.shared.resources.logo_square_medium_custom8

enum class PremiumAppIcon(
    val title: String,
    val alias: String,
    val isPremium: Boolean,
    val imageRes: DrawableResource // 👈 Point to your shared KMP images
) {
    DEFAULT("Default", "DefaultIcon", false, SharedRes.drawable.logo_square_medium),
    LIGHT("Light", "LightIcon", false, SharedRes.drawable.logo_square_medium_light),
    GOLD_ELITE("The Gold", "GoldIcon", true, SharedRes.drawable.logo_square_medium_custom1),
    STEALTH("Stealth", "StealthIcon", true, SharedRes.drawable.logo_square_medium_custom2),
    CYBER("Cyber", "CyberIcon", true, SharedRes.drawable.logo_square_medium_custom3),
    SYNTHWAVE("Synthwave", "SynthwaveIcon", true, SharedRes.drawable.logo_square_medium_custom4),
    ICE("Ice", "IceIcon", true, SharedRes.drawable.logo_square_medium_custom5),
    CHAMPIONSHIP("Championship", "ChampionshipIcon", true, SharedRes.drawable.logo_square_medium_custom6),
    CARBON("Carbon", "CarbonIcon", true, SharedRes.drawable.logo_square_medium_custom7),
    ADRENALIN("Adrenalin", "AdrenalinIcon", true, SharedRes.drawable.logo_square_medium_custom8),
}

// TODO CONVERT PNG TO WEBP TO SAVE SPACE, ESPECIALLY FOR APP ICONS. MOST PLATFORMS SUPPORT WEBP AND IT CAN SIGNIFICANTLY REDUCE THE SIZE OF YOUR ASSETS WITHOUT LOSING QUALITY.

interface AppIconManager {
    fun changeIcon(icon: PremiumAppIcon)
    fun getCurrentIconAlias(): String?
}