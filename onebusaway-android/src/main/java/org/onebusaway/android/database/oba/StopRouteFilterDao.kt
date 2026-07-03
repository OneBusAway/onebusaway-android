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

/** Room access for per-stop route filters (the legacy `stop_routes_filter` table). */
@Dao
interface StopRouteFilterDao {

    @Query("SELECT route_id FROM stop_routes_filter WHERE stop_id = :stopId")
    suspend fun routeIdsForStop(stopId: String): List<String>

    @Query("DELETE FROM stop_routes_filter WHERE stop_id = :stopId")
    suspend fun deleteForStop(stopId: String)

    @Insert
    suspend fun insert(rows: List<StopRouteFilterRecord>)

    /** Replaces the filter for [stopId] (delete then insert), matching the legacy set(). */
    @Transaction
    suspend fun replaceForStop(stopId: String, routeIds: List<String>) {
        deleteForStop(stopId)
        insert(routeIds.map { StopRouteFilterRecord(stopId = stopId, routeId = it) })
    }
}
