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
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.util.Polyline

/*
 * The store of per-trip state: an LRU cache of immutable TripState snapshots keyed by tripId —
 * and nothing else. Identity is the key: consumers hold a tripId and call lookupTripState per
 * frame/tick (an uncontended synchronized map get; cheap enough for per-frame loops), getting a
 * consistent immutable snapshot or null. Writes are data-shaped — record takes the standard
 * TripObservation, the put functions take single resources — and never read into API response
 * shapes; distilling responses into observations is the adapters' job (Adapters.kt).
 * (putTripDetailsResponse does accept a whole response, but stores it as an opaque value for UI
 * consumers — the store never looks inside it.) Network I/O lives in the pollers (Pollers.kt)
 * and the pure fetchers (Fetchers.kt).
 *
 * Retention: lookups and writes both promote, so a trip being actively displayed or polled is
 * never the eviction victim; once more than MAX_TRACKED_TRIPS trips are tracked, the
 * least-recently-used trip is dropped and refills if it is ever recorded again.
 *
 * Threading: main thread only. The cache is internally synchronized, but writes are
 * get-transform-put — not atomic — so all public functions must be called from the main thread.
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

/**
 * Applies [transform] to the current snapshot for [tripId] (or a fresh empty one) and stores the
 * result — the `compute` that LruCache doesn't provide.
 */
private inline fun update(tripId: String, transform: (TripState) -> TripState) {
    trips.put(tripId, transform(trips.get(tripId) ?: TripState.empty(tripId)))
}

// --- Writes ---

/** Records [observation] into its trip's snapshot. */
fun record(observation: TripObservation, localTimeMs: Long) {
    update(observation.tripId) { it.withObservation(observation, localTimeMs) }
}

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

/**
 * Caches a polled trip details response on [polledTripId], along with the trip the vehicle
 * reported as active (which differs from [polledTripId] once the vehicle rolls onto its next
 * run). [vehicleActiveTripId] is null when the response carried no vehicle status.
 */
fun putTripDetailsResponse(
        polledTripId: String,
        vehicleActiveTripId: String?,
        response: ObaTripDetailsResponse
) {
    update(polledTripId) {
        it.copy(vehicleActiveTripId = vehicleActiveTripId, tripDetailsResponse = response)
    }
}

// --- Introspection and cleanup ---

/** IDs of the trips currently tracked (the eviction working set). */
fun getTrackedTripIds(): Set<String> = trips.snapshot().keys

/** Drops all tracked trips. For tests only. */
@VisibleForTesting
fun clearAllTrips() {
    trips.evictAll()
}
