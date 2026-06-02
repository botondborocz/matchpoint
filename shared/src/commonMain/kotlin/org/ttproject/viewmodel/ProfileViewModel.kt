package org.ttproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ttproject.data.TokenStorage
import org.ttproject.data.UserBadgeMetricsDto
import org.ttproject.icon.AppIconManager
import org.ttproject.icon.PremiumAppIcon
import org.ttproject.repository.UserRepository

sealed class ProfileState {
    object Loading : ProfileState()
    // 👇 Added imageUrl here
    data class Success(
        val name: String?,
        val elo: Int,
        val winRate: String,
        val language: String?,
        val imageUrl: String? = null,
        val blade: String? = null,
        val rubberFh: String? = null,
        val rubberBh: String? = null,
        val bio: String? = null,
        val birthDate: String? = null,
        val skillLevel: String? = null,
        val age: Int? = null,
        val badgeMetrics: UserBadgeMetricsDto? = null,
        val isPremium: Boolean
    ) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Loading : UpdateState()
    object Success : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val appIconManager: AppIconManager,
    private val tokenStorage: TokenStorage
    ) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val uiState: StateFlow<ProfileState> = _uiState

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        fetchUserProfile()
    }

    fun fetchUserProfile(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _uiState.value !is ProfileState.Success) {
                _uiState.value = ProfileState.Loading
            }
            try {
                // 1. Fetch the main profile data
                val user = userRepository.getMyProfile()

                // 👇 2. Fetch the badge metrics (wrapped in try-catch so it doesn't break the profile if it fails)
                val metrics = try {
                    userRepository.getBadgeMetrics()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null // Fallback to null if the network request fails
                }

                // 3. Update the UI state with BOTH profile and badge data
                _uiState.value = ProfileState.Success(
                    name = user.name, elo = user.elo, winRate = user.winRate,
                    language = user.preferredLanguage, imageUrl = user.imageUrl,
                    blade = user.blade, rubberFh = user.rubberFh, rubberBh = user.rubberBh,
                    bio = user.bio, birthDate = user.birthDate,
                    skillLevel = user.skillLevel, age = user.age,
                    badgeMetrics = metrics, isPremium = user.isPremium
                )
            } catch (e: Exception) {
                if (_uiState.value !is ProfileState.Success) {
                    _uiState.value = ProfileState.Error("Failed to load profile: ${e.message}")
                }
            }
        }
    }

    fun updateProfile(
        name: String, blade: String, forehand: String, backhand: String,
        bio: String?, birthDate: String?, skillLevel: String? // 👈 Added
    ) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            // 👇 Pass them to the repo
            val result = userRepository.updateProfile(name, blade, forehand, backhand, bio, birthDate, skillLevel)

            if (result.isSuccess) {
                _updateState.value = UpdateState.Success
                fetchUserProfile() // Refresh profile data after successful update
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _updateState.value = UpdateState.Error(errorMsg)
            }
        }
    }

    // 👇 New function to handle the image upload
    fun uploadProfileImage(imageBytes: ByteArray) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading

            val result = userRepository.uploadProfileImage(imageBytes)

            if (result.isSuccess) {
                _updateState.value = UpdateState.Success
                // Re-fetch the profile so the UI gets the new Firebase Download URL from the server
                fetchUserProfile()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown upload error"
                _updateState.value = UpdateState.Error(errorMsg)
            }
        }
    }

    fun clearData() {
        _uiState.value = ProfileState.Loading
        _updateState.value = UpdateState.Idle
    }

    fun changeLanguage(newLanguage: String) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading

            val result = userRepository.updateLanguage(newLanguage)

            if (result.isSuccess) {
                _updateState.value = UpdateState.Success
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _updateState.value = UpdateState.Error(errorMsg)
            }
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    fun changeAppIcon(icon: PremiumAppIcon, isUserPremium: Boolean) {
        // Double-check premium status just in case
        if (icon.isPremium && !isUserPremium) {
            _updateState.value = UpdateState.Error("Ez egy prémium ikon!")
            return
        }

        viewModelScope.launch {
            try {
                // 1. Tell the OS to change the icon (Android / iOS native code)
                appIconManager.changeIcon(icon)

                // 2. Save it to local storage so the app remembers it on next boot
                tokenStorage.saveAppIcon(icon.alias)

            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Error("Nem sikerült megváltoztatni az ikont.")
            }
        }
    }

    fun togglePremiumStatus() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            val result = userRepository.togglePremiumStatus()
            println("Toggle Premium Result: $result") // Debug log to check the result

            if (result.isSuccess) {
                _updateState.value = UpdateState.Success
                fetchUserProfile() // 🌟 Boom: Re-fetches user profile to update all screens instantly
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _updateState.value = UpdateState.Error(errorMsg)
            }
        }
    }
}