package org.ttproject.di

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.ttproject.viewmodel.LocationViewModel
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

    fun getTokenStorage(): TokenStorage {
        return getKoin().get()
    }
}
