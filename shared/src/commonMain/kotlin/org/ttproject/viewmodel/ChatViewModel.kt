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

class ChatViewModel(
    private val repository: ChatRepository,
    private val userRepository: UserRepository,
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
                        // Standard message: Add it to the top of the list
                        _messages.update { currentList ->
                            currentList + event.message
                        }                    }
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
                }
            }
        }
    }

    fun sendMessage(text: String, replyToMessageId: String? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Send it to the server.
            // The server will broadcast it back, which will be caught by `observeLiveMessages`
            // and automatically added to the UI!
            repository.sendMessage(text, replyToMessageId)
            NotificationEventBus.triggerRefresh()
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

    // 👇 Accepts a List of ByteArrays
    // 👇 Accepts a List of ByteArrays from the Gallery Picker
    fun sendImagesMessage(connectionId: String, mediaBytes: List<ByteArray>, replyToMessageId: String?) {
        if (mediaBytes.isEmpty()) return

        viewModelScope.launch {
            // TODO: Optional _isUploading.value = true

            val imageBytesList = mutableListOf<ByteArray>()

            // 1. Sort the incoming files using Magic Bytes
            mediaBytes.forEach { bytes ->

                val isVideo = bytes.size > 8 &&
                        bytes[4].toInt().toChar() == 'f' &&
                        bytes[5].toInt().toChar() == 't' &&
                        bytes[6].toInt().toChar() == 'y' &&
                        bytes[7].toInt().toChar() == 'p'

                if (isVideo) {
                    // 2. It's a video! Route it to our dedicated video pipeline
                    // so it generates a local thumbnail and uploads properly.
                    sendVideoMessage(connectionId, bytes, replyToMessageId)
                } else {
                    // 3. It's an image! Queue it up for the bulk collage upload.
                    imageBytesList.add(bytes)
                }
            }

            // 4. Upload all remaining true images as a grouped collage message
            if (imageBytesList.isNotEmpty()) {
                repository.uploadChatImages(connectionId, imageBytesList).onSuccess { urls ->
                    val tag = if (urls.size == 1) "[IMAGE]" else "[IMAGES]"
                    val joinedUrls = urls.joinToString(",")
                    repository.sendMessage("$tag$joinedUrls", replyToMessageId)
                }.onFailure {
                    it.printStackTrace()
                }
            }

            // TODO: Optional _isUploading.value = false
        }
    }

    fun sendVideoMessage(connectionId: String, videoBytes: ByteArray, replyToMessageId: String?) {
        viewModelScope.launch {
            // TODO: Set _isUploading = true here

            // 1. Generate the thumbnail locally
            val thumbnailBytes = generateVideoThumbnail(videoBytes)

            println("📸 THUMBNAIL DEBUG: ${if (thumbnailBytes != null) "✅ SUCCESS! Extracted ${thumbnailBytes.size / 1024} KB JPEG" else "❌ FAILED! Extractor returned null"}")

            // 2. Prepare the list of files to upload
            val filesToUpload = if (thumbnailBytes != null) {
                listOf(thumbnailBytes, videoBytes) // Upload both!
            } else {
                listOf(videoBytes) // Fallback if extraction fails
            }

            // 3. Upload them simultaneously
            repository.uploadChatImages(connectionId, filesToUpload).onSuccess { urls ->

                // 4. Extract the URLs from the backend response
                val videoUrl = urls.find { it.contains(".mp4") } ?: ""
                val thumbUrl = urls.find { it.contains(".jpg") || it.contains(".jpeg") } ?: ""

                // 5. Blast the formatted string to the WebSocket!
                val payload = if (thumbUrl.isNotBlank()) {
                    "[VIDEO]$thumbUrl,$videoUrl"
                } else {
                    "[VIDEO]$videoUrl" // Fallback UI will just show a black box with a play button
                }

                repository.sendMessage(payload, replyToMessageId)

            }.onFailure {
                it.printStackTrace()
            }

            // TODO: Set _isUploading = false here
        }
    }

    fun sendVoiceMessage(connectionId: String, audioBytes: ByteArray, replyToMessageId: String?) {
        viewModelScope.launch {
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