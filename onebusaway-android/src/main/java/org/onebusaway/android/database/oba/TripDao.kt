/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.database.oba

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A reminder row projected for the My Reminders list, with the route's short name resolved by a join
 * (the legacy list looked it up per-row via ReminderUtils.getRouteShortName). [departure] is the
 * legacy minutes-to-midnight value.
 */
data class ReminderRow(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "route_id") val routeId: String?,
    val name: String?,
    val headsign: String?,
    val departure: Int,
    @ColumnInfo(name = "route_short_name") val routeShortName: String?,
)

/** Room access for trip reminders (the legacy `trips` table). */
@Dao
interface TripDao {

    @Query("SELECT * FROM trips WHERE _id = :tripId AND stop_id = :stopId LIMIT 1")
    suspend fun getTrip(tripId: String, stopId: String): TripRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripRecord)

    /** Deletes a single reminder trip (the legacy delete on `content://.../trips/{tripId#stopId}`). */
    @Query("DELETE FROM trips WHERE _id = :tripId AND stop_id = :stopId")
    suspend fun delete(tripId: String, stopId: String)

    @Query(
        "SELECT t._id AS trip_id, t.stop_id, t.route_id, t.name, t.headsign, t.departure, " +
            "r.short_name AS route_short_name FROM trips t " +
            "LEFT JOIN routes r ON r._id = t.route_id ORDER BY t.name ASC"
    )
    fun remindersByName(): Flow<List<ReminderRow>>

    @Query(
        "SELECT t._id AS trip_id, t.stop_id, t.route_id, t.name, t.headsign, t.departure, " +
            "r.short_name AS route_short_name FROM trips t " +
            "LEFT JOIN routes r ON r._id = t.route_id ORDER BY t.departure ASC"
    )
    fun remindersByDeparture(): Flow<List<ReminderRow>>
}
