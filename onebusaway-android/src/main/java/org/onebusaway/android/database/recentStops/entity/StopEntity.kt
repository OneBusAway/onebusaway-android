package org.onebusaway.android.database.recentStops.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a stop in the `stops` table.
 * Includes a primary key `stop_id`, foreign key `regionId`, stop name, and timestamp.
 */

@Entity(
    tableName = "stops",
    foreignKeys = [
        ForeignKey(
            entity = RegionEntity::class,
            parentColumns = ["regionId"],
            childColumns = ["regionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StopEntity(
    @PrimaryKey val stop_id: String,
    val name: String,
    val regionId: Int,
    val timestamp: Long
)
