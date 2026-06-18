package org.ttproject.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.ttproject.database.tables.AiAnalyses
import org.ttproject.database.tables.Connections
import org.ttproject.database.tables.Locations
import org.ttproject.database.tables.Matches
import org.ttproject.database.tables.MessageReactions
import org.ttproject.database.tables.Messages
import org.ttproject.database.tables.ReportedMedia
import org.ttproject.database.tables.ReviewTags
import org.ttproject.database.tables.Reviews
import org.ttproject.database.tables.Swipes
import org.ttproject.database.tables.UserBadgeMetrics
import org.ttproject.database.tables.Users

// 2. The connection function
fun initDatabase() {
    val dotenv = dotenv {
        ignoreIfMissing = true
    }
    val dbPassword = dotenv["SUPABASE_DB_PASSWORD"]
    val dbUser = dotenv["SUPABASE_DB_USER"]
    val dbUrl = dotenv["SUPABASE_DB_URL"]

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = dbUser
        password = dbPassword
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

//    transaction {
//        // 👇 ADD THIS LINE TEMPORARILY: This wipes the old tables!
//        SchemaUtils.drop(Reviews, ReviewTags)
//
//        // This rebuilds them with the new CASCADE and Unique rules
//        SchemaUtils.create(Reviews, ReviewTags)
//    }

    // 3. Automatically create the table if it doesn't exist yet
    transaction {
        SchemaUtils.createMissingTablesAndColumns(Users, Swipes, Locations, Reviews, ReviewTags, Matches, Connections, Messages,
            MessageReactions, AiAnalyses, UserBadgeMetrics,
            ReportedMedia)
    }

    insertDummyData()
}