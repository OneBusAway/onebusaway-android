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
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room access for the map stop cache ([CachedStopRecord] + [CachedRouteTypeRecord]). Only SQL lives
 * here; the bounding-box math and TTL cutoff are pure helpers in [MapStopCacheMappers]
 * ([boundsFor]/[ttlCutoff]). See [StopCacheRepository] for the caller-facing API.
 */
@Dao
interface MapStopCacheDao {

    // --- reads (bounding box + region + TTL) ---

    /**
     * Cached stops within the viewport bounds, for the region, seen no earlier than [ttlCutoff]. When
     * more than [limit] match (a dense/zoomed-out view), returns the [limit] nearest the viewport centre
     * ([centerLat]/[centerLon]) — so the render is centred on what the user is looking at, mirroring the
     * server's centre-biased result, rather than an arbitrary corner of the box. Squared planar distance
     * is enough to rank within one small viewport (no need for great-circle accuracy).
     */
    @Query(
        "SELECT * FROM cached_stops WHERE region_id = :regionId AND last_seen >= :ttlCutoff " +
            "AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon " +
            "ORDER BY (latitude - :centerLat) * (latitude - :centerLat) + " +
            "(longitude - :centerLon) * (longitude - :centerLon) ASC LIMIT :limit"
    )
    suspend fun stopsInBounds(
        regionId: Long,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        centerLat: Double,
        centerLon: Double,
        ttlCutoff: Long,
        limit: Int,
    ): List<CachedStopRecord>

    /** The cached route types for the given route ids (the icon-colour lookup). */
    @Query("SELECT * FROM cached_route_types WHERE _id IN (:routeIds)")
    suspend fun routeTypes(routeIds: List<String>): List<CachedRouteTypeRecord>

    // --- writes ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStops(stops: List<CachedStopRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRouteTypes(types: List<CachedRouteTypeRecord>)

    // --- eviction ---

    @Query("DELETE FROM cached_stops WHERE region_id = :regionId AND last_seen < :ttlCutoff")
    suspend fun evictStaleStops(regionId: Long, ttlCutoff: Long)

    @Query("DELETE FROM cached_route_types WHERE region_id = :regionId AND last_seen < :ttlCutoff")
    suspend fun evictStaleRouteTypes(regionId: Long, ttlCutoff: Long)

    /** Keeps only the [cap] most-recently-seen stops in the region, dropping the rest. */
    @Query(
        "DELETE FROM cached_stops WHERE region_id = :regionId AND _id NOT IN " +
            "(SELECT _id FROM cached_stops WHERE region_id = :regionId ORDER BY last_seen DESC LIMIT :cap)"
    )
    suspend fun evictBeyondCap(regionId: Long, cap: Int)

    /**
     * Upserts a viewport's stops + route types then evicts, in one transaction, so a concurrent
     * [stopsInBounds] never observes a partially-written viewport. [ttlCutoff] drops stale rows;
     * [cap] bounds the stop count per region.
     */
    @Transaction
    suspend fun saveAndEvict(
        stops: List<CachedStopRecord>,
        types: List<CachedRouteTypeRecord>,
        regionId: Long,
        ttlCutoff: Long,
        cap: Int,
    ) {
        upsertStops(stops)
        upsertRouteTypes(types)
        evictStaleStops(regionId, ttlCutoff)
        evictStaleRouteTypes(regionId, ttlCutoff)
        // The cap delete's `NOT IN (SELECT … LIMIT cap)` scans the whole region even when it would
        // delete nothing, so gate it on a cheap indexed COUNT — the common (under-cap) pan skips it.
        if (countStops(regionId) > cap) evictBeyondCap(regionId, cap)
    }

    /** The number of cached stops for a region. */
    @Query("SELECT COUNT(*) FROM cached_stops WHERE region_id = :regionId")
    suspend fun countStops(regionId: Long): Int
}
