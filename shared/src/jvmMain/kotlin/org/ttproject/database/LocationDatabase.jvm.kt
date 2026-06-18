package org.ttproject.database

import org.ttproject.data.Location

class JvmLocationDatabase : LocationDatabase {
    override fun saveLocations(locations: List<Location>) {}
    override fun getLocations(): List<Location> = emptyList()
    override fun clearLocations() {}
}
