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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.onebusaway.android.api.data.RouteStopsDataSource
import org.onebusaway.android.extrapolation.data.BoundedLruCache
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.RouteStopGroup
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.util.SingleFlight

data class FocusedTripShape(
    val shapeId: String,
    val routeId: String,
    val routeColor: Int?,
    val points: List<GeoPoint>,
    val directionId: Int? = null,
)

data class FocusedTripGeometry(val shapes: Map<String, FocusedTripShape>)

data class FocusedTripStops(
    val stopIdsByTripId: Map<String, List<String>>,
    val stopsById: Map<String, ObaStop>,
) {
    val stopIds: Set<String>
        get() = buildSet { stopIdsByTripId.values.forEach(::addAll) }
}

/** Exact shape and scheduled-stop pass-throughs for the trips displayed at a focused stop. */
interface FocusedTripRepository {
    suspend fun getGeometry(trips: Set<FocusedTrip>): FocusedTripGeometry
    suspend fun getStops(trips: Set<FocusedTrip>): FocusedTripStops
}

private const val MAX_CONCURRENT_SHAPE_FETCHES = 2
private const val MAX_CACHED_FOCUS_DATA = 32
private val FOCUS_DATA_CACHE_TTL = 10.minutes

private data class CachedValue<V>(val value: V, val storedAt: ElapsedTime)

private class ExpiringLruCache<K : Any, V : Any>(
    maxSize: Int,
    private val ttl: Duration,
    private val now: () -> ElapsedTime,
) {
    private val values = BoundedLruCache<K, CachedValue<V>>(maxSize)

    @Synchronized
    fun get(key: K): V? {
        val cached = values.get(key) ?: return null
        return if (now() - cached.storedAt < ttl) cached.value else {
            values.remove(key)
            null
        }
    }

    @Synchronized
    fun put(key: K, value: V) = values.put(key, CachedValue(value, now()))
}

@Singleton
class DefaultFocusedTripRepository internal constructor(
    private val observations: TripObservationRepository,
    private val routeStops: RouteStopsDataSource,
    fetchScope: CoroutineScope,
    cacheSize: Int = MAX_CACHED_FOCUS_DATA,
    cacheTtl: Duration = FOCUS_DATA_CACHE_TTL,
    now: () -> ElapsedTime = ElapsedTime::now,
) : FocusedTripRepository {

    @Inject
    constructor(
        observations: TripObservationRepository,
        routeStops: RouteStopsDataSource,
    ) : this(
        observations,
        routeStops,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    private val routeStopFetches = SingleFlight<String, List<RouteStopGroup>>(fetchScope)
    private val shapeFetches = SingleFlight<String, List<GeoPoint>>(fetchScope)
    private val shapePermits = Semaphore(MAX_CONCURRENT_SHAPE_FETCHES)
    private val routeStopCache =
        ExpiringLruCache<String, List<RouteStopGroup>>(cacheSize, cacheTtl, now)
    private val shapeCache =
        ExpiringLruCache<String, List<GeoPoint>>(cacheSize, cacheTtl, now)

    override suspend fun getGeometry(trips: Set<FocusedTrip>): FocusedTripGeometry = coroutineScope {
        val byShapeId = LinkedHashMap<String, FocusedTrip>()
        for (trip in trips) {
            trip.shapeId?.let { byShapeId.putIfAbsent(it, trip) }
        }
        val resolved = byShapeId.values.map { trip ->
            async {
                val shapeId = checkNotNull(trip.shapeId)
                trip to fetchShape(trip.tripId, shapeId)
            }
        }.awaitAll()
        FocusedTripGeometry(
            buildMap {
                for ((trip, points) in resolved) {
                    if (points != null) {
                        val shapeId = checkNotNull(trip.shapeId)
                        put(
                            shapeId,
                            FocusedTripShape(
                                shapeId,
                                trip.routeId,
                                trip.routeColor,
                                points,
                                trip.directionId,
                            )
                        )
                    }
                }
            }
        )
    }

    override suspend fun getStops(trips: Set<FocusedTrip>): FocusedTripStops = coroutineScope {
        val schedules = async {
            trips.map { trip ->
                async {
                    trip.tripId to resolveOrNull { observations.ensureSchedule(trip.tripId) }
                        ?.stopTimes
                        ?.map { it.stopId }
                }
            }.awaitAll()
        }
        val catalogs = async {
            trips.mapTo(LinkedHashSet()) { it.routeId }
                .map { routeId -> async { resolveOrNull { fetchRouteStops(routeId) } } }
                .awaitAll()
        }
        val stopIdsByTripId = buildMap {
            for ((tripId, stopIds) in schedules.await()) {
                if (stopIds != null) put(tripId, stopIds)
            }
        }
        val stopsById = buildMap {
            for (groups in catalogs.await()) {
                groups?.forEach { group -> group.stops.forEach { putIfAbsent(it.id, it) } }
            }
        }
        FocusedTripStops(stopIdsByTripId, stopsById)
    }

    private suspend fun fetchRouteStops(routeId: String): List<RouteStopGroup>? =
        routeStopCache.get(routeId) ?: routeStopFetches.run(routeId) {
            routeStopCache.get(routeId) ?: routeStops.stopsForRoute(routeId).getOrNull()
                ?.also { routeStopCache.put(routeId, it) }
        }

    /** Shape-id cache avoids refetching one pattern merely because a later arrival has a new trip id. */
    private suspend fun fetchShape(tripId: String, shapeId: String): List<GeoPoint>? =
        shapeCache.get(shapeId) ?: shapeFetches.run(shapeId) {
            shapeCache.get(shapeId) ?: shapePermits.withPermit {
                resolveOrNull { observations.ensureShape(tripId, shapeId) }
                    ?.let { polyline ->
                        withContext(Dispatchers.Default) { polyline.points.map(Location::toGeoPoint) }
                    }
                    ?.also { shapeCache.put(shapeId, it) }
            }
        }
}

/** A failed trip/route is omitted without cancelling successful siblings; cancellation still propagates. */
private suspend fun <T> resolveOrNull(block: suspend () -> T?): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
