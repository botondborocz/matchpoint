package org.ttproject.data

import com.liftric.kvault.KVault

class IosTokenStorage : TokenStorage {
    // KVault wraps the iOS Keychain automatically!
    private val vault = KVault()
    private val key = "jwt_token"

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
}