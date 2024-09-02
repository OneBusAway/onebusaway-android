package org.onebusaway.android.database.recentStops.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class representing a region in the `regions` table.
 * Contains a primary key `regionId` for identifying regions.
 */

@Entity(tableName = "regions")
data class RegionEntity(
    @PrimaryKey val regionId:Int
)
