package org.ttproject.data

import kotlinx.serialization.Serializable

@Serializable
// Removed @Serializable here if BadgeData contains ImageVector,
// OR keep it and map it in your ViewModel!
data class Player(
    val id: String,
    val username: String,
    val skillLevel: String,
    val lat: Double? = null,
    val lng: Double? = null,
    // We'll calculate mock values for the UI display based on your design
    val age: Int = (18..45).random(),
    val elo: Int = (1000..2000).random(),
    val distanceKm: Int = (1..10).random(),
    val imageUrl: String? = null,
    val badgeMetrics: UserBadgeMetricsDto? = null
)