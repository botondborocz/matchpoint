package org.ttproject.data

import com.liftric.kvault.KVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class IosTokenStorage : TokenStorage {
    // KVault wraps the iOS Keychain automatically!
    private val vault = KVault()
    private val key = "jwt_token"
    private val json = Json { ignoreUnknownKeys = true }

    override fun saveToken(token: String) { vault.set(key, token) }
    override fun getToken(): String? = vault.string(key)
    override fun clearToken() { vault.deleteObject(key) }
    override fun saveUserId(userId: String) { vault.set("user_id", userId) }
    override fun getUserId(): String? = vault.string("user_id")
    override fun clearUserId() { vault.deleteObject("user_id") }
    override fun saveLanguage(languageCode: String) { vault.set("language", languageCode) }
    override fun getLanguage(): String? = vault.string("language")
    override fun clearLanguage() { vault.deleteObject("language") }
    override fun saveThemeMode(mode: String) { vault.set("theme_mode", mode) }
    override fun getThemeMode(): String = vault.string("theme_mode") ?: "system"
    override fun saveMapChoice(choice: String) { vault.set("map_choice", choice) }
    override fun getMapChoice(): String? = vault.string("map_choice")
    override fun clearMapChoice() { vault.deleteObject("map_choice") }
    override fun saveAppTheme(theme: String) { vault.set("app_theme", theme) }
    override fun getAppTheme(): String? = vault.string("app_theme")
    override fun saveAppIcon(iconAlias: String) { vault.set("app_icon", iconAlias) }
    override fun getAppIcon(): String? = vault.string("app_icon")
    override fun savePremiumStatus(isPremium: Boolean) { vault.set("local_premium", isPremium.toString()) }
    override fun getPremiumStatus(): Boolean = vault.string("local_premium")?.toBoolean() ?: false
    override fun clearPremiumStatus() { vault.deleteObject("local_premium") }

    override fun saveUserProfile(profile: UserProfile) {
        val serialized = json.encodeToString(profile)
        vault.set("user_profile_cache", serialized)
    }

    override fun getUserProfile(): UserProfile? {
        val serialized = vault.string("user_profile_cache") ?: return null
        return try {
            json.decodeFromString<UserProfile>(serialized)
        } catch (e: Exception) {
            null
        }
    }

    override fun saveBadgeMetrics(metrics: UserBadgeMetricsDto) {
        val serialized = json.encodeToString(metrics)
        vault.set("badge_metrics_cache", serialized)
    }

    override fun getBadgeMetrics(): UserBadgeMetricsDto? {
        val serialized = vault.string("badge_metrics_cache") ?: return null
        return try {
            json.decodeFromString<UserBadgeMetricsDto>(serialized)
        } catch (e: Exception) {
            null
        }
    }

    override fun setPendingLanguageSync(language: String?) {
        if (language != null) {
            vault.set("pending_language_sync", language)
        } else {
            vault.deleteObject("pending_language_sync")
        }
    }

    override fun getPendingLanguageSync(): String? = vault.string("pending_language_sync")

    override fun clearPendingLanguageSync() { vault.deleteObject("pending_language_sync") }

    override fun saveSidebarExpanded(isExpanded: Boolean) { vault.set("sidebar_expanded", isExpanded.toString()) }

    override fun getSidebarExpanded(): Boolean? = vault.string("sidebar_expanded")?.toBoolean()
}