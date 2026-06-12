package org.ttproject.database

import org.ttproject.data.Player

interface PlayerDatabase {
    fun savePlayers(players: List<Player>)
    fun getPlayers(): List<Player>
    fun clearPlayers()
}
