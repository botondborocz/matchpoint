package org.ttproject.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.ttproject.SERVER_IP
import org.ttproject.data.TokenStorage
import org.ttproject.data.UpdateLanguageRequest
import org.ttproject.data.UpdateProfileRequest
import org.ttproject.data.UserProfile
import org.ttproject.data.UserBadgeMetricsDto
import kotlinx.datetime.TimeZone

// 1. The Interface
interface UserRepository {
    suspend fun getMyProfile(): UserProfile
    suspend fun getUserProfile(username: String): Result<UserProfile>
    suspend fun updateProfile(
        name: String, blade: String, forehand: String, backhand: String,
        bio: String?, birthDate: String?, skillLevel: String?
    ): Result<Boolean>
    suspend fun updateLanguage(language: String): Result<Boolean>
    suspend fun uploadProfileImage(imageBytes: ByteArray): Result<Boolean>

    // 👇 ADDED: Fetch Badge Metrics
    suspend fun getBadgeMetrics(): UserBadgeMetricsDto
    suspend fun togglePremiumStatus(): Result<Boolean>
}

// 2. The Implementation
class UserRepositoryImpl(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage
) : UserRepository {

    override suspend fun getMyProfile(): UserProfile {
        val token = tokenStorage.getToken()
            ?: throw Exception("No auth token found! User should be logged out.")

        val localTz = TimeZone.currentSystemDefault().id

        return try {
            val response = httpClient.get("${SERVER_IP}/api/users/me") {
                bearerAuth(token)
                header("X-Timezone", localTz)
            }

            if (response.status.value in 200..299) {
                val userProfile: UserProfile = response.body()
                tokenStorage.saveUserId(userProfile.id)
                tokenStorage.savePremiumStatus(userProfile.isPremium)
                tokenStorage.saveUserProfile(userProfile)

                // Trigger background language sync if pending offline change exists
                val pendingLang = tokenStorage.getPendingLanguageSync()
                if (pendingLang != null) {
                    try {
                        val syncRes = updateLanguage(pendingLang)
                        if (syncRes.isSuccess && syncRes.getOrNull() == true) {
                            tokenStorage.clearPendingLanguageSync()
                        }
                    } catch (e: Exception) {
                        println("Failed to background sync pending language: ${e.message}")
                    }
                }

                userProfile
            } else if (response.status.value == 401) {
                throw Exception("Session expired. Please log in again.")
            } else {
                throw Exception("Server error: ${response.status.description}")
            }
        } catch (e: Exception) {
            println("Network Error fetching profile: ${e.message}")
            tokenStorage.getUserProfile() ?: throw e
        }
    }

    override suspend fun getUserProfile(username: String): Result<UserProfile> {
        return try {
            val token = tokenStorage.getToken() ?: throw Exception("No auth token")

            val response = httpClient.get("${SERVER_IP}/api/users/profile/$username") {
                bearerAuth(token)
            }

            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch profile. Server returned: ${response.status}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(
        name: String, blade: String, forehand: String, backhand: String,
        bio: String?, birthDate: String?, skillLevel: String?
    ): Result<Boolean> {
        return try {
            val response = httpClient.put("${SERVER_IP}/api/users/me") {
                contentType(ContentType.Application.Json)
                setBody(UpdateProfileRequest(name, blade, forehand, backhand, bio, birthDate, skillLevel))
                bearerAuth(tokenStorage.getToken()!!)
            }

            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to update profile. Server returned: ${response.status}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun updateLanguage(language: String): Result<Boolean> {
        // Save locally first so the UI updates instantly
        tokenStorage.saveLanguage(language)
        
        // Also update the cached user profile's language field so subsequent cache reads are consistent
        val cachedProfile = tokenStorage.getUserProfile()
        if (cachedProfile != null) {
            tokenStorage.saveUserProfile(cachedProfile.copy(preferredLanguage = language))
        }

        return try {
            val response = httpClient.put("${SERVER_IP}/api/users/language") {
                contentType(ContentType.Application.Json)
                setBody(UpdateLanguageRequest(language))
                bearerAuth(tokenStorage.getToken()!!)
            }

            if (response.status.isSuccess()) {
                tokenStorage.clearPendingLanguageSync()
                Result.success(true)
            } else {
                tokenStorage.setPendingLanguageSync(language)
                Result.success(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tokenStorage.setPendingLanguageSync(language)
            Result.success(true)
        }
    }

    override suspend fun uploadProfileImage(imageBytes: ByteArray): Result<Boolean> {
        return try {
            val token = tokenStorage.getToken() ?: throw Exception("No auth token")

            val response = httpClient.post("${SERVER_IP}/api/users/profile-image") {
                bearerAuth(token)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("image", imageBytes, Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"profile_pic.jpg\"")
                            })
                        }
                    )
                )
            }

            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                Result.failure(Exception("Upload failed. Server returned: ${response.status}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 👇 ADDED: Implementation for fetching the badge metrics
    override suspend fun getBadgeMetrics(): UserBadgeMetricsDto {
        val token = tokenStorage.getToken()
            ?: throw Exception("No auth token found! User should be logged out.")

        return try {
            val response = httpClient.get("${SERVER_IP}/api/profile/badges") {
                bearerAuth(token)
            }

            if (response.status.isSuccess()) {
                val metrics: UserBadgeMetricsDto = response.body()
                tokenStorage.saveBadgeMetrics(metrics)
                metrics
            } else if (response.status.value == 401) {
                throw Exception("Session expired. Please log in again.")
            } else {
                throw Exception("Failed to fetch badge metrics. Server returned: ${response.status}")
            }
        } catch (e: Exception) {
            println("Network Error fetching badge metrics: ${e.message}")
            tokenStorage.getBadgeMetrics() ?: throw e
        }
    }

    override suspend fun togglePremiumStatus(): Result<Boolean> {
        val token = tokenStorage.getToken() ?: return Result.failure(Exception("No token found"))
        return try {
            val response = httpClient.post("${SERVER_IP}/api/users/me/toggle-premium") {
                bearerAuth(token)
            }
            println("Toggle Premium Response: ${response.status}")
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<Map<String, Boolean>>()
                val nextPremium = body["isPremium"] ?: false
                tokenStorage.savePremiumStatus(nextPremium)
                Result.success(nextPremium)
            } else {
                val nextPremium = !tokenStorage.getPremiumStatus()
                tokenStorage.savePremiumStatus(nextPremium)
                Result.success(nextPremium)
            }
        } catch (e: Exception) {
            val nextPremium = !tokenStorage.getPremiumStatus()
            tokenStorage.savePremiumStatus(nextPremium)
            Result.success(nextPremium)
        }
    }
}