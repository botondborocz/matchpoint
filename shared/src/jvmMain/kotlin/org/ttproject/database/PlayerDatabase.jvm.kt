package org.ttproject.database

import org.ttproject.data.Player

class JvmPlayerDatabase : PlayerDatabase {
    override fun savePlayers(players: List<Player>) {}
    override fun getPlayers(): List<Player> = emptyList()
    override fun clearPlayers() {}
}
