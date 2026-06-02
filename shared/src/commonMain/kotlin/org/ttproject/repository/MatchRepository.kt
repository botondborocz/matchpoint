package org.ttproject.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete // 👈 NEW
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.ttproject.SERVER_IP
import org.ttproject.data.Player
import org.ttproject.data.SwipeRequest
import org.ttproject.data.SwipeResponse
import org.ttproject.data.TokenStorage

interface MatchRepository {
    suspend fun getNearbyPlayers(): List<Player>
    suspend fun recordSwipeAction(playerId: String, isLiked: Boolean): Boolean
    suspend fun undoSwipeAction(playerId: String): Boolean // 👈 NEW CONTRACT
    suspend fun getPeopleWhoLikedMe(): List<Player>
}

class MatchRepositoryImpl(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage
) : MatchRepository {

    override suspend fun getNearbyPlayers(): List<Player> {
        val token = tokenStorage.getToken() ?: throw Exception("No token found")
        return try {
            val response = httpClient.get("${SERVER_IP}/api/users/nearby") {
                bearerAuth(token)
            }
            response.body<List<Player>>()
        } catch (e: Exception) {
            println("Network Error fetching players: ${e.message}")
            emptyList()
        }
    }

    override suspend fun recordSwipeAction(playerId: String, isLiked: Boolean): Boolean {
        return try {
            val response = httpClient.post("${SERVER_IP}/api/users/$playerId/swipe") {
                contentType(ContentType.Application.Json)
                bearerAuth(tokenStorage.getToken()!!)
                setBody(SwipeRequest(isLiked = isLiked))
            }.body<SwipeResponse>()
            response.isMatch
        } catch (e: Exception) {
            println("Network Error recording swipe: ${e.message}")
            false
        }
    }

    // 👈 NEW NETWORK EXECUTOR: Direct transactional cleanup link
    override suspend fun undoSwipeAction(playerId: String): Boolean {
        return try {
            val response = httpClient.delete("${SERVER_IP}/api/users/$playerId/swipe") {
                bearerAuth(tokenStorage.getToken()!!)
            }
            response.status.value == 200
        } catch (e: Exception) {
            println("Network Error undoing swipe: ${e.message}")
            false
        }
    }

    override suspend fun getPeopleWhoLikedMe(): List<Player> {
        val token = tokenStorage.getToken() ?: throw Exception("No token found")
        return try {
            val response = httpClient.get("${SERVER_IP}/api/users/likes") {
                bearerAuth(token)
            }
            response.body<List<Player>>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}