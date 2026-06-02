package org.ttproject.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.ttproject.SERVER_IP
import org.ttproject.data.GoogleLoginRequest
import org.ttproject.data.LoginRequest
import org.ttproject.data.RegisterRequest
import org.ttproject.data.TokenResponse
import org.ttproject.data.TokenStorage

// 1. The Interface
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<String>
    suspend fun register(email: String, password: String): Result<String>
    suspend fun googleLogin(idToken: String): Result<String>
}

// 2. The Implementation (where Ktor lives)
class AuthRepositoryImpl(
    private val httpClient: HttpClient, // Injected by Koin
    private val tokenStorage: TokenStorage // Injected by Koin
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<String> {
        return try {
            val cleanEmail = email.trim()
            // Point this to your Google Cloud IP!
            val response = httpClient.post("${SERVER_IP}/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(cleanEmail, password))
            }

            if (response.status.isSuccess()) {
                // Parse the JSON token from your Ktor backend
                val tokenResponse = response.body<TokenResponse>()
                tokenStorage.saveToken(tokenResponse.token)
                Result.success("Logged in perfectly!")
            } else {
                Result.failure(Exception("Invalid credentials"))
            }
        } catch (e: Exception) {
            println(e.message)
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, password: String): Result<String> {
        return try {
            val response = httpClient.post("${SERVER_IP}/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email.trim(), password))
            }

            if (response.status.isSuccess()) {
                // Assuming your backend logs them in and returns a token immediately after registering!
                val tokenResponse = response.body<TokenResponse>()
                tokenStorage.saveToken(tokenResponse.token)
                Result.success("Registered successfully!")
            } else {
                // You can parse the error body here if your backend sends specific messages like "Email already in use"
                Result.failure(Exception("Registration failed. Please check your details."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun googleLogin(idToken: String): Result<String> {
        return try {
            val response = httpClient.post("${SERVER_IP}/api/auth/google") {
                contentType(ContentType.Application.Json)
                setBody(GoogleLoginRequest(idToken))
            }

            if (response.status.isSuccess()) {
                val tokenResponse = response.body<TokenResponse>()
                tokenStorage.saveToken(tokenResponse.token)
                Result.success("Google login successful!")
            } else {
                // 👇 READ THE ACTUAL SERVER ERROR!
                val serverError = response.bodyAsText()
                println("🚨 BACKEND REJECTED TOKEN: HTTP ${response.status.value} - $serverError")
                Result.failure(Exception("Server said: $serverError"))
            }
        } catch (e: Exception) {
            println("🚨 NETWORK CRASH: ${e.message}")
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}