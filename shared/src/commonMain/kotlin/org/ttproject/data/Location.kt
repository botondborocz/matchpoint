package org.ttproject.data

import kotlinx.serialization.Serializable

@Serializable
enum class LocationType {
    Indoor, Outdoor
}

@Serializable
data class Location(
    val id: String? = null,           // Primary Key (null for new items)
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: LocationType,
    val isFree: Boolean,
    val tableCount: Int,
    val address: String? = null,
    val createdBy: String? = null,     // ID of the user who added it
    val imageUrls: List<String> = emptyList()
)