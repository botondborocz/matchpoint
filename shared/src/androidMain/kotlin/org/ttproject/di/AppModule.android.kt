package org.ttproject.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.ttproject.AndroidAppIconManager
import org.ttproject.data.AndroidTokenStorage
import org.ttproject.data.TokenStorage
import org.ttproject.icon.AppIconManager

actual val platformModule: Module = module {
    // The get() here magically grabs the Android Context we passed to Koin inside MatchApplication.kt!
    single<TokenStorage> { AndroidTokenStorage(context = get()) }
    single<AppIconManager> { AndroidAppIconManager(get()) }
}