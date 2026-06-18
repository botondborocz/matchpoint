package org.ttproject.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.ttproject.data.IosTokenStorage
import org.ttproject.data.TokenStorage
import org.ttproject.icon.AppIconManager
import org.ttproject.icon.IosAppIconManager
import org.ttproject.database.LocationDatabase
import org.ttproject.database.IosLocationDatabase
import org.ttproject.database.PlayerDatabase
import org.ttproject.database.IosPlayerDatabase
import org.ttproject.database.ChatDatabase
import org.ttproject.database.IosChatDatabase
import org.ttproject.util.ConnectivityChecker
import org.ttproject.util.IosConnectivityChecker

actual val platformModule: Module = module {
    single<TokenStorage> { IosTokenStorage() }
    single<AppIconManager> { IosAppIconManager() }
    single<LocationDatabase> { IosLocationDatabase() }
    single<PlayerDatabase> { IosPlayerDatabase() }
    single<ConnectivityChecker> { IosConnectivityChecker() }
    single<ChatDatabase> { IosChatDatabase() }
}