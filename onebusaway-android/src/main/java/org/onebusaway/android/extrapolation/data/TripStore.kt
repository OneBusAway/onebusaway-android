/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
@file:JvmName("TripStore")

package org.onebusaway.android.extrapolation.data

import android.util.LruCache
import androidx.annotation.VisibleForTesting
import org.onebusaway.android.io.elements.ObaTrip
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/*
 * The store of per-trip state: an LRU cache of immutable TripState snapshots keyed by tripId,
 * plus recording and fetch-writeback — and nothing else. Identity is the key: consumers hold a
 * tripId and call lookupTripState per frame/tick (an uncontended synchronized map get; cheap
 * enough for per-frame loops), getting a consistent immutable snapshot or null. Network I/O
 * lives in the pollers (Pollers.kt) and the pure fetchers (Fetchers.kt); hydrating call sites
 * write results back in here, where every record and put call is a pure fold producing a new
 * snapshot.
 *
 * Retention: lookups and writes both promote, so a trip being actively displayed or polled is
 * never the eviction victim; once more than MAX_TRACKED_TRIPS trips are tracked, the
 * least-recently-used trip is dropped and refills if it is ever recorded again.
 *
 * Threading: main thread only. The cache is internally synchronized, but writes are
 * get-fold-put — not atomic — so all public functions must be called from the main thread.
 * Background work (network fetches) lives in Pollers.kt and Fetchers.kt; results are posted
 * back to the main thread before they touch any state here.
 */

private const val MAX_TRACKED_TRIPS = 100

private val trips = LruCache<String, TripState>(MAX_TRACKED_TRIPS)

/**
 * The current snapshot for [tripId], or null if the trip has never been recorded (or has been
 * evicted). Promotes the trip in the retention order.
 */
fun lookupTripState(tripId: String?): TripState? = tripId?.let { trips.get(it) }

/** Applies [fold] to the current snapshot for [tripId] (or a fresh empty one) and stores it. */
private inline fun update(tripId: String, fold: (TripState) -> TripState) {
    trips.put(tripId, fold(trips.get(tripId) ?: TripState.empty(tripId)))
}

// --- Recording ---

/**
 * Records a trip status snapshot. Deduplicates history by distance — only adds an entry when the
 * vehicle has moved.
 */
fun recordStatus(status: ObaTripStatus?, serverTimeMs: Long, localTimeMs: Long) {
    if (status == null) return
    val tripId = status.activeTripId ?: return
    update(tripId) { it.recorded(status, serverTimeMs, localTimeMs) }
}

fun recordTripDetailsResponse(
        polledTripId: String?,
        response: ObaTripDetailsResponse?,
        localTimeMs: Long
) {
    if (response == null) return
    val status = response.status ?: return
    if (polledTripId != null) {
        update(polledTripId) {
            it.copy(vehicleActiveTripId = status.activeTripId, tripDetailsResponse = response)
        }
    }
    val activeTripId = status.activeTripId ?: return
    update(activeTripId) {
        it.recorded(status, response.currentTime, localTimeMs)
                .withServiceDate(status.serviceDate)
    }
}

fun recordTripsForRouteResponse(response: ObaTripsForRouteResponse, localTimeMs: Long) {
    val serverTime = response.currentTime
    response.forEachActiveTrip { tripId, status, activeTrip ->
        val route = activeTrip.routeId?.let { response.getRoute(it) }
        update(tripId) {
            it.recorded(status, serverTime, localTimeMs)
                    .withServiceDate(status.serviceDate)
                    .withRouteType(route?.type)
        }
    }
}

// --- Fetch-writeback ---

fun putSchedule(tripId: String?, schedule: ObaTripSchedule?) {
    if (tripId != null && schedule != null) {
        update(tripId) { it.copy(schedule = schedule) }
    }
}

fun putServiceDate(tripId: String?, serviceDate: Long) {
    if (tripId != null && serviceDate > 0) {
        update(tripId) { it.withServiceDate(serviceDate) }
    }
}

fun putPolyline(tripId: String, polyline: Polyline) {
    update(tripId) { it.copy(polyline = polyline) }
}

// --- Introspection and cleanup ---

/** IDs of the trips currently tracked (the eviction working set). */
fun getTrackedTripIds(): Set<String> = trips.snapshot().keys

/** Drops all tracked trips. For tests only. */
@VisibleForTesting
fun clearAllTrips() {
    trips.evictAll()
}

/**
 * Iterates the active trips in a trips-for-route response, skipping entries without a status, an
 * active trip ID, or a matching trip reference. Shared by [recordTripsForRouteResponse] and the
 * route poller's backfill so both walk the response identically.
 */
internal inline fun ObaTripsForRouteResponse.forEachActiveTrip(
        block: (tripId: String, status: ObaTripStatus, activeTrip: ObaTrip) -> Unit
) {
    for (tripDetails in trips) {
        val status = tripDetails.status ?: continue
        val tripId = status.activeTripId ?: continue
        val activeTrip = getTrip(tripId) ?: continue
        block(tripId, status, activeTrip)
    }
}
