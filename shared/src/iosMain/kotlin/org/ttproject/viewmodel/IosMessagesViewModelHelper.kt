package org.ttproject.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ttproject.data.ChatThreadDto

class IosMessagesViewModelHelper(
    private val viewModel: MessagesViewModel
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun subscribeThreads(onCollect: (List<ChatThreadDto>) -> Unit) {
        viewModel.filteredThreads.onEach { onCollect(it) }.launchIn(scope)
    }

    fun subscribeIsLoading(onCollect: (Boolean) -> Unit) {
        viewModel.isLoading.onEach { onCollect(it) }.launchIn(scope)
    }

    fun subscribeSearchQuery(onCollect: (String) -> Unit) {
        viewModel.searchQuery.onEach { onCollect(it) }.launchIn(scope)
    }

    fun updateSearchQuery(query: String) {
        viewModel.updateSearchQuery(query)
    }

    fun loadConnections(isBackgroundRefresh: Boolean = false) {
        viewModel.loadConnections(isBackgroundRefresh)
    }

    fun clearData() {
        viewModel.clearData()
    }

    fun markMessagesAsRead(chatId: String) {
        viewModel.markMessagesAsRead(chatId)
    }

    fun clear() {
        scope.cancel()
    }
}
