package org.ttproject.routes

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import org.ttproject.database.tables.SkillLevel
import org.ttproject.database.tables.Users
import org.ttproject.security.JwtConfig
import java.util.UUID
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Data classes for Ktor to parse incoming JSON
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val username: String? = null, val fullName: String? = null)
data class GoogleAuthRequest(val idToken: String)
data class SupabaseAuthRequest(val supabaseToken: String)

fun Route.authRoutes() {
    route("/api/auth") {
        // 1. BASIC REGISTER
        post("/register") {
            val req = call.receive<RegisterRequest>()

            // 1. Check if the user already exists!
            val existingUser = transaction {
                Users.select { Users.email eq req.email }.singleOrNull()
            }

            if (existingUser != null) {
                call.respondText("Email already in use", status = HttpStatusCode.Conflict)
                return@post
            }

            // 2. Hash the password
            val hashedPw = BCrypt.hashpw(req.password, BCrypt.gensalt())

            // 3. Insert the user safely
            val newUserId = transaction {
                Users.insert {
                    it[email] = req.email
                    it[passwordHash] = hashedPw

                    // If they didn't send a username, generate a random one just like we did for Google!
                    it[username] =
                        req.username ?: (req.email.substringBefore("@") + "_" + UUID.randomUUID().toString().take(4))
                    it[fullName] = req.fullName ?: "test" // This will safely insert null if they didn't send it
                    it[skillLevel] = SkillLevel.Beginner
                    it[createdAt] = java.time.Instant.now()
                } get Users.id
            }

            // Success!
            // Generate the token using the user's UUID
            val token = JwtConfig.generateToken(newUserId.toString())

            // Send it back to Android as a JSON response!
            call.respondText(
                """{"token": "$token"}""",
                io.ktor.http.ContentType.Application.Json
            )
        }

        // 2. BASIC LOGIN
        post("/login") {
            val req = call.receive<LoginRequest>()

            val userRow = transaction {
                Users.select { Users.email eq req.email }.singleOrNull()
            }

            if (userRow == null) {
                call.respondText("User not found", status = io.ktor.http.HttpStatusCode.Unauthorized)
                return@post
            }

            val savedHash = userRow[Users.passwordHash]
            if (savedHash != null && BCrypt.checkpw(req.password, savedHash)) {
                val userId = userRow[Users.id]
                // Generate the token using the user's UUID
                val token = JwtConfig.generateToken(userId.toString())

                // Send it back to Android as a JSON response!
                call.respondText(
                    """{"token": "$token"}""",
                    io.ktor.http.ContentType.Application.Json
                )
            } else {
                call.respondText("Invalid password", status = io.ktor.http.HttpStatusCode.Unauthorized)
            }
        }

        // 1. Load the .env file
        // (ignoreIfMissing = true prevents crashes in production where Docker handles envs instead)
        val dotenv = dotenv {
            ignoreIfMissing = true
        }

        // 2. Grab your Google ID! (Falls back to System Env if .env file is missing)

        val googleWebClientId = dotenv["GOOGLE_WEB_CLIENT_ID"]
            ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
            ?: throw IllegalStateException("Google Client ID is missing!")

        val googleIosClientId = dotenv["GOOGLE_IOS_CLIENT_ID"]
            ?: System.getenv("GOOGLE_IOS_CLIENT_ID")
            ?: throw IllegalStateException("Google Client ID is missing!")


        // Put this outside your route so it isn't recreated on every request
        val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory())
            .setAudience(listOf(
                googleWebClientId,
                googleIosClientId
            ))
            .build()

        post("/google") {
            // 1. Safely parse the JSON body
            val req = try {
                call.receive<GoogleAuthRequest>()
            } catch (e: Exception) {
                return@post call.respondText("Invalid request body", status = HttpStatusCode.BadRequest)
            }

            if (req.idToken.isBlank()) {
                return@post call.respondText("Token is empty", status = HttpStatusCode.BadRequest)
            }

            try {
                // 2. VERIFY THE ID TOKEN LOCALLY (No network request to Google needed!)
                val googleIdToken = verifier.verify(req.idToken)

                if (googleIdToken != null) {
                    val payload = googleIdToken.payload
                    val googleUserId = payload.subject // This is the "sub" (Google ID)
                    val googleEmail = payload.email
                    val googleName = payload["name"] as? String ?: ""

                    // 3. Database logic
                    val userId = transaction {
                        val existingUser = Users.select { Users.googleId eq googleUserId }.singleOrNull()

                        if (existingUser != null) {
                            existingUser[Users.id]
                        } else {
                            Users.insert {
                                it[email] = googleEmail
                                it[googleId] = googleUserId
                                it[fullName] = googleName
                                it[username] = "user_${java.util.UUID.randomUUID().toString().take(8)}"
                                it[skillLevel] = SkillLevel.Beginner
                                it[createdAt] = java.time.Instant.now()
                            } get Users.id
                        }
                    }

                    // 4. Generate your custom JWT and send it back
                    val token = JwtConfig.generateToken(userId.toString())
                    call.respondText("""{"token": "$token"}""", ContentType.Application.Json)

                } else {
                    call.respondText("Invalid Google ID Token", status = HttpStatusCode.Unauthorized)
                }
            } catch (e: Exception) {
                call.respondText("Malformed token format", status = HttpStatusCode.BadRequest)
            }
        }

        post("/login-supabase") {
            val req = try {
                call.receive<SupabaseAuthRequest>()
            } catch (e: Exception) {
                return@post call.respondText("Invalid request body", status = HttpStatusCode.BadRequest)
            }

            if (req.supabaseToken.isBlank()) {
                return@post call.respondText("Token is empty", status = HttpStatusCode.BadRequest)
            }

            val dotenv = dotenv {
                ignoreIfMissing = true
            }
            val supabaseUrl = dotenv["SUPABASE_URL"] ?: System.getenv("SUPABASE_URL")
            val supabaseAnonKey = dotenv["SUPABASE_ANON_KEY"] ?: System.getenv("SUPABASE_ANON_KEY")

            try {
                // Verify with Supabase Auth API
                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$supabaseUrl/auth/v1/user"))
                    .header("Authorization", "Bearer ${req.supabaseToken}")
                    .header("apikey", supabaseAnonKey)
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 200) {
                    val body = response.body()
                    val json = Json.parseToJsonElement(body).jsonObject
                    val idStr = json["id"]?.jsonPrimitive?.content ?: throw Exception("ID not found in Supabase response")
                    val emailStr = json["email"]?.jsonPrimitive?.content ?: throw Exception("Email not found in Supabase response")
                    val userUuid = UUID.fromString(idStr)

                    transaction {
                        val existingUser = Users.select { Users.id eq userUuid }.singleOrNull()
                        if (existingUser == null) {
                            Users.insert {
                                it[id] = userUuid
                                it[email] = emailStr
                                it[username] = emailStr.substringBefore("@") + "_" + UUID.randomUUID().toString().take(4)
                                it[fullName] = "Player"
                                it[skillLevel] = SkillLevel.Beginner
                                it[createdAt] = java.time.Instant.now()
                            }
                        }
                    }

                    val token = JwtConfig.generateToken(userUuid.toString())
                    call.respondText("""{"token": "$token"}""", ContentType.Application.Json)
                } else {
                    call.respondText("Invalid Supabase Token: ${response.body()}", status = HttpStatusCode.Unauthorized)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText("Verification failed: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}