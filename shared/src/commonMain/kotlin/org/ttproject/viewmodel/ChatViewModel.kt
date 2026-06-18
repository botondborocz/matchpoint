package org.ttproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ttproject.data.Location
import org.ttproject.data.MessageDto
import org.ttproject.repository.ChatEvent
import org.ttproject.repository.ChatRepository
import org.ttproject.util.NotificationEventBus
import kotlinx.coroutines.flow.update
import org.ttproject.data.ReactionDto
import org.ttproject.data.UserProfile
import org.ttproject.repository.UserRepository
import org.ttproject.util.generateVideoThumbnail
import kotlinx.datetime.Clock
import org.ttproject.data.MessageStatus

class ChatViewModel(
    private val repository: ChatRepository,
    private val userRepository: UserRepository,
    private val tokenStorage: org.ttproject.data.TokenStorage,
    private val connectionId: String,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _otherUserProfile = MutableStateFlow<UserProfile?>(null)
    val otherUserProfile: StateFlow<UserProfile?> = _otherUserProfile.asStateFlow()

    init {
        loadChat()
        repository.triggerPendingSync()

        viewModelScope.launch {
            org.ttproject.util.NotificationEventBus.refreshEvents.collect {
                repository.triggerPendingSync()
                val history = repository.getMessageHistory(connectionId)
                _messages.value = history
            }
        }

        viewModelScope.launch {
            var wasConnected = repository.isConnected()
            while (true) {
                kotlinx.coroutines.delay(2000)
                val currentlyConnected = repository.isConnected()
                if (currentlyConnected && !wasConnected) {
                    repository.triggerPendingSync()
                    val history = repository.getMessageHistory(connectionId)
                    _messages.value = history
                }
                wasConnected = currentlyConnected
            }
        }
    }

    private fun loadChat() {
        viewModelScope.launch {
            // 1. Fetch the history first
            val history = repository.getMessageHistory(connectionId)
            _messages.value = history
            _isLoading.value = false

            // 2. Once history is loaded, open the WebSocket and listen for new ones
            repository.observeLiveMessages(connectionId).collect { event ->
                when (event) {
                    is ChatEvent.Message -> {
                        // Standard message: Add it to the list, preventing duplicates
                        _messages.update { currentList ->
                            if (currentList.any { it.id == event.message.id }) {
                                currentList.map { if (it.id == event.message.id) event.message else it }
                            } else {
                                currentList + event.message
                            }
                        }
                        // If the message is NOT from me, we are actively viewing the screen, so it is read!
                        if (event.message.senderId != tokenStorage.getUserId()) {
                            markMessagesAsRead()
                        }
                    }
                    is ChatEvent.Reaction -> {
                        _messages.update { currentList ->
                            currentList.map { msg ->
                                if (msg.id == event.messageId) {
                                    // Remove their old reaction (if any) and add the new one
                                    val updatedReactions = msg.reactions
                                        .filter { it.userId != event.userId }
                                        .toMutableList()
                                        .apply { add(ReactionDto(event.userId, event.emoji)) }

                                    msg.copy(reactions = updatedReactions)
                                } else msg
                            }
                        }
                    }
                    is ChatEvent.RemoveReaction -> {
                        _messages.update { currentList ->
                            currentList.map { msg ->
                                if (msg.id == event.messageId) {
                                    // Filter out the user's reaction
                                    val updatedReactions = msg.reactions.filter { it.userId != event.userId }
                                    msg.copy(reactions = updatedReactions)
                                } else msg
                            }
                        }
                    }
                    is ChatEvent.Read -> {
                        _messages.update { currentList ->
                            currentList.map { msg ->
                                if (msg.senderId != event.readerId && msg.status != MessageStatus.READ) {
                                    msg.copy(status = MessageStatus.READ)
                                } else msg
                            }
                        }
                    }
                }
            }
        }
    }

    fun sendMessage(text: String, replyToMessageId: String? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            if (!repository.isConnected()) {
                val tempId = "pending_" + Clock.System.now().toEpochMilliseconds()
                val currentUserId = tokenStorage.getUserId() ?: ""
                val pendingMsg = MessageDto(
                    id = tempId,
                    senderId = currentUserId,
                    content = text,
                    createdAt = Clock.System.now().toString(),
                    replyToMessageId = replyToMessageId,
                    reactions = emptyList(),
                    status = MessageStatus.PENDING
                )
                _messages.update { it + pendingMsg }
                repository.queuePendingMessage(
                    connectionId = connectionId,
                    tempId = tempId,
                    text = text,
                    replyToId = replyToMessageId,
                    mediaType = "TEXT",
                    mediaBytes = null,
                    createdAt = pendingMsg.createdAt
                )
            } else {
                repository.sendMessage(text, replyToMessageId)
                NotificationEventBus.triggerRefresh()
            }
        }
    }

    fun sendReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            // Send it to the server.
            // The server will broadcast it back, which will be caught by `observeLiveMessages`
            // and automatically added to the UI!
            repository.sendReaction(messageId, emoji)
            NotificationEventBus.triggerRefresh()
        }
    }

    fun removeReaction(messageId: String) {
        viewModelScope.launch {
            repository.removeReaction(messageId)
            NotificationEventBus.triggerRefresh()
        }
    }

    fun markMessagesAsRead() {
        viewModelScope.launch {
            repository.markMessagesAsRead(connectionId)
            NotificationEventBus.triggerRefresh()
        }
    }

    fun updateChatTheme(connectionId: String, themeName: String) {
        viewModelScope.launch {
            repository.updateChatTheme(connectionId, themeName)
        }
    }

    fun fetchOtherUserProfile(username: String) {
        viewModelScope.launch {
            userRepository.getUserProfile(username).onSuccess { profile ->
                _otherUserProfile.value = profile
            }
        }
    }

    fun sendImagesMessage(connectionId: String, mediaBytes: List<ByteArray>, replyToMessageId: String?) {
        if (mediaBytes.isEmpty()) return

        viewModelScope.launch {
            val images = mutableListOf<ByteArray>()
            val videos = mutableListOf<ByteArray>()

            mediaBytes.forEach { bytes ->
                val isVideo = bytes.size > 8 &&
                        bytes[4].toInt().toChar() == 'f' &&
                        bytes[5].toInt().toChar() == 't' &&
                        bytes[6].toInt().toChar() == 'y' &&
                        bytes[7].toInt().toChar() == 'p'
                if (isVideo) {
                    videos.add(bytes)
                } else {
                    images.add(bytes)
                }
            }

            // Send each video individually
            videos.forEach { videoBytes ->
                sendVideoMessage(connectionId, videoBytes, replyToMessageId)
            }

            // Send images as a grouped collage
            if (images.isNotEmpty()) {
                val tempId = "pending_" + Clock.System.now().toEpochMilliseconds() + "_" + kotlin.random.Random.nextInt(1000, 9999)
                val currentUserId = tokenStorage.getUserId() ?: ""
                val tag = if (images.size == 1) "[IMAGE]" else "[IMAGES]"
                val pendingUrls = images.indices.joinToString(",") { index -> "pending_media_${tempId}_$index" }
                val pendingMsg = MessageDto(
                    id = tempId,
                    senderId = currentUserId,
                    content = "$tag$pendingUrls",
                    createdAt = Clock.System.now().toString(),
                    replyToMessageId = replyToMessageId,
                    reactions = emptyList(),
                    status = MessageStatus.PENDING
                )
                _messages.update { it + pendingMsg }
                repository.queuePendingMessage(
                    connectionId = connectionId,
                    tempId = tempId,
                    text = "$tag$pendingUrls",
                    replyToId = replyToMessageId,
                    mediaType = "IMAGE",
                    mediaBytes = images,
                    createdAt = pendingMsg.createdAt
                )
            }

            if (repository.isConnected()) {
                repository.triggerPendingSync()
            }
        }
    }

    fun sendVideoMessage(connectionId: String, videoBytes: ByteArray, replyToMessageId: String?) {
        viewModelScope.launch {
            val tempId = "pending_" + Clock.System.now().toEpochMilliseconds() + "_" + kotlin.random.Random.nextInt(1000, 9999)
            val currentUserId = tokenStorage.getUserId() ?: ""
            val thumbnailBytes = generateVideoThumbnail(videoBytes)
            val contentPayload = if (thumbnailBytes != null) {
                "[VIDEO]pending_media_${tempId}_0,pending_media_${tempId}_1"
            } else {
                "[VIDEO]pending_media_${tempId}_0"
            }
            val pendingMsg = MessageDto(
                id = tempId,
                senderId = currentUserId,
                content = contentPayload,
                createdAt = Clock.System.now().toString(),
                replyToMessageId = replyToMessageId,
                reactions = emptyList(),
                status = MessageStatus.PENDING
            )
            _messages.update { it + pendingMsg }
            val filesToSave = if (thumbnailBytes != null) {
                listOf(thumbnailBytes, videoBytes)
            } else {
                listOf(videoBytes)
            }
            repository.queuePendingMessage(
                connectionId = connectionId,
                tempId = tempId,
                text = contentPayload,
                replyToId = replyToMessageId,
                mediaType = "VIDEO",
                mediaBytes = filesToSave,
                createdAt = pendingMsg.createdAt
            )

            if (repository.isConnected()) {
                repository.triggerPendingSync()
            }
        }
    }

    fun sendVoiceMessage(connectionId: String, audioBytes: ByteArray, replyToMessageId: String?) {
        viewModelScope.launch {
            if (!repository.isConnected()) {
                val tempId = "pending_" + Clock.System.now().toEpochMilliseconds()
                val currentUserId = tokenStorage.getUserId() ?: ""
                val pendingMsg = MessageDto(
                    id = tempId,
                    senderId = currentUserId,
                    content = "[VOICE]pending_media_${tempId}_0",
                    createdAt = Clock.System.now().toString(),
                    replyToMessageId = replyToMessageId,
                    reactions = emptyList(),
                    status = MessageStatus.PENDING
                )
                _messages.update { it + pendingMsg }
                repository.queuePendingMessage(
                    connectionId = connectionId,
                    tempId = tempId,
                    text = "[VOICE]pending_media_${tempId}_0",
                    replyToId = replyToMessageId,
                    mediaType = "VOICE",
                    mediaBytes = listOf(audioBytes),
                    createdAt = pendingMsg.createdAt
                )
                return@launch
            }

            // Upload to a dedicated voice endpoint (or reuse the images one)
            repository.uploadAudioMessage(connectionId, audioBytes).onSuccess { url ->
                val payload = "[VOICE]$url"
                repository.sendMessage(payload, replyToMessageId)
            }.onFailure {
                it.printStackTrace()
            }
        }
    }
}