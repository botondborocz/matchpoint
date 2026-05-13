package org.ttproject.data

import kotlinx.serialization.Serializable

@Serializable
data class ReportedMediaDto(
    val id: String,
    val reporterId: String,
    val reporterUsername: String, // Good to know who reported it
    val locationId: String,
    val imageUrl: String,
    val reason: String?,
    val status: String, // "PENDING", "DELETED", "DISMISSED"
    val createdAt: Long
)