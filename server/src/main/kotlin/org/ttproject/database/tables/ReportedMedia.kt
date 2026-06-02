package org.ttproject.database.tables

import org.jetbrains.exposed.sql.Table

object ReportedMedia : Table("reported_media") {
    val id = uuid("id").autoGenerate()
    val reporterId = uuid("reporter_id").references(Users.id)
    val locationId = uuid("location_id").references(Locations.id)

    // We use 'text' instead of 'varchar' because Firebase storage URLs can get very long
    val imageUrl = text("image_url")

    // Optional: If you ever want to let users pick a reason (e.g., "Inappropriate", "Spam")
    val reason = varchar("reason", 255).nullable()

    // Track the status so you know what you've handled: PENDING, REVIEWED, or DELETED
    val status = varchar("status", 50).default("PENDING")

    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}