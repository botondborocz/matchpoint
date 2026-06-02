package org.ttproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ttproject.data.Player
import org.ttproject.repository.MatchRepository
import org.ttproject.util.NotificationEventBus

sealed class MatchUiState {
    data object Loading : MatchUiState()
    data class Success(val players: List<Player>) : MatchUiState()
    data class Error(val message: String) : MatchUiState()
}

class MatchViewModel(
    private val repository: MatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchUiState>(MatchUiState.Loading)
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private val _matchedPlayer = MutableStateFlow<Player?>(null)
    val matchedPlayer: StateFlow<Player?> = _matchedPlayer.asStateFlow()

    // 👈 NEW PREMIUM STATE PARAMETERS
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private var lastSwipedPlayer: Player? = null

    init {
        loadPlayers()
    }

    fun loadPlayers() {
        viewModelScope.launch {
            _uiState.value = MatchUiState.Loading
            try {
                val players = repository.getNearbyPlayers()
                _uiState.value = MatchUiState.Success(players)
            } catch (e: Exception) {
                _uiState.value = MatchUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onPlayerSwiped(player: Player, isLiked: Boolean) {
        // Save swipe context state variables locally before updating the stack array
        lastSwipedPlayer = player
        _canUndo.value = player.isPremium // 👈 Only premium users get to activate this tracking hook

        _uiState.update { currentState ->
            if (currentState is MatchUiState.Success) {
                MatchUiState.Success(currentState.players.filterNot { it.id == player.id })
            } else currentState
        }

        viewModelScope.launch {
            val isMatch = repository.recordSwipeAction(player.id, isLiked)
            if (isMatch) {
                _matchedPlayer.value = player
            }
        }
    }

    // 👈 NEW: Command link to pull the target record straight back onto the workspace stack array
    fun undoLastSwipe() {
        val playerToRestore = lastSwipedPlayer ?: return
        _canUndo.value = false
        lastSwipedPlayer = null

        viewModelScope.launch {
            _uiState.value = MatchUiState.Loading
            try {
                val success = repository.undoSwipeAction(playerToRestore.id)
                if (success) {
                    // Refetch or manually push back to head position safely
                    val currentPlayers = repository.getNearbyPlayers().toMutableList()
                    if (!currentPlayers.any { it.id == playerToRestore.id }) {
                        currentPlayers.add(0, playerToRestore)
                    }
                    _uiState.value = MatchUiState.Success(currentPlayers)
                } else {
                    loadPlayers()
                }
            } catch (e: Exception) {
                _uiState.value = MatchUiState.Error(e.message ?: "Undo action failed")
            }
        }
    }

    fun dismissMatchPopup() {
        _matchedPlayer.value = null
        NotificationEventBus.triggerRefresh()
    }

    // Add these variables and functions inside your MatchViewModel class:

    private val _isShowingLikesTab = MutableStateFlow(false)
    val isShowingLikesTab: StateFlow<Boolean> = _isShowingLikesTab.asStateFlow()

    private val _likedMePlayers = MutableStateFlow<List<Player>>(emptyList())
    val likedMePlayers: StateFlow<List<Player>> = _likedMePlayers.asStateFlow()

    fun toggleTab(showLikes: Boolean) {
        _isShowingLikesTab.value = showLikes
        if (showLikes) {
            loadLikesFeed()
        } else {
            loadPlayers() // Reload the regular swipe card stack
        }
    }

    fun loadLikesFeed() {
        viewModelScope.launch {
            try {
                _likedMePlayers.value = repository.getPeopleWhoLikedMe()
            } catch (e: Exception) {
                _likedMePlayers.value = emptyList()
            }
        }
    }
}