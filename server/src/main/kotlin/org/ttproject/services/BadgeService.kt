package org.ttproject.services

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
import org.ttproject.data.UserBadgeMetricsDto
import org.ttproject.database.tables.UserBadgeMetrics
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit

class BadgeService {

    // 1. Call this when a new user registers
    fun initializeMetricsForUser(userIdParam: UUID) {
        transaction {
            UserBadgeMetrics.insertIgnore {
                it[userId] = userIdParam
                // The rest will fall back to their .default(0) values
            }
        }
    }

    // 2. Fetch metrics to send to the frontend
    fun getMetricsForUser(userIdParam: UUID): UserBadgeMetricsDto? {
        return transaction {
            UserBadgeMetrics.selectAll().where { UserBadgeMetrics.userId eq userIdParam }
                .singleOrNull()
                ?.let { row ->
                    UserBadgeMetricsDto(
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
                }
        }
    }

    // 3. Example helper to increment a specific metric atomically
    fun incrementMetric(userIdParam: UUID, column: Column<Int>, amount: Int = 1) {
        transaction {
            // 👇 1. Ensure the record exists. insertIgnore does nothing if it's already there.
            UserBadgeMetrics.insertIgnore {
                it[userId] = userIdParam
            }

            // 👇 2. Now perform the update safely
            UserBadgeMetrics.update({ UserBadgeMetrics.userId eq userIdParam }) {
                with(SqlExpressionBuilder) {
                    it.update(column, column + amount)
                }
            }

            // Optional: Log it in the console to verify it's working
            println("DEBUG: Incremented ${column.name} for user $userIdParam by $amount")
        }
    }

    fun updateStreak(userIdParam: UUID, userTimeZone: String?) {
        transaction {
            // Ensure row exists
            UserBadgeMetrics.insertIgnore { it[userId] = userIdParam }

            val row = UserBadgeMetrics.select { UserBadgeMetrics.userId eq userIdParam }.single()
            val lastCheckIn = row[UserBadgeMetrics.lastCheckIn]
            val zoneId = try {
                ZoneId.of(userTimeZone ?: "UTC")
            } catch (e: Exception) {
                ZoneId.of("UTC")
            }

            val now = Instant.now()
            val today = LocalDate.ofInstant(now, zoneId)
            val lastDate = lastCheckIn?.let { LocalDate.ofInstant(it, ZoneId.systemDefault()) }

            when {
                // 1. First time or already checked in today -> Do nothing
                lastDate == today -> return@transaction

                // 2. Checked in yesterday -> Increment streak
                lastDate == today.minusDays(1) -> {
                    val newStreak = row[UserBadgeMetrics.currentStreak] + 1
                    UserBadgeMetrics.update({ UserBadgeMetrics.userId eq userIdParam }) {
                        it[currentStreak] = newStreak
                        it[UserBadgeMetrics.lastCheckIn] = now
                        if (newStreak > row[maxStreak]) {
                            it[maxStreak] = newStreak
                        }
                    }
                }

                // 3. Missed a day or more -> Reset to 1
                else -> {
                    UserBadgeMetrics.update({ UserBadgeMetrics.userId eq userIdParam }) {
                        it[currentStreak] = 1
                        it[UserBadgeMetrics.lastCheckIn] = now
                    }
                }
            }
        }
    }
}