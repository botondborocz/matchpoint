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
import org.ttproject.data.AddReviewRequest
import org.ttproject.data.AddTableRequest
import org.ttproject.data.TTReview
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
        viewModelScope.launch {
            _uiState.value = LocationsUiState.Loading
            try {
                val locations = withContext(Dispatchers.Default) {
                    repository.getNearbyLocations()
                }
                _uiState.value = LocationsUiState.Success(locations)
            } catch (e: Exception) {
                _uiState.value = LocationsUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun clearData() {
        _uiState.value = LocationsUiState.Loading
        _clubReviews.value = emptyList()
    }

    fun submitNewTable(
        lat: Double,
        lng: Double,
        isIndoor: Boolean,
        count: Int,
        isFree: Boolean,
        images: List<ByteArray>, // 👇 ADDED
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val request = AddTableRequest(
                latitude = lat,
                longitude = lng,
                type = if (isIndoor) "Indoor" else "Outdoor",
                tableCount = count,
                isFree = isFree,
                images = images // 👇 ADDED
            )

            val result = repository.addTable(request)

            if (result.isSuccess) {
                fetchNearbyLocations()
                onSuccess()
            } else {
                println("Error adding table: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private val _clubReviews = MutableStateFlow<List<TTReview>>(emptyList())
    val clubReviews = _clubReviews.asStateFlow()

    fun loadReviewsForClub(locationId: String) {
        viewModelScope.launch {
            _clubReviews.value = emptyList()
            val result = repository.getReviews(locationId)
            if (result.isSuccess) {
                _clubReviews.value = result.getOrNull() ?: emptyList()
            }
        }
    }

    fun submitReview(
        locationId: String,
        tags: List<String>,
        text: String,
        images: List<ByteArray>, // 👇 ADDED
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val request = AddReviewRequest(text, tags, images) // 👇 ADDED
            val result = repository.addReview(locationId, request)
            if (result.isSuccess) {
                loadReviewsForClub(locationId)
                onSuccess()
            }
        }
    }

    fun addLocationImages(locationId: String, images: List<ByteArray>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.addLocationImages(locationId, images)
            if (result.isSuccess) {
                // Refresh the master list so the new images are available globally!
                fetchNearbyLocations()
                onSuccess()
            } else {
                println("Error adding standalone images: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteImage(locationId: String, imageUrl: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteImage(locationId, imageUrl)
            if (result.isSuccess) {
                // Refresh BOTH lists since the image could have belonged to the club itself or a review!
                fetchNearbyLocations()
                loadReviewsForClub(locationId)
                onSuccess()
            } else {
                println("Error deleting image: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun reportImage(locationId: String, imageUrl: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.reportImage(locationId, imageUrl, reason)
            if (result.isSuccess) {
                onSuccess()
            } else {
                println("Error reporting image: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}