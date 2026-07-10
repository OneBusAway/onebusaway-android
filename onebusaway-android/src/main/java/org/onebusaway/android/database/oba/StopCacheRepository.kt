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

import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.models.NearbyStops
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.time.WallTime

/** Cached stops for a viewport plus the route-id → GTFS-type lookup for their marker colours. */
data class CachedViewport(val stops: List<ObaStop>, val routeTypes: Map<String, Int>)

/**
 * Persists and serves the map stop cache (#1754): the read-through store behind
 * [org.onebusaway.android.map.StopsMapController]. [stopsFor] returns the cached stops overlapping a
 * viewport (so the map can render before/without a network response); [save] upserts a network result
 * and evicts, so the cache stays fresh and bounded. All cache rows are scoped by region — the caller
 * must have a resolved region id (never a region-less query, which would mix feeds).
 *
 * [now] is the device wall clock ([WallTime] — the cache TTL is a purely local timer, per the CLAUDE.md
 * time-domain rule); it's passed in by the caller so this never reads the clock directly, and unwrapped
 * to epoch millis only at the DAO boundary.
 */
@Singleton
class StopCacheRepository @Inject constructor(private val dao: MapStopCacheDao) {

    /**
     * The cached stops overlapping the viewport centred on ([centerLat], [centerLon]) spanning
     * [latSpan]/[lonSpan], for [regionId], not older than the TTL relative to [now], capped at [limit].
     * Empty (no [routeTypes] fetch) when nothing is cached.
     */
    suspend fun stopsFor(
        centerLat: Double,
        centerLon: Double,
        latSpan: Double,
        lonSpan: Double,
        regionId: Long,
        now: WallTime,
        limit: Int,
    ): CachedViewport {
        val bounds = boundsFor(centerLat, centerLon, latSpan, lonSpan)
        val rows = dao.stopsInBounds(
            regionId, bounds.minLat, bounds.maxLat, bounds.minLon, bounds.maxLon,
            centerLat, centerLon, ttlCutoff(now).epochMs, limit,
        )
        if (rows.isEmpty()) return CachedViewport(emptyList(), emptyMap())
        val routeIds = rows.flatMapTo(mutableSetOf()) { splitRouteIds(it.routeIds).asIterable() }
        val types = dao.routeTypes(routeIds.toList()).associate { it.id to it.type }
        return CachedViewport(rows.map { it.toObaStop() }, types)
    }

    /** Upserts a network [nearby] load into the cache for [regionId] (stamped [now]) and evicts. */
    suspend fun save(nearby: NearbyStops, regionId: Long, now: WallTime) {
        val stopRows = nearby.stops.map { it.toCachedRecord(regionId, now.epochMs) }
        val typeRows = nearby.routes.map { CachedRouteTypeRecord(it.id, it.type, regionId, now.epochMs) }
        dao.saveAndEvict(stopRows, typeRows, regionId, ttlCutoff(now).epochMs, MAX_CACHED_STOPS_PER_REGION)
    }

    companion object {
        /** The per-region stop cap; an order of magnitude above the in-memory viewport LRU (200). */
        const val MAX_CACHED_STOPS_PER_REGION = 4000
    }
}
