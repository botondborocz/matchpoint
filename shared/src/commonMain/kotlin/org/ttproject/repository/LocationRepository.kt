package org.ttproject.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.ttproject.SERVER_IP
import org.ttproject.data.AddReviewRequest
import org.ttproject.data.AddTableRequest
import org.ttproject.data.Location
import org.ttproject.data.Player
import org.ttproject.data.TTReview
import org.ttproject.data.TokenStorage

interface LocationRepository {
    suspend fun getNearbyLocations(): List<Location>
    suspend fun addTable(request: AddTableRequest): Result<Unit>
    suspend fun addReview(locationId: String, request: AddReviewRequest): Result<Unit>
    suspend fun getReviews(locationId: String): Result<List<TTReview>>
    suspend fun addLocationImages(locationId: String, images: List<ByteArray>): Result<Unit>
}

class LocationRepositoryImpl(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage
) : LocationRepository {

    override suspend fun getNearbyLocations(): List<Location> {
        return try {
            httpClient.get("${SERVER_IP}/api/locations/nearby") {
            }.body<List<Location>>()
        } catch (e: Exception) {
            println("Network Error fetching locations: ${e.message}")
            emptyList()
        }
    }

    override suspend fun addTable(request: AddTableRequest): Result<Unit> {
        val token = tokenStorage.getToken() ?: return Result.failure(Exception("User not authenticated"))

        return try {
            val response = httpClient.post("${SERVER_IP}/api/locations") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            // 1. Pack the standard text data
                            append("latitude", request.latitude.toString())
                            append("longitude", request.longitude.toString())
                            append("type", request.type)
                            append("tableCount", request.tableCount.toString())
                            append("isFree", request.isFree.toString())
                            if (!request.notes.isNullOrBlank()) {
                                append("notes", request.notes)
                            }

                            // 2. Pack the Images using your existing Chat logic!
                            request.images.forEachIndexed { index, imageBytes ->
                                val isVideo = imageBytes.size > 8 &&
                                        imageBytes[4].toInt().toChar() == 'f' &&
                                        imageBytes[5].toInt().toChar() == 't' &&
                                        imageBytes[6].toInt().toChar() == 'y' &&
                                        imageBytes[7].toInt().toChar() == 'p'

                                val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
                                val extension = if (isVideo) "mp4" else "jpg"

                                append("media_$index", imageBytes, Headers.build {
                                    append(HttpHeaders.ContentType, mimeType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"table_media_$index.$extension\"")
                                })
                            }
                        }
                    )
                )
            }
            if (response.status == HttpStatusCode.Created) Result.success(Unit)
            else Result.failure(Exception("Failed to add table"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addReview(locationId: String, request: AddReviewRequest): Result<Unit> {
        val token = tokenStorage.getToken() ?: return Result.failure(Exception("User not authenticated"))

        return try {
            val response = httpClient.post("${SERVER_IP}/api/locations/$locationId/reviews") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            // 1. Pack the review text
                            if (!request.textContent.isNullOrBlank()) {
                                append("textContent", request.textContent)
                            }

                            // 2. Pack the tags as a single comma-separated string for easy parsing
                            append("tags", request.tags.joinToString(","))

                            // 3. Pack the images!
                            request.images.forEachIndexed { index, imageBytes ->
                                val isVideo = imageBytes.size > 8 &&
                                        imageBytes[4].toInt().toChar() == 'f' &&
                                        imageBytes[5].toInt().toChar() == 't' &&
                                        imageBytes[6].toInt().toChar() == 'y' &&
                                        imageBytes[7].toInt().toChar() == 'p'

                                val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
                                val extension = if (isVideo) "mp4" else "jpg"

                                append("media_$index", imageBytes, Headers.build {
                                    append(HttpHeaders.ContentType, mimeType)
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "filename=\"review_media_$index.$extension\""
                                    )
                                })
                            }
                        }
                    )
                )
            }
            if (response.status == HttpStatusCode.Created) Result.success(Unit)
            else Result.failure(Exception("Failed to add review"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReviews(locationId: String): Result<List<TTReview>> {
        return try {
            val response = httpClient.get("${SERVER_IP}/api/locations/$locationId/reviews")
            if (response.status.isSuccess()) Result.success(response.body())
            else Result.failure(Exception("Failed to fetch reviews"))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun addLocationImages(locationId: String, images: List<ByteArray>): Result<Unit> {
        val token = tokenStorage.getToken() ?: return Result.failure(Exception("User not authenticated"))

        return try {
            val response = httpClient.post("${SERVER_IP}/api/locations/$locationId/images") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            images.forEachIndexed { index, imageBytes ->
                                val isVideo = imageBytes.size > 8 &&
                                        imageBytes[4].toInt().toChar() == 'f' &&
                                        imageBytes[5].toInt().toChar() == 't' &&
                                        imageBytes[6].toInt().toChar() == 'y' &&
                                        imageBytes[7].toInt().toChar() == 'p'

                                val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
                                val extension = if (isVideo) "mp4" else "jpg"

                                append("media_$index", imageBytes, Headers.build {
                                    append(HttpHeaders.ContentType, mimeType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"addon_media_$index.$extension\"")
                                })
                            }
                        }
                    )
                )
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception("Failed to add images"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}