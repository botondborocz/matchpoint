package org.ttproject.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun provideHttpClientEngine(): HttpClientEngine

fun createHttpClient(engine: HttpClientEngine): HttpClient {
    return HttpClient(engine) {
        // 1. Install the JSON parser
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // Crucial so it doesn't crash if the backend sends extra data
                coerceInputValues = true // Safely defaults nulls if the types mismatch
            })
        }
        install(WebSockets)
    }
}