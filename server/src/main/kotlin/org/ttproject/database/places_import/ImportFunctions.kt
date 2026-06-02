package org.ttproject.database.places_import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.batchInsert
import org.ttproject.database.tables.LocationType
import org.ttproject.database.tables.Locations
import java.io.File
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger

fun importGeoJsonToDatabase(filePath: String) {
    println("\n--- STARTING GEOJSON IMPORT PROCESS ---")

    // 1. Check the file
    val file = File(filePath)
    println("📍 1. Reading file from: ${file.absolutePath}")
    if (!file.exists()) {
        println("❌ ERROR: File does not exist!")
        return
    }

    val jsonString = file.readText()
    println("✅ 2. File read successfully. Size: ${jsonString.length} characters")

    // 2. Parse the JSON
    val collection = try {
        Json { ignoreUnknownKeys = true }.decodeFromString<GeoJsonCollection>(jsonString)
    } catch (e: Exception) {
        println("❌ ERROR: Failed to parse JSON. Check your data classes!")
        e.printStackTrace()
        return
    }

    println("✅ 3. JSON parsed successfully. Found ${collection.features.size} features in the file.")

    if (collection.features.isEmpty()) {
        println("⚠️ WARNING: 0 features found. Nothing to insert. Exiting.")
        return
    }

    // Helper function
    fun JsonObject.getString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    println("⏳ 4. Starting database transaction...")

    // 3. Database Transaction with error handling
    try {
        transaction {
            // TURN ON SQL LOGGING! This will print the actual INSERT commands to your console.
            addLogger(StdOutSqlLogger)

            var preparedCount = 0

            Locations.batchInsert(collection.features, shouldReturnGeneratedValues = false) { feature ->
                val props = feature.properties
                val coords = feature.geometry.coordinates

                val rawName = props.getString("name") ?: "Table Tennis Table"

                // Print the first 3 items just to verify the mapping is working inside the loop
                if (preparedCount < 3) {
                    println("   -> Preparing row: $rawName at [${coords[0]}, ${coords[1]}]")
                } else if (preparedCount == 3) {
                    println("   -> ... continuing for the remaining ${collection.features.size - 3} items ...")
                }

                // --- MAP LAT / LON ---
                this[Locations.longitude] = coords[0]
                this[Locations.latitude] = coords[1]

                // --- MAP NAME ---
                this[Locations.name] = rawName.take(100)

                // --- MAP TYPE (Indoor / Outdoor) ---
                val isIndoor = props.getString("indoor") == "room" ||
                        props.getString("indoor") == "yes" ||
                        props.getString("building") == "yes" ||
                        props.getString("leisure") == "sports_centre" ||
                        props.getString("leisure") == "sports_hall"

                this[Locations.type] = if (isIndoor) LocationType.Indoor else LocationType.Outdoor

                // --- MAP IS FREE ---
                val hasFee = props.getString("fee") == "yes" || props.getString("charge") != null
                this[Locations.isFree] = !hasFee

                // --- MAP TABLE COUNT ---
                val tableCountStr = props.getString("table_tennis:tables")
                    ?: props.getString("number_of_tables")
                    ?: props.getString("capacity")
                    ?: props.getString("count")
                    ?: "1"

                this[Locations.tableCount] = tableCountStr.toIntOrNull() ?: 1

                // --- MAP ADDRESS ---
                val city = props.getString("addr:city")
                val street = props.getString("addr:street")
                val houseNum = props.getString("addr:housenumber")

                val fullAddress = listOfNotNull(city, street, houseNum).joinToString(", ")
                this[Locations.address] = fullAddress.ifBlank { null }

                preparedCount++
            }

            println("✅ 5. Batch insert statement executed. Prepared $preparedCount rows.")
        }

        println("🎉 --- IMPORT COMPLETE! Data has been committed to the database. ---")

    } catch (e: Exception) {
        println("❌ DATABASE ERROR: Transaction failed and rolled back! See details below:")
        e.printStackTrace()
    }
}

// Example execution
fun main() {
    val file = File("server/src/main/resources/table_tennis_locations_austria.json")

    println("Looking for file at: ${file.absolutePath}")

    if (!file.exists()) {
        println("❌ Error: File not found!")
        return
    }
    println("✅ File found! Starting import...")
    Database.connect(
        url = "jdbc:postgresql://89.168.56.34:5433/match_db",
        user = "ktor_user",
        password = "ktor_password"
    )
    println("✅ Database connection established! Importing data...")
    importGeoJsonToDatabase(file.path)
}