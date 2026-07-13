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
import org.onebusaway.android.api.data.TripVehiclesDataSource
import org.onebusaway.android.extrapolation.data.BoundedLruCache
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.simplifyRoutePolyline
import org.onebusaway.android.models.TripPatternGeometry
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.util.SingleFlight

/**
 * One exact trip-pattern shape for adjacency focus (#1827). [shapeId] comes from a trip serving the
 * selected stop, so [points] cannot include unrelated branches belonging to the same [routeId].
 */
data class AdjacencyRouteShape(
    val shapeId: String,
    val routeId: String,
    val routeColor: Int?,
    val points: List<GeoPoint>,
)

/**
 * The result of a multi-pattern shape fetch: the [shapes] that resolved (keyed by shape id, in the
 * requested set's iteration order) and the [failedShapeIds] that didn't (network error, non-OK code,
 * or no API endpoint). The two partition the requested set — a caller draws what resolved and ignores
 * the rest.
 */
data class AdjacencyShapes(
    val shapes: Map<String, AdjacencyRouteShape>,
    val failedShapeIds: Set<String>,
)

/** Stop membership for one route, kept separate from its exact trip-pattern render geometry. */
data class AdjacencyRouteStopMembership(
    val stopIds: Set<String>,
    val stopIdsByDirection: Map<Int, Set<String>>,
)

/** Route stop memberships that resolved, plus routes requiring conservative whole-route fallback. */
data class AdjacencyRouteStops(
    val routes: Map<String, AdjacencyRouteStopMembership>,
    val failedRouteIds: Set<String>,
)

/**
 * Fetches the exact shapes for the distinct trip patterns with an upcoming arrival at a tapped stop.
 * Tolerates partial failure: a shape that fails lands in [AdjacencyShapes.failedShapeIds] rather than
 * failing the whole call.
 */
interface AdjacencyRouteShapeRepository {

    /** Fetches [tripPatterns] concurrently (bounded), cached, and de-duped; never throws per shape. */
    suspend fun getShapes(tripPatterns: Set<TripPatternGeometry>): AdjacencyShapes

    /** Fetches direction-specific stop membership without coupling it to rendered route geometry. */
    suspend fun getRouteStops(routeIds: Set<String>): AdjacencyRouteStops =
        AdjacencyRouteStops(emptyMap(), routeIds)
}

private const val MAX_CONCURRENT_SHAPE_FETCHES = 2
private const val MAX_CACHED_SHAPES = 32
private const val ADJACENCY_SHAPE_SIMPLIFICATION_METERS = 2.0
private val SHAPE_CACHE_TTL = 10.minutes
private const val TAG = "AdjacencyRouteShapes"

/** One successfully mapped shape and the monotonic time it entered the completed cache. */
private data class CachedShapeGeometry(
    val points: List<GeoPoint>,
    val storedAt: ElapsedTime,
)

private data class CachedRouteStops(
    val membership: AdjacencyRouteStopMembership,
    val storedAt: ElapsedTime,
)

/** Thread-safe, success-only LRU with an age limit so GTFS shape changes eventually refresh. */
private class ShapeGeometryCache(
    maxSize: Int,
    private val ttl: Duration,
    private val now: () -> ElapsedTime,
) {
    private val shapes = BoundedLruCache<String, CachedShapeGeometry>(maxSize)

    @Synchronized
    fun get(shapeId: String): List<GeoPoint>? {
        val cached = shapes.get(shapeId) ?: return null
        return if (now() - cached.storedAt < ttl) {
            cached.points
        } else {
            shapes.remove(shapeId)
            null
        }
    }

    @Synchronized
    fun put(shapeId: String, points: List<GeoPoint>) {
        shapes.put(shapeId, CachedShapeGeometry(points, now()))
    }
}

/** Thread-safe, success-only LRU for the much smaller route-to-stop membership payload. */
private class RouteStopCache(
    maxSize: Int,
    private val ttl: Duration,
    private val now: () -> ElapsedTime,
) {
    private val routes = BoundedLruCache<String, CachedRouteStops>(maxSize)

    @Synchronized
    fun get(routeId: String): AdjacencyRouteStopMembership? {
        val cached = routes.get(routeId) ?: return null
        return if (now() - cached.storedAt < ttl) {
            cached.membership
        } else {
            routes.remove(routeId)
            null
        }
    }

    @Synchronized
    fun put(routeId: String, membership: AdjacencyRouteStopMembership) {
        routes.put(routeId, CachedRouteStops(membership, now()))
    }
}

/**
 * Fans out over [TripVehiclesDataSource.shape], one request per distinct trip pattern, with three
 * safeguards against a fetch storm at a busy hub:
 *
 * - **Bounded concurrency** via a [Semaphore] of [MAX_CONCURRENT_SHAPE_FETCHES] permits, so at most N
 *   requests are in flight and the rest queue. This deliberately uses a semaphore rather than
 *   [Dispatchers.IO]`.limitedParallelism` (the [org.onebusaway.android.extrapolation.data.TripObservationFetcher]
 *   precedent): the data source's Retrofit calls are main-safe `suspend` calls that hold no dispatcher
 *   thread while awaiting the response, so a limited dispatcher wouldn't actually bound in-flight
 *   requests — a semaphore held across the `suspend` call does, and it's exercisable under virtual time.
 * - **In-flight de-dup** via [SingleFlight], so a shape already being fetched (e.g. an immediate
 *   re-tap, or several arrivals sharing a pattern) is joined, not re-requested. The permit is acquired
 *   *inside* the coalesced block so joined callers consume none.
 * - **Completed-result cache**: successful shapes live in a 32-entry access-ordered LRU for ten
 *   minutes. This suppresses sequential re-fetches while users focus nearby stops that share shapes,
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
    private val tripVehiclesDataSource: TripVehiclesDataSource,
    fetchScope: CoroutineScope,
    private val log: (String) -> Unit,
    cacheSize: Int = MAX_CACHED_SHAPES,
    cacheTtl: Duration = SHAPE_CACHE_TTL,
    now: () -> ElapsedTime = ElapsedTime::now,
    private val mapDataSource: MapDataSource? = null,
) : AdjacencyRouteShapeRepository {

    @Inject
    constructor(
        tripVehiclesDataSource: TripVehiclesDataSource,
        mapDataSource: MapDataSource,
    ) : this(
        tripVehiclesDataSource = tripVehiclesDataSource,
        fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        log = { Log.w(TAG, it) },
        mapDataSource = mapDataSource,
    )

    private val permits = Semaphore(MAX_CONCURRENT_SHAPE_FETCHES)
    private val shapeFetches = SingleFlight<String, List<GeoPoint>>(fetchScope)
    private val shapeCache = ShapeGeometryCache(cacheSize, cacheTtl, now)
    private val routeStopFetches =
        SingleFlight<String, AdjacencyRouteStopMembership>(fetchScope)
    private val routeStopCache = RouteStopCache(cacheSize, cacheTtl, now)

    override suspend fun getShapes(
        tripPatterns: Set<TripPatternGeometry>
    ): AdjacencyShapes = coroutineScope {
        // Shape ids, not trips, are the render identity: several arrivals commonly share one pattern.
        val patternsByShapeId = LinkedHashMap<String, TripPatternGeometry>()
        tripPatterns.forEach { patternsByShapeId.putIfAbsent(it.shapeId, it) }
        val results = patternsByShapeId.values
            .map { pattern -> async { pattern to fetchShape(pattern.shapeId) } }
            .awaitAll()
        val shapes = LinkedHashMap<String, AdjacencyRouteShape>()
        val failed = LinkedHashSet<String>()
        for ((pattern, points) in results) {
            if (points != null) {
                shapes[pattern.shapeId] = AdjacencyRouteShape(
                    shapeId = pattern.shapeId,
                    routeId = pattern.routeId,
                    routeColor = pattern.routeColor,
                    points = points,
                )
            } else {
                failed += pattern.shapeId
            }
        }
        AdjacencyShapes(shapes, failed)
    }

    override suspend fun getRouteStops(routeIds: Set<String>): AdjacencyRouteStops = coroutineScope {
        val results = routeIds
            .map { routeId -> async { routeId to fetchRouteStops(routeId) } }
            .awaitAll()
        val routes = LinkedHashMap<String, AdjacencyRouteStopMembership>()
        val failed = LinkedHashSet<String>()
        for ((routeId, membership) in results) {
            if (membership != null) routes[routeId] = membership else failed += routeId
        }
        AdjacencyRouteStops(routes, failed)
    }

    /** Returns a fresh cache hit, or joins/starts a single-flight fetch; null on failure (no throw). */
    private suspend fun fetchShape(shapeId: String): List<GeoPoint>? =
        shapeCache.get(shapeId) ?: shapeFetches.run(shapeId) {
            // Re-check inside SingleFlight: a racing request may have populated the cache after this
            // caller's first lookup but before its coalesced block started.
            shapeCache.get(shapeId) ?: fetchAndCacheShape(shapeId)
        }

    /** Fetches and maps one exact GTFS shape, caching only a successful completed shape. */
    private suspend fun fetchAndCacheShape(shapeId: String): List<GeoPoint>? {
        // The permit bounds in-flight network requests only; hold it just for the fetch. shape
        // returns Result; a failure/absent endpoint resolves to null (recorded as a failed id) and
        // never throws, so SingleFlight's own error path (its Log.e) never runs.
        val polyline = permits.withPermit {
            tripVehiclesDataSource.shape(shapeId)
                .onFailure { log("trip shape fetch failed for $shapeId: $it") }
                .getOrNull()
        }
        // Walking the shape into GeoPoints is CPU-bound; keep it off the main thread so a busy hub's
        // focus doesn't jank.
        return polyline
            ?.let {
                withContext(Dispatchers.Default) {
                    simplifyRoutePolyline(
                        it.points.map(Location::toGeoPoint),
                        ADJACENCY_SHAPE_SIMPLIFICATION_METERS,
                    )
                }
            }
            ?.also { shapeCache.put(shapeId, it) }
    }

    private suspend fun fetchRouteStops(routeId: String): AdjacencyRouteStopMembership? =
        routeStopCache.get(routeId) ?: routeStopFetches.run(routeId) {
            routeStopCache.get(routeId) ?: fetchAndCacheRouteStops(routeId)
        }

    private suspend fun fetchAndCacheRouteStops(routeId: String): AdjacencyRouteStopMembership? {
        val dataSource = mapDataSource ?: return null
        val data = permits.withPermit {
            dataSource.routeMap(routeId)
                .onFailure { log("route stop fetch failed for $routeId: $it") }
                .getOrNull()
        }
        return data
            ?.let { withContext(Dispatchers.Default) { it.toAdjacencyRouteStops() } }
            ?.also { routeStopCache.put(routeId, it) }
    }
}

/** Maps only direction membership; whole-route polylines deliberately stay out of this pass-through. */
internal fun RouteMapData.toAdjacencyRouteStops(): AdjacencyRouteStopMembership =
    AdjacencyRouteStopMembership(
        stopIds = stops.mapTo(LinkedHashSet()) { it.stop.id },
        stopIdsByDirection = buildMap<Int, MutableSet<String>> {
            for (routeStop in stops) {
                for (directionId in routeStop.directionIds) {
                    getOrPut(directionId) { LinkedHashSet() }.add(routeStop.stop.id)
                }
            }
        },
    )
