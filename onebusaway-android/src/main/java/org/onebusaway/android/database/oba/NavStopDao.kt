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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room access for the turn-by-turn navigation state (the legacy `nav_stops` table). Only ever one
 * active trip: the legacy insert deleted all rows first, mirrored here by [replaceActive].
 */
@Dao
interface NavStopDao {

    @Query("DELETE FROM nav_stops")
    suspend fun clearAll()

    @Insert
    suspend fun insert(row: NavStopRecord)

    /** Stores the single active navigation trip, clearing any prior one (legacy delete-then-insert). */
    @Transaction
    suspend fun replaceActive(row: NavStopRecord) {
        clearAll()
        insert(row)
    }

    /** The trip/destination/before stop ids for a navigation id (the legacy `getDetails`). */
    @Query(
        "SELECT trip_id AS tripId, destination_id AS destinationId, before_id AS beforeId " +
            "FROM nav_stops WHERE nav_id = :navId LIMIT 1"
    )
    suspend fun getDetails(navId: String): NavStopDetailsRow?
}

/** The three stop ids a navigation session needs (the legacy `NavStops.getDetails` projection). */
data class NavStopDetailsRow(
    val tripId: String,
    val destinationId: String,
    val beforeId: String,
)
