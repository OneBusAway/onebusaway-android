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

/**
 * Room access for route/headsign favorites (the legacy `route_headsign_favorites` table). A null
 * headsign argument matches any headsign (the legacy WHERE omits the headsign clause in that case).
 */
@Dao
interface RouteHeadsignFavoriteDao {

    @Query(
        "DELETE FROM route_headsign_favorites WHERE route_id = :routeId AND stop_id = :stopId " +
            "AND (:headsign IS NULL OR headsign = :headsign)"
    )
    suspend fun deleteMatch(routeId: String, headsign: String?, stopId: String)

    @Query(
        "DELETE FROM route_headsign_favorites WHERE route_id = :routeId " +
            "AND (:headsign IS NULL OR headsign = :headsign)"
    )
    suspend fun deleteForRoute(routeId: String, headsign: String?)

    @Insert
    suspend fun insert(row: RouteHeadsignFavoriteRecord)

    @Query("SELECT EXISTS(SELECT 1 FROM route_headsign_favorites WHERE route_id = :routeId AND exclude = 0)")
    suspend fun routeHasFavorite(routeId: String): Boolean

    /**
     * All favorite/exclude rows that could apply to a stop: rows for that stop plus the wildcard
     * `all`-stops rows. One query for a whole arrivals list, so [computeRouteHeadsignFavorite] can
     * resolve each row's favorite state in memory instead of a per-row DB lookup.
     */
    @Query("SELECT * FROM route_headsign_favorites WHERE stop_id = :stopId OR stop_id = 'all'")
    suspend fun favoritesForStopOrAll(stopId: String): List<RouteHeadsignFavoriteRecord>

    @Query("DELETE FROM route_headsign_favorites")
    suspend fun clearAll()
}
