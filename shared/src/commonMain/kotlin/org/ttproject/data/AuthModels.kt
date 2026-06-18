package org.ttproject.data

import kotlinx.serialization.Serializable

// 1. What we send TO the server
@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String
)

@Serializable
data class GoogleLoginRequest(
    val idToken: String
)

// 2. What we get BACK from the server
@Serializable
data class TokenResponse(
    val token: String
)

@Serializable
data class UpdateLanguageRequest(
    val language: String
)

@Serializable
data class SupabaseTokenRequest(
    val supabaseToken: String
)