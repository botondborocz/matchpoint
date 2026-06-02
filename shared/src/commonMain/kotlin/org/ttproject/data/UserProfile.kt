package org.ttproject.data

import kotlinx.serialization.Serializable

// 👇 @Serializable is required so Ktor knows how to convert the JSON string into this Kotlin object
@Serializable
data class UserProfile(
    val id: String,
    val name: String?,
    val email: String,
    val elo: Int,
    val winRate: String,
    val preferredLanguage: String? = "en", // Optional fallback
    val imageUrl: String? = null, // Optional profile picture URL
    val blade: String? = null,
    val rubberFh: String? = null,
    val rubberBh: String? = null,
    val bio: String? = null,
    val birthDate: String? = null,
    val skillLevel: String? = null,
    val age: Int? = null,
    val isPremium: Boolean = false
)

@Serializable
data class UpdateProfileRequest(
    val name: String,
    val blade: String,
    val forehand: String,
    val backhand: String,
    val bio: String? = null,
    val birthDate: String? = null,
    val skillLevel: String? = null
)

@Serializable
data class PlayerResponse(
    val id: String,
    val username: String?,
    val skillLevel: String,
    val lat: Double?,
    val lng: Double?,
    val preferredLanguage: String? = null,
    val imageUrl: String? = null,
    val badgeMetrics: UserBadgeMetricsDto? = null,
    val isPremium: Boolean = false,
    val hasSwipedMeRight: Boolean = false
)