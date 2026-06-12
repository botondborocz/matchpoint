package org.ttproject.database

import com.liftric.kvault.KVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ttproject.data.Player

class IosPlayerDatabase : PlayerDatabase {
    private val vault = KVault()
    private val key = "cached_players"
    private val json = Json { ignoreUnknownKeys = true }

    override fun savePlayers(players: List<Player>) {
        try {
            val serialized = json.encodeToString(players)
            vault.set(key, serialized)
        } catch (e: Exception) {
            println("iOS Player DB Save Error: ${e.message}")
        }
    }

    override fun getPlayers(): List<Player> {
        val serialized = vault.string(key) ?: return emptyList()
        return try {
            json.decodeFromString<List<Player>>(serialized)
        } catch (e: Exception) {
            println("iOS Player DB Load Error: ${e.message}")
            emptyList()
        }
    }

    override fun clearPlayers() {
        vault.deleteObject(key)
    }
}
