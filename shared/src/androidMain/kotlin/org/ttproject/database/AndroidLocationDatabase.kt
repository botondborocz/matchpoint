package org.ttproject.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ttproject.data.Location
import org.ttproject.data.LocationType

class AndroidLocationDatabase(context: Context) : LocationDatabase, SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "locations.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_LOCATIONS = "locations"

        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_IS_FREE = "is_free"
        private const val COLUMN_TABLE_COUNT = "table_count"
        private const val COLUMN_ADDRESS = "address"
        private const val COLUMN_CREATED_BY = "created_by"
        private const val COLUMN_TAGS = "tags"
        private const val COLUMN_IMAGE_URLS = "image_urls"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_LOCATIONS (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_NAME TEXT,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL,
                $COLUMN_TYPE TEXT,
                $COLUMN_IS_FREE INTEGER,
                $COLUMN_TABLE_COUNT INTEGER,
                $COLUMN_ADDRESS TEXT,
                $COLUMN_CREATED_BY TEXT,
                $COLUMN_TAGS TEXT,
                $COLUMN_IMAGE_URLS TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOCATIONS")
        onCreate(db)
    }

    override fun saveLocations(locations: List<Location>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // First clear old
            db.delete(TABLE_LOCATIONS, null, null)

            val json = Json { ignoreUnknownKeys = true }
            for (location in locations) {
                val values = ContentValues().apply {
                    put(COLUMN_ID, location.id)
                    put(COLUMN_NAME, location.name)
                    put(COLUMN_LATITUDE, location.latitude)
                    put(COLUMN_LONGITUDE, location.longitude)
                    put(COLUMN_TYPE, location.type.name)
                    put(COLUMN_IS_FREE, if (location.isFree) 1 else 0)
                    put(COLUMN_TABLE_COUNT, location.tableCount)
                    put(COLUMN_ADDRESS, location.address)
                    put(COLUMN_CREATED_BY, location.createdBy)
                    put(COLUMN_TAGS, json.encodeToString(location.tags))
                    put(COLUMN_IMAGE_URLS, json.encodeToString(location.imageUrls))
                }
                db.insertWithOnConflict(TABLE_LOCATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun getLocations(): List<Location> {
        val locations = mutableListOf<Location>()
        val db = readableDatabase
        val cursor = db.query(TABLE_LOCATIONS, null, null, null, null, null, null)
        val json = Json { ignoreUnknownKeys = true }

        cursor.use { c ->
            val idIdx = c.getColumnIndex(COLUMN_ID)
            val nameIdx = c.getColumnIndex(COLUMN_NAME)
            val latIdx = c.getColumnIndex(COLUMN_LATITUDE)
            val lngIdx = c.getColumnIndex(COLUMN_LONGITUDE)
            val typeIdx = c.getColumnIndex(COLUMN_TYPE)
            val freeIdx = c.getColumnIndex(COLUMN_IS_FREE)
            val countIdx = c.getColumnIndex(COLUMN_TABLE_COUNT)
            val addrIdx = c.getColumnIndex(COLUMN_ADDRESS)
            val creatorIdx = c.getColumnIndex(COLUMN_CREATED_BY)
            val tagsIdx = c.getColumnIndex(COLUMN_TAGS)
            val imagesIdx = c.getColumnIndex(COLUMN_IMAGE_URLS)

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val name = c.getString(nameIdx)
                val lat = c.getDouble(latIdx)
                val lng = c.getDouble(lngIdx)
                val typeStr = c.getString(typeIdx)
                val type = try { LocationType.valueOf(typeStr) } catch(e: Exception) { LocationType.Outdoor }
                val isFree = c.getInt(freeIdx) == 1
                val count = c.getInt(countIdx)
                val address = c.getString(addrIdx)
                val createdBy = c.getString(creatorIdx)
                val tagsStr = c.getString(tagsIdx)
                val tags = try { json.decodeFromString<List<String>>(tagsStr) } catch(e: Exception) { emptyList() }
                val imagesStr = c.getString(imagesIdx)
                val imageUrls = try { json.decodeFromString<List<String>>(imagesStr) } catch(e: Exception) { emptyList() }

                locations.add(
                    Location(
                        id = id,
                        name = name,
                        latitude = lat,
                        longitude = lng,
                        type = type,
                        isFree = isFree,
                        tags = tags,
                        tableCount = count,
                        address = address,
                        createdBy = createdBy,
                        imageUrls = imageUrls
                    )
                )
            }
        }
        return locations
    }

    override fun clearLocations() {
        val db = writableDatabase
        db.delete(TABLE_LOCATIONS, null, null)
    }
}
