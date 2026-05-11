package org.ttproject.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.ttproject.data.IosTokenStorage
import org.ttproject.data.TokenStorage
import org.ttproject.icon.AppIconManager
import org.ttproject.icon.IosAppIconManager

actual val platformModule: Module = module {
    single<TokenStorage> { IosTokenStorage() }
    single<AppIconManager> { IosAppIconManager() }
}