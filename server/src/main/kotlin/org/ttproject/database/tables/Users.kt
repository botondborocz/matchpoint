package org.ttproject.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

enum class SkillLevel { Beginner, Intermediate, Advanced, Pro }
enum class PlayStyle { Offensive, Defensive, AllRound }

object Users : Table("users") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val username = varchar("username", 50).uniqueIndex().nullable()
    val fullName = varchar("full_name", 100).nullable()
    val profileImageUrl = text("profile_image_url").nullable()

    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255).nullable()
    val googleId = varchar("google_id", 255).nullable().uniqueIndex()
    val fcmToken = varchar("fcm_token", 255).nullable()

    val skillLevel = enumerationByName("skill_level", 20, SkillLevel::class).nullable()
    val eloRating = integer("elo_rating").default(1200)
    val playStyle = enumerationByName("play_style", 20, PlayStyle::class).nullable()
    val bio = varchar("bio", 500).nullable()
    val birthDate = varchar("birth_date", 20).nullable() // Stored as "YYYY-MM-DD"

    val gearBlade = varchar("gear_blade", 100).nullable()
    val gearRubberFh = varchar("gear_rubber_fh", 100).nullable()
    val gearRubberBh = varchar("gear_rubber_bh", 100).nullable()

    val lastLat = double("last_lat").nullable()
    val lastLng = double("last_lng").nullable()

    val preferred_language = varchar("preferred_language", 10).nullable()

    val isAdmin = bool("is_admin").default(false)

    val isPremium = bool("is_premium").default(false)

    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}