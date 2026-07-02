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
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A route row projected for the My-tab lists. */
data class RouteListRow(
    val id: String,
    @ColumnInfo(name = "short_name") val shortName: String,
    @ColumnInfo(name = "long_name") val longName: String?,
    val url: String?,
)

private const val ROUTE_REGION_SCOPE =
    "(:regionId IS NULL OR region_id = :regionId OR region_id IS NULL)"

/** Room access for routes + the My-tab recent/starred lists (the legacy `routes` table). */
@Dao
interface RouteDao {

    @Query("SELECT * FROM routes WHERE _id = :routeId LIMIT 1")
    suspend fun getRoute(routeId: String): RouteRecord?

    @Query("SELECT short_name FROM routes WHERE _id = :routeId LIMIT 1")
    suspend fun shortName(routeId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(route: RouteRecord)

    @Query("UPDATE routes SET favorite = :favorite WHERE _id = :routeId")
    suspend fun setFavorite(routeId: String, favorite: Int)

    // --- Usage/metadata writes (the legacy partial upsert; see RoutesStore) ---

    /**
     * Records a route's short/long name and marks it used, leaving an existing URL untouched. Merges
     * onto the existing row so unset columns aren't clobbered. [now] is supplied by the caller so this
     * stays a stateless helper.
     */
    @Transaction
    suspend fun markRouteUsed(
        routeId: String,
        shortName: String?,
        longName: String?,
        regionId: Long?,
        now: Long,
    ) {
        val existing = getRoute(routeId)
        upsert(
            existing?.copy(
                shortName = shortName.orEmpty(),
                longName = longName,
                regionId = regionId ?: existing.regionId,
                useCount = existing.useCount + 1,
                accessTime = now,
            ) ?: RouteRecord(
                id = routeId,
                shortName = shortName.orEmpty(),
                longName = longName,
                useCount = 1,
                accessTime = now,
                regionId = regionId,
            )
        )
    }

    /** Stores a route's full short/long name + URL and marks it used. */
    @Transaction
    suspend fun storeRouteDetails(
        routeId: String,
        shortName: String?,
        longName: String?,
        url: String?,
        regionId: Long?,
        now: Long,
    ) {
        val existing = getRoute(routeId)
        upsert(
            existing?.copy(
                shortName = shortName.orEmpty(),
                longName = longName,
                url = url,
                regionId = regionId ?: existing.regionId,
                useCount = existing.useCount + 1,
                accessTime = now,
            ) ?: RouteRecord(
                id = routeId,
                shortName = shortName.orEmpty(),
                longName = longName,
                url = url,
                useCount = 1,
                accessTime = now,
                regionId = regionId,
            )
        )
    }

    /** Refreshes only a route's short name (does not mark it used; leaves other columns untouched). */
    @Transaction
    suspend fun refreshRouteShortName(routeId: String, shortName: String?) {
        val existing = getRoute(routeId)
        upsert(
            existing?.copy(shortName = shortName.orEmpty())
                ?: RouteRecord(id = routeId, shortName = shortName.orEmpty(), useCount = 0)
        )
    }

    // --- Recents/starred lists (reactive) ---

    @Query(
        "SELECT _id AS id, short_name, long_name, url FROM routes " +
            "WHERE ((access_time IS NOT NULL AND access_time > :cutoff) OR use_count > 0) " +
            "AND $ROUTE_REGION_SCOPE ORDER BY access_time DESC, use_count DESC LIMIT 20"
    )
    fun recents(cutoff: Long, regionId: Long?): Flow<List<RouteListRow>>

    @Query(
        "SELECT _id AS id, short_name, long_name, url FROM routes " +
            "WHERE favorite = 1 AND $ROUTE_REGION_SCOPE ORDER BY length(short_name), short_name ASC"
    )
    fun starredByName(regionId: Long?): Flow<List<RouteListRow>>

    @Query(
        "SELECT _id AS id, short_name, long_name, url FROM routes " +
            "WHERE favorite = 1 AND $ROUTE_REGION_SCOPE ORDER BY use_count DESC"
    )
    fun starredByFrequency(regionId: Long?): Flow<List<RouteListRow>>

    // --- Recents/starred mutations ---

    @Query("UPDATE routes SET use_count = 0, access_time = NULL WHERE _id = :routeId")
    suspend fun markUnused(routeId: String)

    @Query("UPDATE routes SET use_count = 0, access_time = NULL")
    suspend fun markAllUnused()

    @Query("UPDATE routes SET favorite = 0")
    suspend fun clearAllFavorites()
}
