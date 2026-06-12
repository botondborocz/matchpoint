package org.ttproject.data

// We expect the Android and iOS folders to provide the actual code for this later!
interface TokenStorage {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
    fun saveUserId(userId: String)
    fun getUserId(): String?
    fun clearUserId()
    fun saveLanguage(languageCode: String)
    fun getLanguage(): String?
    fun clearLanguage()
    fun saveThemeMode(mode: String)
    fun getThemeMode(): String
    fun saveMapChoice(choice: String)
    fun getMapChoice(): String?
    fun clearMapChoice()
    fun saveAppTheme(theme: String)
    fun getAppTheme(): String?
    fun saveAppIcon(iconAlias: String)
    fun getAppIcon(): String?
    fun savePremiumStatus(isPremium: Boolean)
    fun getPremiumStatus(): Boolean
    fun clearPremiumStatus()
    
    // 👇 Profile & Badge metrics caching for offline viewing
    fun saveUserProfile(profile: UserProfile)
    fun getUserProfile(): UserProfile?
    fun saveBadgeMetrics(metrics: UserBadgeMetricsDto)
    fun getBadgeMetrics(): UserBadgeMetricsDto?
    fun setPendingLanguageSync(language: String?)
    fun getPendingLanguageSync(): String?
    fun clearPendingLanguageSync()
}