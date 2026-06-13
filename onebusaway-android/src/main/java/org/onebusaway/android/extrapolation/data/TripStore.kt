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
package org.onebusaway.android.extrapolation.data

import android.util.LruCache
import androidx.annotation.VisibleForTesting
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.util.Polyline

private const val MAX_TRACKED_TRIPS = 100

/**
 * An LRU cache of immutable TripState snapshots keyed by tripId. Data flows one way: the
 * adapters (Adapters.kt) distill API responses into data-shaped writes, the pollers (Pollers.kt)
 * record them; UI code only reads, via [lookupTripState] — a synchronized map get, cheap enough
 * for per-frame loops. Lookups and writes both promote, so actively watched trips are never the
 * eviction victims.
 *
 * Threading: main thread only — writes are get-transform-put, not atomic.
 */
object TripStore {

    private val trips = LruCache<String, TripState>(MAX_TRACKED_TRIPS)

    /**
     * The current snapshot for [tripId], or null if the trip has never been recorded (or has
     * been evicted). Promotes the trip in the retention order.
     */
    @JvmStatic
    fun lookupTripState(tripId: String?): TripState? = tripId?.let { trips.get(it) }

    /**
     * Applies [transform] to the current snapshot for [tripId] (or a fresh empty one) and stores
     * the result — the `compute` that LruCache doesn't provide.
     */
    private inline fun update(tripId: String, transform: (TripState) -> TripState) {
        trips.put(tripId, transform(trips.get(tripId) ?: TripState.empty(tripId)))
    }

    // --- Writes ---

    /** Records [observation] into its trip's snapshot. */
    @JvmStatic
    fun record(observation: TripObservation, localTimeMs: Long) {
        update(observation.tripId) { it.withObservation(observation, localTimeMs) }
    }

    @JvmStatic
    fun putSchedule(tripId: String?, schedule: ObaTripSchedule?) {
        if (tripId != null && schedule != null) {
            update(tripId) { it.withSchedule(schedule) }
        }
    }

    @JvmStatic
    fun putServiceDate(tripId: String?, serviceDate: Long) {
        if (tripId != null && serviceDate > 0) {
            update(tripId) { it.withServiceDate(serviceDate) }
        }
    }

    @JvmStatic
    fun putPolyline(tripId: String, polyline: Polyline) {
        update(tripId) { it.copy(polyline = polyline) }
    }

    /**
     * Caches a polled trip details response on [polledTripId], along with the trip the vehicle
     * reported as active (which differs from [polledTripId] once the vehicle rolls onto its next
     * run). [vehicleActiveTripId] is null when the response carried no vehicle status.
     */
    @JvmStatic
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
    @JvmStatic
    fun getTrackedTripIds(): Set<String> = trips.snapshot().keys

    /** Drops all tracked trips. For tests only. */
    @JvmStatic
    @VisibleForTesting
    fun clearAllTrips() {
        trips.evictAll()
    }
}
