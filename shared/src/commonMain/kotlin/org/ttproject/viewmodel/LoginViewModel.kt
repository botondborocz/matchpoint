package org.ttproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ttproject.repository.AuthRepository

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<LoginState>(LoginState.Idle)
    val uiState: StateFlow<LoginState> = _uiState

    // --- STANDARD LOGIN (SUPABASE) ---
    fun login(email: String, password: String) {
        _uiState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.loginWithSupabase(email, password)

            result.fold(
                onSuccess = {
                    _uiState.value = LoginState.Success
                },
                onFailure = { error ->
                    _uiState.value = LoginState.Error(error.message ?: "Invalid email or password")
                }
            )
        }
    }

    // --- REGISTRATION (SUPABASE) ---
    fun register(email: String, password: String) {
        _uiState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.signUpWithSupabase(email, password)

            result.fold(
                onSuccess = {
                    _uiState.value = LoginState.VerificationSent
                },
                onFailure = { error ->
                    _uiState.value = LoginState.Error(error.message ?: "Registration failed")
                }
            )
        }
    }

    // --- FORGOT PASSWORD EMAIL (SUPABASE) ---
    fun sendPasswordResetEmail(email: String) {
        _uiState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)

            result.fold(
                onSuccess = {
                    _uiState.value = LoginState.PasswordResetSent
                },
                onFailure = { error ->
                    _uiState.value = LoginState.Error(error.message ?: "Failed to send reset link")
                }
            )
        }
    }

    // --- RESET PASSWORD RESET (SUPABASE) ---
    fun resetPassword(password: String) {
        _uiState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.resetPassword(password)

            result.fold(
                onSuccess = {
                    _uiState.value = LoginState.Success
                },
                onFailure = { error ->
                    _uiState.value = LoginState.Error(error.message ?: "Failed to reset password")
                }
            )
        }
    }

    // --- GOOGLE LOGIN ---
    fun googleLogin(idToken: String) {
        _uiState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.googleLogin(idToken)

            result.fold(
                onSuccess = {
                    _uiState.value = LoginState.Success
                },
                onFailure = { error ->
                    _uiState.value = LoginState.Error(error.message ?: "Google login failed")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = LoginState.Idle
    }
}

// A simple sealed class to represent what the UI should draw
sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data object VerificationSent : LoginState()
    data object PasswordResetSent : LoginState()
    data class Error(val message: String) : LoginState()
}