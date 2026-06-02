package org.ttproject.routes

import com.google.firebase.cloud.StorageClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.ttproject.database.tables.Users
import org.ttproject.utils.calculateDistanceKm
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.ttproject.data.PlayerResponse
import org.ttproject.data.ReportedMediaDto
import org.ttproject.data.SwipeRequest
import org.ttproject.data.SwipeResponse
import org.ttproject.data.TokenResponse
import org.ttproject.data.UpdateLanguageRequest
import org.ttproject.data.UpdateProfileRequest
import org.ttproject.data.UserProfile
import org.ttproject.database.tables.Locations
import org.ttproject.database.tables.ReportedMedia
import org.ttproject.database.tables.Reviews
import org.ttproject.database.tables.Swipes
import org.ttproject.database.tables.UserBadgeMetrics
import org.ttproject.services.BadgeService
import org.ttproject.services.MatchService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.Period
import java.util.UUID

fun Route.userRoutes(badgeService: BadgeService) {
    // We put this inside the JWT block so only logged-in users can see other players
    authenticate("auth-jwt") {
        route("/api/users") {
            get("/nearby") {
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal?.payload?.getClaim("userId")?.asString()

                if (currentUserId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                    return@get
                }

                val currentUserUuid = UUID.fromString(currentUserId)

                // Check if the requesting user is premium right from our base context
                val isCurrentUserPremium = transaction {
                    Users.selectAll().where { Users.id eq currentUserUuid }
                        .singleOrNull()?.get(Users.isPremium) == true // 👈 (Assumes your schema holds an isPremium col)
                }

                val userLat = call.request.queryParameters["lat"]?.toDoubleOrNull()
                val userLng = call.request.queryParameters["lng"]?.toDoubleOrNull()

                // Query outbound targets safely
                val allPlayers = transaction {
                    Users.leftJoin(UserBadgeMetrics)
                        .selectAll()
                        .map { row ->
                            val targetId = row[Users.id].toString()

                            // Check if this specific target user has swiped current user right
                            val hasSwipedMe = Swipes.selectAll().where {
                                (Swipes.swiperId eq UUID.fromString(targetId)) and
                                        (Swipes.swipedId eq currentUserUuid) and
                                        (Swipes.isLiked eq true)
                            }.count() > 0

                            val metricsDto = if (row.getOrNull(UserBadgeMetrics.userId) != null) {
                                org.ttproject.data.UserBadgeMetricsDto(
                                    addedTables = row[UserBadgeMetrics.addedTables],
                                    uploadedPhotos = row[UserBadgeMetrics.uploadedPhotos],
                                    writtenReviews = row[UserBadgeMetrics.writtenReviews],
                                    successfulMatches = row[UserBadgeMetrics.successfulMatches],
                                    sentMessages = row[UserBadgeMetrics.sentMessages],
                                    profileSwipes = row[UserBadgeMetrics.profileSwipes],
                                    trimmedVideos = row[UserBadgeMetrics.trimmedVideos],
                                    aiQuestions = row[UserBadgeMetrics.aiQuestions],
                                    currentStreak = row[UserBadgeMetrics.currentStreak],
                                    maxStreak = row[UserBadgeMetrics.maxStreak],
                                    invitedFriends = row[UserBadgeMetrics.invitedFriends]
                                )
                            } else null

                            PlayerResponse(
                                id = targetId,
                                username = row[Users.username] ?: "Player",
                                skillLevel = row[Users.skillLevel]?.name ?: "Beginner",
                                lat = row[Users.lastLat],
                                lng = row[Users.lastLng],
                                imageUrl = row[Users.profileImageUrl],
                                badgeMetrics = metricsDto,
                                isPremium = isCurrentUserPremium, // 👈 Tells app client if content can be unmasked
                                hasSwipedMeRight = hasSwipedMe    // 👈 Controls layout blurring logic
                            )
                        }.filter { it.id != currentUserId }
                }

                val alreadySwipedPlayers = transaction {
                    Swipes.selectAll().where {
                        (Swipes.swiperId eq currentUserUuid) and
                                (Swipes.swipedId inList allPlayers.map { UUID.fromString(it.id) })
                    }.map { it[Swipes.swipedId].toString() }.toSet()
                }

                val freshPlayers = allPlayers.filterNot { alreadySwipedPlayers.contains(it.id) }

                if (userLat != null && userLng != null) {
                    val sortedPlayers = freshPlayers.sortedBy { player ->
                        if (player.lat != null && player.lng != null) {
                            calculateDistanceKm(userLat, userLng, player.lat!!, player.lng!!)
                        } else { Double.MAX_VALUE }
                    }
                    call.respond(sortedPlayers)
                } else {
                    call.respond(freshPlayers.shuffled())
                }
            }

// 2. 👇 NEW: DELETE Route to clear out swipe metrics and support premium undo operations
            delete("/{playerId}/swipe") {
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal?.payload?.getClaim("userId")?.asString()

                if (currentUserId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                    return@delete
                }

                val targetPlayerId = call.parameters["playerId"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing targets")

                val swiperUuid = UUID.fromString(currentUserId)
                val targetUuid = UUID.fromString(targetPlayerId)

                // Security Verification step: Verify pricing subscription tiers match execution scope limits
                val isPremium = transaction {
                    Users.selectAll().where { Users.id eq swiperUuid }.singleOrNull()?.get(Users.isPremium) == true
                }

                if (!isPremium) {
                    call.respond(HttpStatusCode.Forbidden, "Undo is a Premium subscription feature.")
                    return@delete
                }

                val rowsDeleted = transaction {
                    // Erase record out of database tables cleanly
                    Swipes.deleteWhere {
                        (Swipes.swiperId eq swiperUuid) and (Swipes.swipedId eq targetUuid)
                    }
                }

                if (rowsDeleted > 0) {
                    // Decrement profile metric targets cleanly to keep stats perfectly synched
                    transaction {
                        UserBadgeMetrics.update({ UserBadgeMetrics.userId eq swiperUuid }) {
                            with(SqlExpressionBuilder) {
                                it[profileSwipes] = profileSwipes - 1
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, "Swipe action reverted successfully.")
                } else {
                    call.respond(HttpStatusCode.NotFound, "No matching record found to undo.")
                }
            }

            // Add this route right inside your authenticated "/api/users" block in userRoutes.kt

            get("/likes") {
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal?.payload?.getClaim("userId")?.asString()

                if (currentUserId == null) {
                    return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                }

                val currentUserUuid = UUID.fromString(currentUserId)

                // Check if the current user is premium
                val isPremiumUser = transaction {
                    Users.selectAll().where { Users.id eq currentUserUuid }
                        .singleOrNull()?.getOrNull(Users.isPremium) == true
                }

                val peopleWhoLikedMe = transaction {
                    // ✂️ REMOVED: myOutboundSwipes query mapping block completely

                    (Swipes innerJoin Users)
                        .selectAll().where { (Swipes.swipedId eq currentUserUuid) and (Swipes.isLiked eq true) }
                        .map { row ->
                            val senderId = row[Users.id]

                            PlayerResponse(
                                id = senderId.toString(),
                                // PAYWALL SECURITY MASK: Unmasked for premium, placeholder text for free tiers
                                username = if (isPremiumUser) row[Users.username] else "Premium User",
                                skillLevel = row[Users.skillLevel]?.name ?: "Beginner",
                                lat = null,
                                lng = null,
                                imageUrl = row[Users.profileImageUrl],
                                isPremium = isPremiumUser,
                                hasSwipedMeRight = true
                            )
                        } // ✂️ REMOVED: .filterNot { ... } trailing condition block completely
                }

                call.respond(peopleWhoLikedMe)
            }

            // Inside your route("/api/users") block:

            route("/me") {

                // 1. GET Profile
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid token: Missing user ID")

                    val userId = UUID.fromString(userIdStr)
                    val userTimezone = call.request.headers["X-Timezone"] ?: "UTC"
                    badgeService.updateStreak(userId, userTimezone)

                    val userProfile = transaction {
                        Users.selectAll().where { Users.id eq UUID.fromString(userIdStr) }
                            .singleOrNull()?.let { row ->
                                UserProfile(
                                    id = row[Users.id].toString(),
                                    name = row[Users.username],
                                    email = row[Users.email],
                                    elo = row[Users.eloRating],
                                    winRate = "50%",
                                    preferredLanguage = row[Users.preferred_language],
                                    imageUrl = row[Users.profileImageUrl],
                                    blade = row[Users.gearBlade],
                                    rubberFh = row[Users.gearRubberFh],
                                    rubberBh = row[Users.gearRubberBh],
                                    bio = row[Users.bio],
                                    skillLevel = row[Users.skillLevel]?.name,
                                    birthDate =  row[Users.birthDate],
                                    age = calculateAge(row[Users.birthDate]),
                                    isPremium = row.getOrNull(Users.isPremium) == true
                                )
                            }
                    }

                    if (userProfile != null) {
                        call.respond(userProfile)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                }

                // 2. PUT Profile Update
                put {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@put call.respond(HttpStatusCode.Unauthorized)

                    val request = try { call.receive<UpdateProfileRequest>() } catch (e: Exception) {
                        return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON")
                    }

                    try {
                        val userUuid = UUID.fromString(userId)
                        val isNameTaken = transaction {
                            Users.selectAll().where { (Users.username eq request.name) and (Users.id neq userUuid) }.count() > 0
                        }

                        if (isNameTaken) {
                            return@put call.respond(HttpStatusCode.Conflict, "Username is already taken.")
                        }

                        val updatedRows = transaction {
                            Users.update({ Users.id eq userUuid }) {
                                it[username] = request.name
                                it[gearBlade] = request.blade
                                it[gearRubberFh] = request.forehand
                                it[gearRubberBh] = request.backhand
                                it[bio] = request.bio
                                it[birthDate] = request.birthDate
                                it[skillLevel] = request.skillLevel?.let { levelStr ->
                                    try { org.ttproject.database.tables.SkillLevel.valueOf(levelStr) } catch (e: Exception) { null }
                                }
                            }
                        }

                        if (updatedRows > 0) call.respond(HttpStatusCode.OK, mapOf("message" to "Profile updated!"))
                        else call.respond(HttpStatusCode.NotFound, "User not found.")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.message}")
                    }
                }

                // 3. POST Premium Toggle (Your 404 Fix)
                post("/toggle-premium") {
                    val principal = call.principal<JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")

                    val userUuid = UUID.fromString(userIdStr)

                    try {
                        val updatedPremiumState = transaction {
                            val currentPremium = Users.selectAll().where { Users.id eq userUuid }
                                .singleOrNull()?.let { it[Users.isPremium] } == true

                            val nextPremium = !currentPremium

                            Users.update({ Users.id eq userUuid }) {
                                it[Users.isPremium] = nextPremium
                            }
                            nextPremium
                        }

                        call.respond(HttpStatusCode.OK, mapOf("isPremium" to updatedPremiumState))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.message}")
                    }
                }
            }

            // 👇 Fetch a public profile by username
            get("/profile/{username}") {
                val username = call.parameters["username"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing username")

                val userProfile = transaction {
                    Users.selectAll().where { Users.username eq username }
                        .singleOrNull()?.let { row ->
                            UserProfile(
                                id = row[Users.id].toString(),
                                name = row[Users.username],
                                // 🚨 SECURITY: Blank out private info so it doesn't leak to other users!
                                email = "",
                                preferredLanguage = "",

                                elo = row[Users.eloRating],
                                winRate = "50%",
                                imageUrl = row[Users.profileImageUrl],
                                blade = row[Users.gearBlade],
                                rubberFh = row[Users.gearRubberFh],
                                rubberBh = row[Users.gearRubberBh],
                                bio = row[Users.bio],
                                skillLevel = row[Users.skillLevel]?.name,
                                age = calculateAge(row[Users.birthDate]),
                                isPremium = false
                            )
                        }
                }

                if (userProfile != null) {
                    call.respond(HttpStatusCode.OK, userProfile)
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            }

            put("/language") {
                // 1. Grab the user's ID from their secure JWT token
                val principal = call.principal<JWTPrincipal>()
                val userIdClaim = principal?.payload?.getClaim("userId")?.asString() // Note: check your login function to see what you named this claim! It might just be "id".

                if (userIdClaim == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                    return@put
                }

                // 2. Parse the JSON body from the mobile app
                val request = try {
                    call.receive<UpdateLanguageRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid JSON format")
                    return@put
                }

                // 3. Update the database!
                try {
                    val userUuid = UUID.fromString(userIdClaim)

                    val updatedRows = transaction {
                        Users.update({ Users.id eq userUuid }) {
                            it[Users.preferred_language] = request.language
                        }
                    }

                    if (updatedRows > 0) {
                        call.respond(HttpStatusCode.OK, "Language updated successfully!")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User not found in database.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.message}")
                }
            }
            post("/fcm-token") {
                val principal = call.principal<JWTPrincipal>()
                val userId = UUID.fromString(principal?.payload?.getClaim("userId")?.asString())
                val request = call.receive<TokenResponse>() // e.g., data class TokenRequest(val token: String)

                transaction {
                    Users.update({ Users.id eq userId }) { it[fcmToken] = request.token }
                }
                call.respond(HttpStatusCode.OK)
            }
            post("/profile-image") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                    return@post
                }

                var imageBytes: ByteArray? = null

                // 1. Read the multipart form data safely using forEachPart
                val multipartData = call.receiveMultipart()

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            // Read the bytes from the file stream
                            imageBytes = part.streamProvider().readBytes()
                        }
                        else -> {
                            // If you send other text fields in the multipart form later,
                            // you would handle PartData.FormItem here.
                        }
                    }
                    part.dispose() // Important: clears the memory for this specific part!
                }

                if (imageBytes != null) {
                    try {
                        // 1. Create a unique file name for the user
                        // Using timestamp ensures cache breaking if they upload a new picture
                        val fileName = "profile_images/${userId}_${System.currentTimeMillis()}.jpg"

                        // 2. Get the Firebase Storage Bucket and upload the bytes
                        val bucket = StorageClient.getInstance().bucket()
                        val blob = bucket.create(fileName, imageBytes, "image/jpeg")

                        // 3. Construct the public Download URL
                        // (The Admin SDK doesn't return a client-friendly URL by default, so we format it standardly)
                        val encodedFilePath = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                        val publicDownloadUrl = "https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/$encodedFilePath?alt=media"

                        // 4. Save the URL to your database
                        val userUuid = UUID.fromString(userId)
                        val updatedRows = transaction {
                            Users.update({ Users.id eq userUuid }) {
                                it[profileImageUrl] = publicDownloadUrl // Replace with your actual column name
                            }
                        }

                        if (updatedRows > 0) {
                            call.respond(HttpStatusCode.OK, mapOf(
                                "message" to "Image uploaded successfully",
                                "imageUrl" to publicDownloadUrl
                            ))
                        } else {
                            call.respond(HttpStatusCode.NotFound, "User not found in database to update image")
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, "Failed to upload image: ${e.message}")
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, "No image file provided")
                }
            }
        }

        route("/admin") {

            // Helper function to check if the current user is an admin
            suspend fun io.ktor.server.application.ApplicationCall.requireAdmin(): Boolean {
                val principal = principal<JWTPrincipal>()
                val userIdStr = principal?.payload?.getClaim("userId")?.asString() ?: return false
                val userUuid = UUID.fromString(userIdStr)

                val isAdmin = transaction {
                    Users.selectAll().where { Users.id eq userUuid }.singleOrNull()?.get(Users.isAdmin) == true
                }

                if (!isAdmin) {
                    respond(HttpStatusCode.Forbidden, "Admin access required")
                }
                return isAdmin
            }

            // 👇 1. GET ALL PENDING REPORTS
            get("/reports") {
                if (!call.requireAdmin()) return@get

                try {
                    val reports = transaction {
                        (ReportedMedia innerJoin Users)
                            .selectAll().where { ReportedMedia.status eq "PENDING" }
                            .orderBy(ReportedMedia.createdAt to SortOrder.DESC)
                            .map { row ->
                                ReportedMediaDto(
                                    id = row[ReportedMedia.id].toString(),
                                    reporterId = row[ReportedMedia.reporterId].toString(),
                                    reporterUsername = row[Users.username] ?: "Unknown",
                                    locationId = row[ReportedMedia.locationId].toString(),
                                    imageUrl = row[ReportedMedia.imageUrl],
                                    reason = row[ReportedMedia.reason],
                                    status = row[ReportedMedia.status],
                                    createdAt = row[ReportedMedia.createdAt]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, reports)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
                }
            }

            // 👇 2. RESOLVE REPORT: DELETE THE IMAGE
            post("/reports/{id}/delete-image") {
                if (!call.requireAdmin()) return@post

                val reportIdStr = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing report ID")

                val reportUuid = UUID.fromString(reportIdStr)

                try {
                    val isResolved = transaction {
                        val report = ReportedMedia.select { ReportedMedia.id eq reportUuid }.singleOrNull()
                        if (report == null) return@transaction false

                        val targetImageUrl = report[ReportedMedia.imageUrl]
                        val locationUuid = report[ReportedMedia.locationId]

                        var imageRemoved = false

                        // A. Try removing from the Location itself
                        val location = Locations.selectAll().where { Locations.id eq locationUuid }.singleOrNull()
                        if (location != null) {
                            val urls = location[Locations.imageUrls].split(",").filter { it.isNotBlank() }.toMutableList()
                            if (urls.remove(targetImageUrl)) {
                                Locations.update({ Locations.id eq locationUuid }) {
                                    it[imageUrls] = urls.joinToString(",")
                                }
                                imageRemoved = true
                            }
                        }

                        // B. Try removing from ANY review at this location
                        if (!imageRemoved) {
                            val reviews = Reviews.selectAll()
                                .where { Reviews.locationId eq locationUuid }
                                .toList()
                            for (row in reviews) {
                                val reviewUrls = row[Reviews.imageUrls].split(",").filter { it.isNotBlank() }.toMutableList()
                                if (reviewUrls.remove(targetImageUrl)) {
                                    Reviews.update({ Reviews.id eq row[Reviews.id] }) {
                                        it[imageUrls] = reviewUrls.joinToString(",")
                                    }
                                    imageRemoved = true
                                    break
                                }
                            }
                        }

                        // C. Update the report status to DELETED
                        if (imageRemoved) {
                            ReportedMedia.update({ ReportedMedia.id eq reportUuid }) {
                                it[status] = "DELETED"
                            }

                            // Optional: Actually delete the file from Firebase Storage here if you want to save space
                        }

                        imageRemoved
                    }

                    if (isResolved) {
                        call.respond(HttpStatusCode.OK, "Image deleted and report resolved.")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Image could not be found to delete.")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
                }
            }

            // 👇 3. RESOLVE REPORT: DISMISS (Image was fine)
            put("/reports/{id}/dismiss") {
                if (!call.requireAdmin()) return@put

                val reportIdStr = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing report ID")

                val reportUuid = UUID.fromString(reportIdStr)

                try {
                    transaction {
                        ReportedMedia.update({ ReportedMedia.id eq reportUuid }) {
                            it[status] = "DISMISSED"
                        }
                    }
                    call.respond(HttpStatusCode.OK, "Report dismissed.")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.localizedMessage)
                }
            }
        }
    }
}

fun calculateAge(birthDateStr: String?): Int? {
    if (birthDateStr.isNullOrBlank()) return null
    return try {
        val birthDate = LocalDate.parse(birthDateStr)
        Period.between(birthDate, LocalDate.now()).years
    } catch (e: Exception) { null }
}