package org.ttproject.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

enum class LocationType { Indoor, Outdoor }

// 1. UPDATED LOCATIONS TABLE
object Locations : Table("locations") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 100).nullable()
    val type = enumerationByName("type", 20, LocationType::class)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = text("address").nullable()
    val isFree = bool("is_free").default(true)
    val tableCount = integer("table_count").default(1)

    val notes = text("notes").nullable()
    val isVerified = bool("is_verified").default(false)

    // 👇 Added CASCADE: If a user deletes their account, their created locations become "Anonymous" (SET_NULL)
    // or you can use CASCADE to delete the locations entirely. SET_NULL is safer for UGC!
    val createdBy = reference("created_by", Users.id, onDelete = ReferenceOption.SET_NULL).nullable()

    // (Nullable is fine here to fix the migration issue you had earlier!)
    val createdAt = long("created_at").nullable()

    val imageUrls = text("image_urls").default("") // 👇 Added!

    override val primaryKey = PrimaryKey(id)
}

// 2. NEW REVIEWS TABLE
object Reviews : Table("reviews") {
    val id = uuid("id").autoGenerate()

    // 👇 Added CASCADE: If the location or user is deleted, instantly delete their reviews too!
    val locationId = reference("location_id", Locations.id, onDelete = ReferenceOption.CASCADE).index() // Added index() for fast lookups!
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)

    val textContent = text("text_content").nullable()
    val createdAt = long("created_at")

    val imageUrls = text("image_urls").default("") // 👇 Added!

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_user_location_review", userId, locationId)
    }
}

// 3. NEW REVIEW TAGS TABLE
object ReviewTags : Table("review_tags") {
    // 👇 Added CASCADE: If a review is deleted, clean up its tags!
    val reviewId = reference("review_id", Reviews.id, onDelete = ReferenceOption.CASCADE)
    val tag = varchar("tag", 50)

    // 👇 ADDED COMPOSITE PRIMARY KEY: Prevents duplicate tags on the same review
    override val primaryKey = PrimaryKey(reviewId, tag)
}