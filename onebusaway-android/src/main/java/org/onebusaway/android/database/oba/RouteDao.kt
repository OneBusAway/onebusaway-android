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
import androidx.room.Embedded
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

/** A [RouteListRow] plus the raw [accessTime], for merging recent stops and routes into one time-ordered list. */
data class RouteRecentRow(
    @Embedded val row: RouteListRow,
    @ColumnInfo(name = "access_time") val accessTime: Long?,
)

private const val ROUTE_REGION_SCOPE =
    "(:regionId IS NULL OR region_id = :regionId OR region_id IS NULL)"

/** The recents predicate + ordering (newest first, capped), shared by the two recent-route queries. */
private const val ROUTE_RECENT_FILTER =
    "((access_time IS NOT NULL AND access_time > :cutoff) OR use_count > 0) AND $ROUTE_REGION_SCOPE"
private const val ROUTE_RECENT_ORDER = "ORDER BY access_time DESC, use_count DESC LIMIT 20"

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

    /**
     * The ids of every starred (favorite) route, live — the arrivals list overlays this to star rows +
     * promote favorited routes to the drawer header (#1751), reacting to a star toggle from an arrival
     * row without a re-fetch.
     */
    @Query("SELECT _id FROM routes WHERE favorite = 1")
    fun favoriteRouteIds(): Flow<List<String>>

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

    /**
     * Ensures the route row exists with its display name/URL/region (so the Starred Routes folder can
     * JOIN it) **without counting a use** — `use_count` and `access_time` are left untouched, and a new
     * row starts at `use_count = 0`. Favoriting/unfavoriting a route is not a "view", so it must not
     * bump the recents (`access_time`) or the frequency sort (`use_count`) (#1727 review). Null **or
     * empty** name/URL arguments preserve any existing value rather than clobbering it — a star from a
     * surface that only has a bare short name (whose loaded route may carry an empty long name) must not
     * wipe a good long name; the network backfill fills it in afterward.
     */
    @Transaction
    suspend fun ensureRouteDetails(
        routeId: String,
        shortName: String?,
        longName: String?,
        url: String?,
        regionId: Long?,
    ) {
        val existing = getRoute(routeId)
        upsert(
            existing?.copy(
                shortName = shortName?.takeIf { it.isNotEmpty() } ?: existing.shortName,
                longName = longName?.takeIf { it.isNotEmpty() } ?: existing.longName,
                url = url?.takeIf { it.isNotEmpty() } ?: existing.url,
                regionId = regionId ?: existing.regionId,
            ) ?: RouteRecord(
                id = routeId,
                shortName = shortName.orEmpty(),
                longName = longName,
                url = url,
                useCount = 0,
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
            "WHERE $ROUTE_RECENT_FILTER $ROUTE_RECENT_ORDER"
    )
    fun recents(cutoff: Long, regionId: Long?): Flow<List<RouteListRow>>

    /** Same rows as [recents], but carrying access_time so callers can merge stops and routes by recency. */
    @Query(
        "SELECT _id AS id, short_name, long_name, url, access_time FROM routes " +
            "WHERE $ROUTE_RECENT_FILTER $ROUTE_RECENT_ORDER"
    )
    fun recentsForSearch(cutoff: Long, regionId: Long?): Flow<List<RouteRecentRow>>

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
