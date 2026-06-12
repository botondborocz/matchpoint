package org.ttproject.database

import com.liftric.kvault.KVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ttproject.data.Location

class IosLocationDatabase : LocationDatabase {
    private val vault = KVault()
    private val key = "cached_locations"
    private val json = Json { ignoreUnknownKeys = true }

    override fun saveLocations(locations: List<Location>) {
        try {
            val serialized = json.encodeToString(locations)
            vault.set(key, serialized)
        } catch (e: Exception) {
            println("iOS DB Save Error: ${e.message}")
        }
    }

    override fun getLocations(): List<Location> {
        val serialized = vault.string(key) ?: return emptyList()
        return try {
            json.decodeFromString<List<Location>>(serialized)
        } catch (e: Exception) {
            println("iOS DB Load Error: ${e.message}")
            emptyList()
        }
    }

    override fun clearLocations() {
        vault.deleteObject(key)
    }
}
