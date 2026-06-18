package org.ttproject.database

import org.ttproject.data.Location

interface LocationDatabase {
    fun saveLocations(locations: List<Location>)
    fun getLocations(): List<Location>
    fun clearLocations()
}
