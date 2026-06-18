package org.ttproject.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.ttproject.data.Location
import org.ttproject.data.TTReview
import org.ttproject.util.toByteArray
import platform.Foundation.NSData

class IosLocationViewModelHelper(private val viewModel: LocationViewModel) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var uiStateJob: Job? = null
    private var reviewsJob: Job? = null

    fun subscribeUiState(
        onLoading: () -> Unit,
        onSuccess: (List<Location>) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        uiStateJob?.cancel()
        uiStateJob = scope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is LocationsUiState.Loading -> onLoading()
                    is LocationsUiState.Success -> onSuccess(state.locations)
                    is LocationsUiState.Error -> onError(state.message)
                }
            }
        }
        return {
            uiStateJob?.cancel()
        }
    }

    fun subscribeReviews(onCollect: (List<TTReview>) -> Unit): () -> Unit {
        reviewsJob?.cancel()
        reviewsJob = scope.launch {
            viewModel.clubReviews.collect {
                onCollect(it)
            }
        }
        return {
            reviewsJob?.cancel()
        }
    }

    fun fetchNearbyLocations() {
        viewModel.fetchNearbyLocations()
    }

    fun loadReviewsForClub(locationId: String) {
        viewModel.loadReviewsForClub(locationId)
    }

    fun submitNewTable(
        lat: Double,
        lng: Double,
        isIndoor: Boolean,
        count: Int,
        isFree: Boolean,
        imagesData: List<NSData>,
        onSuccess: () -> Unit
    ) {
        val byteImages = imagesData.map { it.toByteArray() }
        viewModel.submitNewTable(lat, lng, isIndoor, count, isFree, byteImages, onSuccess)
    }

    fun submitReview(
        locationId: String,
        tags: List<String>,
        text: String,
        imagesData: List<NSData>,
        onSuccess: () -> Unit
    ) {
        val byteImages = imagesData.map { it.toByteArray() }
        viewModel.submitReview(locationId, tags, text, byteImages, onSuccess)
    }

    fun addLocationImages(
        locationId: String,
        imagesData: List<NSData>,
        onSuccess: () -> Unit
    ) {
        val byteImages = imagesData.map { it.toByteArray() }
        viewModel.addLocationImages(locationId, byteImages, onSuccess)
    }

    fun deleteImage(locationId: String, imageUrl: String, onSuccess: () -> Unit) {
        viewModel.deleteImage(locationId, imageUrl, onSuccess)
    }

    fun reportImage(locationId: String, imageUrl: String, reason: String, onSuccess: () -> Unit) {
        viewModel.reportImage(locationId, imageUrl, reason, onSuccess)
    }

    fun clearData() {
        viewModel.clearData()
    }
}
