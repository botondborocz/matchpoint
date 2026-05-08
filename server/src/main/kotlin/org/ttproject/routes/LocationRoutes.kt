package org.ttproject.routes

import com.google.firebase.cloud.StorageClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.ttproject.data.Location
import org.ttproject.data.ReviewResponse
import org.ttproject.database.tables.LocationType
import org.ttproject.database.tables.Locations
import org.ttproject.database.tables.ReviewTags
import org.ttproject.database.tables.Reviews
import org.ttproject.database.tables.Users
import org.ttproject.utils.calculateDistanceKm
import java.lang.System.currentTimeMillis
import java.net.URLEncoder
import java.util.UUID

// 👇 Firebase Upload Helper
suspend fun uploadToFirebaseStorage(imagesBytes: List<ByteArray>, folder: String): String {
    if (imagesBytes.isEmpty()) return ""

    val urls = mutableListOf<String>()

    withContext(Dispatchers.IO) {
        val bucket = StorageClient.getInstance().bucket()

        imagesBytes.forEach { bytes ->
            val isVideo = bytes.size > 8 &&
                    bytes[4].toInt().toChar() == 'f' &&
                    bytes[5].toInt().toChar() == 't' &&
                    bytes[6].toInt().toChar() == 'y' &&
                    bytes[7].toInt().toChar() == 'p'

            val extension = if (isVideo) "mp4" else "jpg"
            val contentType = if (isVideo) "video/mp4" else "image/jpeg"

            val fileName = "$folder/${UUID.randomUUID()}.$extension"
            bucket.create(fileName, bytes, contentType)

            val encodedPath = URLEncoder.encode(fileName, "UTF-8")
            val publicUrl = "https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/$encodedPath?alt=media"

            urls.add(publicUrl)
        }
    }

    return urls.joinToString(",")
}

fun Route.locationRoutes() {
    route("/api") {
        get("/locations/nearby") {
            val userLat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val userLng = call.request.queryParameters["lng"]?.toDoubleOrNull()

            val allLocations = transaction {
                // 👇 FIX 1: Join the Users table!
                (Locations leftJoin Users).selectAll().map { row ->
                    Location(
                        id = row[Locations.id].toString(),
                        name = row[Locations.name] ?: "Unnamed Location",
                        latitude = row[Locations.latitude],
                        longitude = row[Locations.longitude],
                        type = org.ttproject.data.LocationType.valueOf(row[Locations.type].name),
                        isFree = row[Locations.isFree],
                        tableCount = row[Locations.tableCount],
                        address = row[Locations.address],
                        // 👇 FIX 2: Grab the real username!
                        createdBy = row[Users.username] ?: "Anonymous",
                        imageUrls = row[Locations.imageUrls].split(",").filter { it.isNotBlank() }
                    )
                }
            }

            if (userLat != null && userLng != null) {
                val sortedLocations = allLocations.sortedBy { loc ->
                    calculateDistanceKm(userLat, userLng, loc.latitude, loc.longitude)
                }
                call.respond(sortedLocations)
            } else {
                call.respond(allLocations)
            }
        }

        get("/locations/{id}/reviews") {
            val locationId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)

            try {
                val reviews = transaction {
                    val reviewsData = (Reviews innerJoin Users)
                        .select { Reviews.locationId eq UUID.fromString(locationId) }
                        .orderBy(Reviews.createdAt to SortOrder.DESC)
                        .toList()

                    val reviewIds = reviewsData.map { it[Reviews.id] }

                    val tagsData = if (reviewIds.isNotEmpty()) {
                        ReviewTags.selectAll().where { ReviewTags.reviewId inList reviewIds }
                            .groupBy({ it[ReviewTags.reviewId] }, { it[ReviewTags.tag] })
                    } else {
                        emptyMap()
                    }

                    reviewsData.map { row ->
                        val rId = row[Reviews.id]
                        ReviewResponse(
                            id = rId.toString(),
                            userId = row[Reviews.userId].toString(),
                            username = row[Users.username] ?: "Anonymous",
                            textContent = row[Reviews.textContent],
                            tags = tagsData[rId] ?: emptyList(),
                            // 👇 Restore URLs to a list
                            imageUrls = row[Reviews.imageUrls].split(",").filter { it.isNotBlank() },
                            createdAt = row[Reviews.createdAt]
                        )
                    }
                }
                call.respond(reviews)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error fetching reviews")
            }
        }

        authenticate("auth-jwt") {

            // 👇 1. ADD TABLE MULTIPART LOGIC
            post("/locations") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val multipart = call.receiveMultipart()

                var latitude = 0.0
                var longitude = 0.0
                var type = "Outdoor"
                var tableCount = 1
                var isFree = true
                var notes: String? = null
                val imageBytesList = mutableListOf<ByteArray>()

                // Parse the parts
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "latitude" -> latitude = part.value.toDoubleOrNull() ?: 0.0
                                "longitude" -> longitude = part.value.toDoubleOrNull() ?: 0.0
                                "type" -> type = part.value
                                "tableCount" -> tableCount = part.value.toIntOrNull() ?: 1
                                "isFree" -> isFree = part.value.toBooleanStrictOrNull() ?: true
                                "notes" -> notes = part.value
                            }
                        }
                        is PartData.FileItem -> {
                            imageBytesList.add(part.streamProvider().readBytes())
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                // Upload the gathered byte arrays to Firebase!
                val imageUrlsString = uploadToFirebaseStorage(imageBytesList, "locations/tables")

                transaction {
                    Locations.insert {
                        it[this.type] = LocationType.valueOf(type)
                        it[this.latitude] = latitude
                        it[this.longitude] = longitude
                        it[this.tableCount] = tableCount
                        it[this.isFree] = isFree
                        it[this.notes] = notes
                        it[this.isVerified] = false
                        it[this.createdBy] = UUID.fromString(userId)
                        it[this.createdAt] = currentTimeMillis()
                        it[this.imageUrls] = imageUrlsString // 👇 Save the URLs
                    }
                }

                call.respond(HttpStatusCode.Created, "Table added successfully!")
            }

            // 👇 2. ADD REVIEW MULTIPART LOGIC
            post("/locations/{id}/reviews") {
                val locationIdStr = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing location ID")

                val principal = call.principal<JWTPrincipal>()
                val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Missing user claim")

                val locationUuid = runCatching { UUID.fromString(locationIdStr) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid location UUID format")
                val userUuid = runCatching { UUID.fromString(userIdStr) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid user UUID format")

                val multipart = call.receiveMultipart()

                var textContent: String? = null
                var tagsList = emptyList<String>()
                val imageBytesList = mutableListOf<ByteArray>()

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "textContent" -> textContent = part.value
                                "tags" -> tagsList = part.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            }
                        }
                        is PartData.FileItem -> {
                            imageBytesList.add(part.streamProvider().readBytes())
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                // Upload to Firebase
                val imageUrlsString = uploadToFirebaseStorage(imageBytesList, "locations/reviews")

                try {
                    transaction {
                        val insertStatement = Reviews.insert {
                            it[this.locationId] = locationUuid
                            it[this.userId] = userUuid
                            it[this.textContent] = textContent?.takeIf { text -> text.isNotBlank() }
                            it[this.createdAt] = System.currentTimeMillis()
                            it[this.imageUrls] = imageUrlsString // 👇 Save the URLs
                        }

                        val newReviewId = insertStatement[Reviews.id]

                        tagsList.forEach { tagString ->
                            ReviewTags.insert {
                                it[reviewId] = newReviewId
                                it[tag] = tagString
                            }
                        }
                    }
                    call.respond(HttpStatusCode.Created, "Review added successfully")

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.localizedMessage}")
                }
            }

            // 👇 3. ADD STANDALONE PHOTOS TO A LOCATION
            post("/locations/{id}/images") {
                val locationIdStr = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing location ID")

                val locationUuid = runCatching { UUID.fromString(locationIdStr) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid location UUID format")

                val multipart = call.receiveMultipart()
                val imageBytesList = mutableListOf<ByteArray>()

                // We only care about files here!
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        imageBytesList.add(part.streamProvider().readBytes())
                    }
                    part.dispose()
                }

                if (imageBytesList.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "No images provided")
                }

                // Upload to Firebase
                val newImageUrlsString = uploadToFirebaseStorage(imageBytesList, "locations/tables")

                try {
                    transaction {
                        // 1. Fetch the current URLs
                        val existingLocation = Locations.select { Locations.id eq locationUuid }.singleOrNull()
                        val existingUrls = existingLocation?.get(Locations.imageUrls) ?: ""

                        // 2. Safely append the new URLs
                        val updatedUrls = if (existingUrls.isBlank()) newImageUrlsString else "$existingUrls,$newImageUrlsString"

                        // 3. Update the database
                        Locations.update({ Locations.id eq locationUuid }) {
                            it[imageUrls] = updatedUrls
                        }
                    }
                    call.respond(HttpStatusCode.OK, "Images added successfully")

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.localizedMessage}")
                }
            }
        }
    }
}