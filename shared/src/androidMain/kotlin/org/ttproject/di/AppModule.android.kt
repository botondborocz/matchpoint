package org.ttproject.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.ttproject.icon.AndroidAppIconManager
import org.ttproject.data.AndroidTokenStorage
import org.ttproject.data.TokenStorage
import org.ttproject.icon.AppIconManager
import org.ttproject.database.LocationDatabase
import org.ttproject.database.AndroidLocationDatabase
import org.ttproject.database.PlayerDatabase
import org.ttproject.database.AndroidPlayerDatabase
import org.ttproject.database.ChatDatabase
import org.ttproject.database.AndroidChatDatabase
import org.ttproject.util.ConnectivityChecker
import org.ttproject.util.AndroidConnectivityChecker

actual val platformModule: Module = module {
    // The get() here magically grabs the Android Context we passed to Koin inside MatchApplication.kt!
    single<TokenStorage> { AndroidTokenStorage(context = get()) }
    single<AppIconManager> { AndroidAppIconManager(get()) }
    single<LocationDatabase> { AndroidLocationDatabase(context = get()) }
    single<PlayerDatabase> { AndroidPlayerDatabase(context = get()) }
    single<ConnectivityChecker> { AndroidConnectivityChecker(context = get()) }
    single<ChatDatabase> { AndroidChatDatabase(context = get()) }
}