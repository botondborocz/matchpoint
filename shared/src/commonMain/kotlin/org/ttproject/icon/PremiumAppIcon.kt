package org.ttproject.icon

import org.jetbrains.compose.resources.DrawableResource
import org.ttproject.isIosPlatform
import org.ttproject.shared.resources.Res as SharedRes
import org.ttproject.shared.resources.logo_square_medium
import org.ttproject.shared.resources.logo_square_medium_light
import org.ttproject.shared.resources.logo_square_medium_custom1
import org.ttproject.shared.resources.logo_square_medium_custom1_dark
import org.ttproject.shared.resources.logo_square_medium_custom1_light
import org.ttproject.shared.resources.logo_square_medium_custom2
import org.ttproject.shared.resources.logo_square_medium_custom2_dark
import org.ttproject.shared.resources.logo_square_medium_custom2_light
import org.ttproject.shared.resources.logo_square_medium_custom3
import org.ttproject.shared.resources.logo_square_medium_custom3_dark
import org.ttproject.shared.resources.logo_square_medium_custom3_light
import org.ttproject.shared.resources.logo_square_medium_custom4
import org.ttproject.shared.resources.logo_square_medium_custom4_dark
import org.ttproject.shared.resources.logo_square_medium_custom4_light
import org.ttproject.shared.resources.logo_square_medium_custom5
import org.ttproject.shared.resources.logo_square_medium_custom5_dark
import org.ttproject.shared.resources.logo_square_medium_custom5_light
import org.ttproject.shared.resources.logo_square_medium_custom6
import org.ttproject.shared.resources.logo_square_medium_custom6_dark
import org.ttproject.shared.resources.logo_square_medium_custom7
import org.ttproject.shared.resources.logo_square_medium_custom8
import org.ttproject.shared.resources.logo_square_medium_custom8_dark
import org.ttproject.shared.resources.logo_square_medium_custom8_light

enum class PremiumAppIcon(
    val title: String,
    val alias: String,
    val isPremium: Boolean,
    val imageRes: DrawableResource // 👈 Point to your shared KMP images
) {
    DEFAULT("Default", "DefaultIcon", false, SharedRes.drawable.logo_square_medium),
    LIGHT("Light", "LightIcon", false, SharedRes.drawable.logo_square_medium_light),
    GOLD_ELITE("The Gold", "GoldIcon", true, SharedRes.drawable.logo_square_medium_custom1_light),
    GOLD_ELITE_DARK("The Gold Dark", "GoldIconDark", true, SharedRes.drawable.logo_square_medium_custom1_dark),
    STEALTH("Stealth", "StealthIcon", true, SharedRes.drawable.logo_square_medium_custom2_light),
    STEALTH_DARK("Stealth Dark", "StealthIconDark", true, SharedRes.drawable.logo_square_medium_custom2_dark),
    CYBER("Cyber", "CyberIcon", true, SharedRes.drawable.logo_square_medium_custom3_light),
    CYBER_DARK("Cyber Dark", "CyberIconDark", true, SharedRes.drawable.logo_square_medium_custom3_dark),
    SYNTHWAVE("Synthwave", "SynthwaveIcon", true, SharedRes.drawable.logo_square_medium_custom4_light),
    SYNTHWAVE_DARK("Synthwave Dark", "SynthwaveIconDark", true, SharedRes.drawable.logo_square_medium_custom4_dark),
    SYNTHWAVE_EXTRA("Synthwave Extra", "SynthwaveIconExtra", true, SharedRes.drawable.logo_square_medium_custom4),
    ICE("Ice", "IceIcon", true, SharedRes.drawable.logo_square_medium_custom5_light),
    ICE_DARK("Ice Dark", "IceIconDark", true, SharedRes.drawable.logo_square_medium_custom5_dark),
    ICE_EXTRA("Ice Extra", "IceIconExtra", true, SharedRes.drawable.logo_square_medium_custom5),
    CHAMPIONSHIP("Championship", "ChampionshipIcon", true, SharedRes.drawable.logo_square_medium_custom6),
    CHAMPIONSHIP_DARK("Championship Dark", "ChampionshipIconDark", true, SharedRes.drawable.logo_square_medium_custom6_dark),
//    CARBON("Carbon", "CarbonIcon", true, SharedRes.drawable.logo_square_medium_custom7),
    ADRENALIN("Adrenalin", "AdrenalinIcon", true, SharedRes.drawable.logo_square_medium_custom8_light),
    ADRENALIN_DARK("Adrenalin Dark", "AdrenalinIconDark", true, SharedRes.drawable.logo_square_medium_custom8_dark),
}

// TODO CONVERT PNG TO WEBP TO SAVE SPACE, ESPECIALLY FOR APP ICONS. MOST PLATFORMS SUPPORT WEBP AND IT CAN SIGNIFICANTLY REDUCE THE SIZE OF YOUR ASSETS WITHOUT LOSING QUALITY.


val availableAppIcons: List<PremiumAppIcon>
    get() = if (isIosPlatform()) {
        listOf(
            PremiumAppIcon.DEFAULT,
            PremiumAppIcon.LIGHT,
            PremiumAppIcon.GOLD_ELITE,
            PremiumAppIcon.STEALTH,
            PremiumAppIcon.CYBER,
            PremiumAppIcon.SYNTHWAVE,
            PremiumAppIcon.SYNTHWAVE_EXTRA,
            PremiumAppIcon.ICE,
            PremiumAppIcon.ICE_EXTRA,
            PremiumAppIcon.CHAMPIONSHIP,
            PremiumAppIcon.ADRENALIN
        )
    } else {
        PremiumAppIcon.entries
    }

interface AppIconManager {
    fun changeIcon(icon: PremiumAppIcon)
    fun getCurrentIconAlias(): String?
}