package org.ttproject.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ttproject.data.Player

class AndroidPlayerDatabase(context: Context) : PlayerDatabase, SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "players.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_PLAYERS = "players"

        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_SKILL_LEVEL = "skill_level"
        private const val COLUMN_LAT = "lat"
        private const val COLUMN_LNG = "lng"
        private const val COLUMN_AGE = "age"
        private const val COLUMN_ELO = "elo"
        private const val COLUMN_DISTANCE_KM = "distance_km"
        private const val COLUMN_IMAGE_URL = "image_url"
        private const val COLUMN_BADGE_METRICS = "badge_metrics"
        private const val COLUMN_IS_PREMIUM = "is_premium"
        private const val COLUMN_HAS_SWIPED_ME_RIGHT = "has_swiped_me_right"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_PLAYERS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_USERNAME TEXT,
                $COLUMN_SKILL_LEVEL TEXT,
                $COLUMN_LAT REAL,
                $COLUMN_LNG REAL,
                $COLUMN_AGE INTEGER,
                $COLUMN_ELO INTEGER,
                $COLUMN_DISTANCE_KM INTEGER,
                $COLUMN_IMAGE_URL TEXT,
                $COLUMN_BADGE_METRICS TEXT,
                $COLUMN_IS_PREMIUM INTEGER,
                $COLUMN_HAS_SWIPED_ME_RIGHT INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYERS")
        onCreate(db)
    }

    override fun savePlayers(players: List<Player>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_PLAYERS, null, null)
            val json = Json { ignoreUnknownKeys = true }
            for (player in players) {
                val values = ContentValues().apply {
                    put(COLUMN_ID, player.id)
                    put(COLUMN_USERNAME, player.username)
                    put(COLUMN_SKILL_LEVEL, player.skillLevel)
                    put(COLUMN_LAT, player.lat)
                    put(COLUMN_LNG, player.lng)
                    put(COLUMN_AGE, player.age)
                    put(COLUMN_ELO, player.elo)
                    put(COLUMN_DISTANCE_KM, player.distanceKm)
                    put(COLUMN_IMAGE_URL, player.imageUrl)
                    put(COLUMN_BADGE_METRICS, player.badgeMetrics?.let { json.encodeToString(it) })
                    put(COLUMN_IS_PREMIUM, if (player.isPremium) 1 else 0)
                    put(COLUMN_HAS_SWIPED_ME_RIGHT, if (player.hasSwipedMeRight) 1 else 0)
                }
                db.insertWithOnConflict(TABLE_PLAYERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun getPlayers(): List<Player> {
        val players = mutableListOf<Player>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PLAYERS, null, null, null, null, null, null)
        val json = Json { ignoreUnknownKeys = true }

        cursor.use { c ->
            val idIdx = c.getColumnIndex(COLUMN_ID)
            val usernameIdx = c.getColumnIndex(COLUMN_USERNAME)
            val skillIdx = c.getColumnIndex(COLUMN_SKILL_LEVEL)
            val latIdx = c.getColumnIndex(COLUMN_LAT)
            val lngIdx = c.getColumnIndex(COLUMN_LNG)
            val ageIdx = c.getColumnIndex(COLUMN_AGE)
            val eloIdx = c.getColumnIndex(COLUMN_ELO)
            val distIdx = c.getColumnIndex(COLUMN_DISTANCE_KM)
            val imgIdx = c.getColumnIndex(COLUMN_IMAGE_URL)
            val badgeIdx = c.getColumnIndex(COLUMN_BADGE_METRICS)
            val premIdx = c.getColumnIndex(COLUMN_IS_PREMIUM)
            val swipeIdx = c.getColumnIndex(COLUMN_HAS_SWIPED_ME_RIGHT)

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val username = c.getString(usernameIdx)
                val skill = c.getString(skillIdx)
                val lat = if (c.isNull(latIdx)) null else c.getDouble(latIdx)
                val lng = if (c.isNull(lngIdx)) null else c.getDouble(lngIdx)
                val age = c.getInt(ageIdx)
                val elo = c.getInt(eloIdx)
                val distance = c.getInt(distIdx)
                val img = c.getString(imgIdx)
                val badgeStr = c.getString(badgeIdx)
                val badge = badgeStr?.let {
                    try { json.decodeFromString<org.ttproject.data.UserBadgeMetricsDto>(it) } catch(e: Exception) { null }
                }
                val isPremium = c.getInt(premIdx) == 1
                val hasSwipedMeRight = c.getInt(swipeIdx) == 1

                players.add(
                    Player(
                        id = id,
                        username = username,
                        skillLevel = skill,
                        lat = lat,
                        lng = lng,
                        age = age,
                        elo = elo,
                        distanceKm = distance,
                        imageUrl = img,
                        badgeMetrics = badge,
                        isPremium = isPremium,
                        hasSwipedMeRight = hasSwipedMeRight
                    )
                )
            }
        }
        return players
    }

    override fun clearPlayers() {
        val db = writableDatabase
        db.delete(TABLE_PLAYERS, null, null)
    }
}
