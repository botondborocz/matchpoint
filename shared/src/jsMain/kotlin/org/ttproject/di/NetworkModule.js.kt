package org.ttproject.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun provideHttpClientEngine(): HttpClientEngine {
    return Js.create()
}
