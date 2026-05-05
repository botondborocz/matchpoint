package org.ttproject.viewmodel

import org.ttproject.data.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ttproject.repository.LocationRepository

sealed class LocationsUiState {
    object Loading : LocationsUiState()
    data class Success(val locations: List<Location>) : LocationsUiState()
    data class Error(val message: String) : LocationsUiState()
}

class LocationViewModel(
    private val repository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocationsUiState>(LocationsUiState.Loading)
    val uiState: StateFlow<LocationsUiState> = _uiState.asStateFlow()

    init {
        fetchNearbyLocations()
    }

    fun fetchNearbyLocations() {
        // This launches on the Main Thread to safely update the UI state
        viewModelScope.launch {
            _uiState.value = LocationsUiState.Loading

            try {
                // 👇 Shift the heavy lifting to a background thread
                val locations = withContext(Dispatchers.Default) {
                    repository.getNearbyLocations()
                }

                // 🗑️ The println loop is entirely deleted from here

                // Back on the Main thread automatically: update the UI
                _uiState.value = LocationsUiState.Success(locations)

            } catch (e: Exception) {
                _uiState.value = LocationsUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}