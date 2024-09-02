package org.onebusaway.android.database.recentStops.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.onebusaway.android.database.recentStops.entity.StopEntity
/**
 * DAO interface for accessing and managing the `stops` table in the database.
 */
@Dao
interface StopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: StopEntity): Long

    @Query("SELECT * FROM stops WHERE regionId = :regionId")
    suspend fun getRecentStopsForRegion(regionId: Int): List<StopEntity>

    @Query("SELECT COUNT(*) FROM stops WHERE regionId = :regionId")
    suspend fun getStopCount(regionId: Int): Int

    @Query("DELETE FROM stops WHERE stop_id = (SELECT stop_id FROM stops WHERE regionId = :regionId ORDER BY timestamp ASC LIMIT 1)")
    suspend fun deleteOldestStop(regionId: Int)
}
