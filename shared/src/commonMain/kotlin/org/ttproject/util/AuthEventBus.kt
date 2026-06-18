package org.ttproject.util

import org.ttproject.di.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object AuthEventBus {
    private val _resetPasswordActive = MutableStateFlow(false)
    val resetPasswordActive: StateFlow<Boolean> = _resetPasswordActive.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun triggerResetPassword() {
        _resetPasswordActive.value = true
    }

    fun clearResetPassword() {
        _resetPasswordActive.value = false
    }

    fun handleDeeplink(url: String): Boolean {
        println("🔗 Handling deep link in common: $url")
        
        val isRecovery = url.contains("type=recovery") || url.contains("reset-password")
        
        try {
            val fragmentOrQuery = url.substringAfter('#', "").ifEmpty { url.substringAfter('?', "") }
            val params = fragmentOrQuery.split('&').associate {
                val parts = it.split('=', limit = 2)
                val key = parts[0]
                val value = if (parts.size > 1) parts[1] else ""
                key to value
            }
            
            val accessToken = params["access_token"]
            val refreshToken = params["refresh_token"]
            val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
            val tokenType = params["token_type"] ?: "bearer"

            if (accessToken != null && refreshToken != null) {
                if (isRecovery) {
                    triggerResetPassword()
                }
                
                val session = UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    tokenType = tokenType,
                    user = null
                )
                scope.launch {
                    try {
                        supabase.auth.importSession(session)
                        println("✅ Session imported successfully from deep link!")
                    } catch (e: Exception) {
                        println("❌ Failed to import session from deep link: ${e.message}")
                    }
                }
                return true
            } else {
                println("⚠️ Deep link did not contain access_token and refresh_token")
            }
        } catch (e: Exception) {
            println("❌ Failed to parse/import session from deep link: ${e.message}")
        }
        return false
    }
}
