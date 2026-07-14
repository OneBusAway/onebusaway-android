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
package org.onebusaway.android.map

import android.location.Location
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.extrapolation.data.BoundedLruCache
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.simplifyRoutePolyline
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.util.SingleFlight

/**
 * One focused route's whole-route shape for adjacency focus (#1827): the merged (undirected)
 * [polylines] to draw, the [route] for the badge's color/name, and the ids of the [stopIds] it serves
 * (so non-adjacent stops can be minimized). Slimmer than [RouteMap] because several are held at once
 * and adjacency draws only the whole route (no per-direction shapes or direction picker).
 */
data class AdjacencyRouteShape(
    val routeId: String,
    val route: ObaRoute?,
    val polylines: List<List<GeoPoint>>,
    val stopIds: Set<String>,
)

/**
 * The result of a multi-route shape fetch: the [shapes] that resolved (keyed by route id, in the
 * requested set's iteration order) and the [failedRouteIds] that didn't (network error, non-OK code,
 * or no API endpoint). The two partition the requested set — a caller draws what resolved and ignores
 * the rest.
 */
data class AdjacencyShapes(
    val shapes: Map<String, AdjacencyRouteShape>,
    val failedRouteIds: Set<String>,
)

/**
 * Fetches the whole-route shapes for a *set* of routes at once — the routes with an upcoming arrival at
 * a tapped stop, drawn together as adjacency focus (#1827). Tolerates partial failure: a route that
 * fails to load lands in [AdjacencyShapes.failedRouteIds] rather than failing the whole call.
 */
interface AdjacencyRouteShapeRepository {

    /** Fetches [routeIds]'s shapes concurrently (bounded), cached, and de-duped; never throws per-route. */
    suspend fun getShapes(routeIds: Set<String>): AdjacencyShapes
}

private const val MAX_CONCURRENT_ROUTE_FETCHES = 2
private const val MAX_CACHED_ROUTE_SHAPES = 32
private const val ADJACENCY_SHAPE_SIMPLIFICATION_METERS = 2.0
private val ROUTE_SHAPE_CACHE_TTL = 10.minutes
private const val TAG = "AdjacencyRouteShapes"

/** One successfully mapped route shape and the monotonic time it entered the completed cache. */
private data class CachedRouteShape(
    val shape: AdjacencyRouteShape,
    val storedAt: ElapsedTime,
)

/** Thread-safe, success-only LRU with an age limit so route-map changes eventually refresh. */
private class RouteShapeCache(
    maxSize: Int,
    private val ttl: Duration,
    private val now: () -> ElapsedTime,
) {
    private val shapes = BoundedLruCache<String, CachedRouteShape>(maxSize)

    @Synchronized
    fun get(routeId: String): AdjacencyRouteShape? {
        val cached = shapes.get(routeId) ?: return null
        return if (now() - cached.storedAt < ttl) {
            cached.shape
        } else {
            shapes.remove(routeId)
            null
        }
    }

    @Synchronized
    fun put(routeId: String, shape: AdjacencyRouteShape) {
        shapes.put(routeId, CachedRouteShape(shape, now()))
    }
}

/**
 * Fans out over [MapDataSource.routeMap], one request per route, with three safeguards against a
 * fetch storm at a busy hub (a stop with many upcoming routes = many `stops-for-route` calls):
 *
 * - **Bounded concurrency** via a [Semaphore] of [MAX_CONCURRENT_ROUTE_FETCHES] permits, so at most N
 *   requests are in flight and the rest queue. This deliberately uses a semaphore rather than
 *   [Dispatchers.IO]`.limitedParallelism` (the [org.onebusaway.android.extrapolation.data.TripObservationFetcher]
 *   precedent): the data source's Retrofit calls are main-safe `suspend` calls that hold no dispatcher
 *   thread while awaiting the response, so a limited dispatcher wouldn't actually bound in-flight
 *   requests — a semaphore held across the `suspend` call does, and it's exercisable under virtual time.
 * - **In-flight de-dup** via [SingleFlight], so a route already being fetched (e.g. an immediate re-tap,
 *   or two routes sharing the set) is joined, not re-requested. The permit is acquired *inside* the
 *   coalesced block so joined callers consume none.
 * - **Completed-result cache**: successful shapes live in a 32-entry access-ordered LRU for ten
 *   minutes. This suppresses sequential re-fetches while users focus nearby stops that share routes,
 *   bounds the potentially large polyline working set, and eventually refreshes server changes.
 *   Failures are never cached, so a later focus can retry them.
 *
 * The coalesced fetch runs on the process-lifetime [fetchScope], so a caller that cancels mid-fetch
 * (tapping away) abandons its join immediately while the in-flight work completes for the next caller
 * to re-join — the desired de-dup across re-taps. A successful completion is then available to later
 * callers through the repository-owned cache.
 *
 * `@Singleton` because it owns both the [SingleFlight] de-dup state and the completed-result cache (the
 * [org.onebusaway.android.extrapolation.data.DefaultTripObservationFetcher] precedent).
 */
@Singleton
class DefaultAdjacencyRouteShapeRepository internal constructor(
    private val mapDataSource: MapDataSource,
    fetchScope: CoroutineScope,
    private val log: (String) -> Unit,
    cacheSize: Int = MAX_CACHED_ROUTE_SHAPES,
    cacheTtl: Duration = ROUTE_SHAPE_CACHE_TTL,
    now: () -> ElapsedTime = ElapsedTime::now,
) : AdjacencyRouteShapeRepository {

    @Inject
    constructor(mapDataSource: MapDataSource) : this(
        mapDataSource = mapDataSource,
        fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        log = { Log.w(TAG, it) },
    )

    private val permits = Semaphore(MAX_CONCURRENT_ROUTE_FETCHES)
    private val shapeFetches = SingleFlight<String, AdjacencyRouteShape>(fetchScope)
    private val shapeCache = RouteShapeCache(cacheSize, cacheTtl, now)

    override suspend fun getShapes(routeIds: Set<String>): AdjacencyShapes = coroutineScope {
        val results = routeIds
            .map { id -> async { id to fetchShape(id) } }
            .awaitAll()
        val shapes = LinkedHashMap<String, AdjacencyRouteShape>()
        val failed = LinkedHashSet<String>()
        for ((id, shape) in results) {
            if (shape != null) shapes[id] = shape else failed += id
        }
        AdjacencyShapes(shapes, failed)
    }

    /** Returns a fresh cache hit, or joins/starts a single-flight fetch; null on failure (no throw). */
    private suspend fun fetchShape(routeId: String): AdjacencyRouteShape? =
        shapeCache.get(routeId) ?: shapeFetches.run(routeId) {
            // Re-check inside SingleFlight: a racing request may have populated the cache after this
            // caller's first lookup but before its coalesced block started.
            shapeCache.get(routeId) ?: fetchAndCacheShape(routeId)
        }

    /** Fetches and maps one route, caching only a successful completed shape. */
    private suspend fun fetchAndCacheShape(routeId: String): AdjacencyRouteShape? {
        // The permit bounds in-flight network requests only; hold it just for the fetch. routeMap
        // returns Result; a failure/absent endpoint resolves to null (drawn as a failed id) and
        // never throws, so SingleFlight's own error path (its Log.e) never runs.
        val data = permits.withPermit {
            mapDataSource.routeMap(routeId)
                .onFailure { log("route shape fetch failed for $routeId: $it") }
                .getOrNull()
        }
        // Walking the shape into GeoPoints is CPU-bound; keep it off the main thread (as
        // MapDataSource already does for the decode) so a busy hub's focus doesn't jank.
        return data
            ?.let { withContext(Dispatchers.Default) { it.toAdjacencyShape(routeId) } }
            ?.also { shapeCache.put(routeId, it) }
    }
}

/** Maps a wire [RouteMapData] to the slim adjacency shape, converting shape points to [GeoPoint]. */
internal fun RouteMapData.toAdjacencyShape(routeId: String): AdjacencyRouteShape =
    AdjacencyRouteShape(
        routeId = routeId,
        route = route,
        // Simplify once in fetchAndCacheShape's Dispatchers.Default block. Google Maps otherwise has
        // to retain and retessellate every GTFS shape vertex during a pinch zoom; caching the result
        // keeps that work out of both the renderer and every later focus on the same route. A fixed
        // world-space tolerance avoids swapping geometry as the camera crosses zoom levels.
        polylines = polylines.map { line ->
            simplifyRoutePolyline(
                line.map(Location::toGeoPoint),
                ADJACENCY_SHAPE_SIMPLIFICATION_METERS,
            )
        },
        stopIds = stops.mapTo(LinkedHashSet()) { it.stop.id },
    )
