package org.ttproject.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UserBadgeMetrics : Table("user_badge_metrics") {
    // Make sure this is a UUID and references the Users table
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()

    // Térkép és Adatbázis
    val addedTables = integer("added_tables").default(0)
    val uploadedPhotos = integer("uploaded_photos").default(0)
    val writtenReviews = integer("written_reviews").default(0)

    // Match és Közösség
    val successfulMatches = integer("successful_matches").default(0)
    val sentMessages = integer("sent_messages").default(0)
    val profileSwipes = integer("profile_swipes").default(0)

    // AI Edző és Fejlődés
    val trimmedVideos = integer("trimmed_videos").default(0)
    val aiQuestions = integer("ai_questions").default(0)

    // Növekedés és Megtartás
    val currentStreak = integer("current_streak").default(0)
    val maxStreak = integer("max_streak").default(0)
    val lastCheckIn = timestamp("last_check_in").nullable()
    val invitedFriends = integer("invited_friends").default(0)

    override val primaryKey = PrimaryKey(userId)
}