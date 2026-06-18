package org.ttproject.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.ttproject.repository.AuthRepository
import org.ttproject.repository.AuthRepositoryImpl
import org.ttproject.repository.ChatRepository
import org.ttproject.repository.ChatRepositoryImpl
import org.ttproject.repository.LocationRepository
import org.ttproject.repository.LocationRepositoryImpl
import org.ttproject.repository.MatchRepository
import org.ttproject.repository.MatchRepositoryImpl
import org.ttproject.repository.UserRepository
import org.ttproject.repository.UserRepositoryImpl
import org.ttproject.database.ChatDatabase
import org.ttproject.util.ConnectivityChecker
import org.ttproject.viewmodel.ChatViewModel
import org.ttproject.viewmodel.LocationViewModel
import org.ttproject.viewmodel.LoginViewModel
import org.ttproject.viewmodel.MatchViewModel
import org.ttproject.viewmodel.MessagesViewModel
import org.ttproject.viewmodel.ProfileViewModel

expect val platformModule: Module

val appModule = module {
    // Loads the Android or iOS specific dependencies!
    includes(platformModule)

    single { createHttpClient(provideHttpClientEngine()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<MatchRepository> { MatchRepositoryImpl(get(), get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get(), get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { MatchViewModel(get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { LocationViewModel(get()) }
    viewModel { params -> ChatViewModel(get(), get(), get(), params.get()) }
    viewModel { MessagesViewModel(get()) }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}