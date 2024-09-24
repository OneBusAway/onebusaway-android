package org.onebusaway.android.database.recentStops

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.onebusaway.android.app.Application
import org.onebusaway.android.database.DatabaseProvider
import org.onebusaway.android.database.recentStops.entity.RegionEntity
import org.onebusaway.android.database.recentStops.entity.StopEntity
import org.onebusaway.android.io.elements.ObaStop

/**
 * Manages recent stops data by interacting with the database.
 * Handles saving new stops, and retrieving recent stops.
 */
object RecentStopsManager {

    // Maximum stops count to save
    private var MAX_STOP_COUNT = 5

    /**
     * Inserts a region into the database if it does not already exist.
     *
     * @param regionId The ID of the region to insert.
     */
    private suspend fun insertRegion(context: Context, regionId: Int) {
        val db = DatabaseProvider.getDatabase(context)
        val regionDao = db.regionDao()

        withContext(Dispatchers.IO) {
            val existingRegion = regionDao.getRegionByID(regionId)
            if (existingRegion == null) {
                regionDao.insertRegion(RegionEntity(regionId))
            }
        }
    }

    /**
     * Saves a new stop to the database. If the maximum number of stops is exceeded, deletes the oldest stop.
     *
     * @param stop The `ObaStop` object to save.
     */
    @JvmStatic
    fun saveStop(context: Context, stop: ObaStop) {
        val regionId = Application.get().currentRegion?.id?.toInt() ?: return

        val db = DatabaseProvider.getDatabase(context)
        val stopDao = db.stopDao()

        CoroutineScope(Dispatchers.IO).launch {
            val currentTime = System.currentTimeMillis()
            val stopCount = stopDao.getStopCount(regionId)

            if (stopCount >= MAX_STOP_COUNT) {
                // Delete the oldest stop and insert the new one
                stopDao.deleteOldestStop(regionId)
            }

            insertRegion(context, regionId)
            stopDao.insertStop(StopEntity(stop.id, stop.name, regionId, currentTime))
        }
    }

    /**
     * Retrieves a list of recent stop IDs for the current region.
     *
     * @return A list of recent stop IDs or an empty list if none are found.
     */
    fun getRecentStops(context: Context): List<String> {
        return runBlocking {
            val db = DatabaseProvider.getDatabase(context)
            val stopDao = db.stopDao()

            withContext(Dispatchers.IO) {
                val regionId = Application.get().currentRegion.id.toInt()
                val stops = stopDao.getRecentStopsForRegion(regionId)
                stops.map { it.stop_id }
            }
        }
    }
}
