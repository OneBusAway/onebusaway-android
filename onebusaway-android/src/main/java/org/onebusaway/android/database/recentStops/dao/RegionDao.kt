package org.onebusaway.android.database.recentStops.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.onebusaway.android.database.recentStops.entity.RegionEntity

/**
 * DAO interface for accessing and modifying the `regions` table in the database.
 */

@Dao
interface RegionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: RegionEntity): Long

    @Query("SELECT *FROM regions WHERE :regionId = regionId")
    suspend fun getRegionByID(regionId:Int):Int?
}
