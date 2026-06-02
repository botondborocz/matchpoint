package org.ttproject.data

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data class UserBadgeMetricsDto(
    val addedTables: Int = 0,
    val uploadedPhotos: Int = 0,
    val writtenReviews: Int = 0,
    val successfulMatches: Int = 0,
    val sentMessages: Int = 0,
    val profileSwipes: Int = 0,
    val trimmedVideos: Int = 0,
    val aiQuestions: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val invitedFriends: Int = 0
)

// --- 1. Adatmodellek (Data Models) ---

data class BadgeTierDefinition(
    val requirement: Int, // pl. 1, 5, 15, 50
    val suffixText: String // pl. "asztal", "fotó"
)

data class BadgeData(
    val baseName: String,         // pl. "Asztalfelderítő"
    val description: String,      // 👇 ÚJ: Hosszabb leírás a kitűzőhöz
    val icon: ImageVector,
    val currentValue: Int,        // A valós teljesítmény (pl. eddig 7 asztalt adott hozzá)
    val thresholds: List<BadgeTierDefinition> // Mindig 4 elemű lista
) {
    // Kiszámolja, hanyas szinten van (0-tól 4-ig)
    val currentLevel: Int get() {
        var level = 0
        for (tier in thresholds) {
            if (currentValue >= tier.requirement) level++
            else break
        }
        return level
    }

    // Visszaadja a pontos nevet (pl. "Haladó Asztalfelderítő")
    val fullName: String get() {
        val prefix = when (currentLevel) {
            0 -> "" // Ha még nem érte el az 1. szintet
            1 -> "Kezdő"
            2 -> "Haladó"
            3 -> "Profi"
            4 -> "Legenda"
            else -> ""
        }
        return if (prefix.isEmpty()) baseName else "$prefix $baseName"
    }
}