package org.ttproject.di

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import org.ttproject.viewmodel.LocationViewModel
import org.ttproject.viewmodel.MessagesViewModel
import org.ttproject.viewmodel.ChatViewModel
import org.ttproject.data.TokenStorage

object KoinHelper : KoinComponent {
    private var isInitialized = false

    fun safeInitKoin() {
        if (!isInitialized) {
            initKoin()
            isInitialized = true
        }
    }

    fun getLocationViewModel(): LocationViewModel {
        return getKoin().get()
    }

    fun getMessagesViewModel(): MessagesViewModel {
        return getKoin().get()
    }

    fun getChatViewModel(connectionId: String): ChatViewModel {
        return getKoin().get(parameters = { parametersOf(connectionId) })
    }

    fun getTokenStorage(): TokenStorage {
        return getKoin().get()
    }
}
