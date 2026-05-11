package org.ttproject.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.ttproject.data.IosTokenStorage
import org.ttproject.data.TokenStorage

actual val platformModule: Module = module {
    single<TokenStorage> { IosTokenStorage() }
    single<AppIconManager> { IosAppIconManager() }
}