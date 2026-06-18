package org.ttproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ttproject.data.ChatThreadDto
import org.ttproject.repository.ChatRepository
import org.ttproject.util.NotificationEventBus

class MessagesViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _threads = MutableStateFlow<List<ChatThreadDto>>(emptyList())
//    val threads: StateFlow<List<ChatThreadDto>> = _threads.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredThreads = combine(_threads, _searchQuery) { threads, query ->
        if (query.isBlank()) {
            threads
        } else {
            threads.filter { it.otherUserName.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Load data when the ViewModel is first created
        loadConnections()

        // 👇 THE FIX: Listen for FCM background pings!
        viewModelScope.launch {
            NotificationEventBus.refreshEvents.collect {
                // Whenever FCM receives a message, silently reload the list in the background
                loadConnections(isBackgroundRefresh = true)
            }
        }
    }

    // Add a flag to prevent the spinner from flashing during silent updates
    fun loadConnections(isBackgroundRefresh: Boolean = false) {
        viewModelScope.launch {
            // Only show the loading state if we have absolutely no data
            // AND it's not a silent background refresh
            if (_threads.value.isEmpty() && !isBackgroundRefresh) {
                _isLoading.value = true
            }

            try {
                _threads.value = repository.getConnections()
            } finally {
                // Always ensure loading is turned off, even if the network fails
                _isLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearData() {
        _threads.value = emptyList()
        _isLoading.value = true
        _searchQuery.value = ""
    }

    fun savePushToken(fcmToken: String) {
        viewModelScope.launch {
            repository.savePushToken(fcmToken)
        }
    }

    fun markMessagesAsRead(chatId: String) {
        viewModelScope.launch {
            repository.markMessagesAsRead(chatId)
        }
    }
}