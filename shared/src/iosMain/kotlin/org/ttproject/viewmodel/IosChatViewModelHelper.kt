package org.ttproject.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ttproject.data.MessageDto
import org.ttproject.data.UserProfile

class IosChatViewModelHelper(
    private val viewModel: ChatViewModel
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun subscribeMessages(onCollect: (List<MessageDto>) -> Unit) {
        viewModel.messages.onEach { onCollect(it) }.launchIn(scope)
    }

    fun subscribeIsLoading(onCollect: (Boolean) -> Unit) {
        viewModel.isLoading.onEach { onCollect(it) }.launchIn(scope)
    }

    fun subscribeOtherUserProfile(onCollect: (UserProfile?) -> Unit) {
        viewModel.otherUserProfile.onEach { onCollect(it) }.launchIn(scope)
    }

    fun sendMessage(text: String, replyToMessageId: String? = null) {
        viewModel.sendMessage(text, replyToMessageId)
    }

    fun sendReaction(messageId: String, emoji: String) {
        viewModel.sendReaction(messageId, emoji)
    }

    fun removeReaction(messageId: String) {
        viewModel.removeReaction(messageId)
    }

    fun markMessagesAsRead() {
        viewModel.markMessagesAsRead()
    }

    fun fetchOtherUserProfile(username: String) {
        viewModel.fetchOtherUserProfile(username)
    }

    fun sendImagesMessage(connectionId: String, mediaBytes: List<ByteArray>, replyToMessageId: String?) {
        viewModel.sendImagesMessage(connectionId, mediaBytes, replyToMessageId)
    }

    fun clear() {
        scope.cancel()
    }
}
