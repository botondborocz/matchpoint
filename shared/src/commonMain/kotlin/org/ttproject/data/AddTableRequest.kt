package org.ttproject.data

import kotlinx.serialization.Serializable

// IN YOUR SHARED MODELS FILE

@Serializable
data class AddTableRequest(
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val tableCount: Int,
    val isFree: Boolean,
    val notes: String? = null,
    val images: List<ByteArray> = emptyList() // 👇 Added for upload!
)

@Serializable
data class AddReviewRequest(
    val textContent: String? = null,
    val tags: List<String>,
    val images: List<ByteArray> = emptyList() // 👇 Added for upload!
)

@Serializable
data class TTReview(
    val id: String,
    val userId: String,
    val username: String = "Anonymous",
    val textContent: String? = null,
    val tags: List<String>,
    val imageUrls: List<String> = emptyList(), // 👇 Added to display!
    val createdAt: Long
)


@Serializable
data class ReviewResponse(
    val id: String,
    val userId: String,
    val username: String = "Anonymous",
    val textContent: String? = null,
    val tags: List<String>,
    val imageUrls: List<String> = emptyList(),
    val createdAt: Long
)

