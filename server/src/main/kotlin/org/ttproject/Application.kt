package org.ttproject

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.ttproject.database.initDatabase
import org.ttproject.database.tables.Locations.name
import org.ttproject.database.tables.Users
import org.ttproject.routes.authRoutes
import org.ttproject.security.JwtConfig
import kotlin.collections.map
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.auth.parseAuthorizationHeader
import org.ttproject.routes.locationRoutes
import org.ttproject.routes.userRoutes
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import org.ttproject.routes.badgeRouting
import org.ttproject.routes.messageRoutes
import org.ttproject.services.BadgeService
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun main() {
    val dotenv = dotenv {
        ignoreIfMissing = true
    }
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // Initialize Firebase Admin SDK
    try {
        if (FirebaseApp.getApps().isEmpty()) {
            // Read the file directly from the Ktor resources folder
            val serviceAccount = Thread.currentThread().contextClassLoader.getResourceAsStream("firebase-adminsdk.json")
                ?: throw Exception("firebase-adminsdk.json not found in resources folder!")

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setStorageBucket("match-79922.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(options)
            println("✅ Firebase Admin SDK initialized successfully!")
        }
    } catch (e: Exception) {
        println("❌ Failed to initialize Firebase: ${e.message}")
    }

    initDatabase()

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    // 1. Install the Authentication plugin
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Match App Backend"
            verifier(JwtConfig.verifier)
            validate { credential ->
                // If the token has a userId claim, let them in!
                if (credential.payload.getClaim("userId").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else null
            }
            authHeader { call ->
                // 1. Try to get the standard Bearer token from headers (Your Mobile App)
                val headerToken = call.request.parseAuthorizationHeader()
                if (headerToken != null) {
                    return@authHeader headerToken
                }

                // 2. Fallback to the URL query parameter (Your React Web App)
                val queryToken = call.request.queryParameters["token"]
                if (queryToken != null) {
                    return@authHeader HttpAuthHeader.Single("Bearer", queryToken)
                }

                // 3. No token found anywhere, reject the request
                null
            }
        }
    }

    val badgeService = BadgeService()

    routing {
        authRoutes()
        userRoutes(badgeService)
        locationRoutes()
        messageRoutes(badgeService)
        badgeRouting(badgeService)

        get("/") {
            call.respondText("Ktor Server is Online!")
        }
        post("/users") {
            val newName = call.receiveText()

            transaction {
                Users.insert {
                    it[username] = newName
                }
            }
            call.respondText("User '$newName' saved to the database!")
        }

        // 2. The Security Checkpoint! Everything inside here requires a valid token.
        authenticate("auth-jwt") {

            get("/users") {
                // If they made it here, their token is 100% valid.
                // We can even extract their ID from the token like this:
                val principal = call.principal<JWTPrincipal>()
                val myUserId = principal!!.payload.getClaim("userId").asString()

                call.respondText("Welcome to the VIP area! Your ID is: $myUserId")
            }
        }
    }
}