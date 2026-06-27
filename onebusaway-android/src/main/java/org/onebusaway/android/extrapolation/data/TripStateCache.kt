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

import androidx.annotation.VisibleForTesting
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.util.Polyline

internal const val MAX_TRACKED_TRIPS = 100

/**
 * An access-ordered LRU cache of immutable [TripState] snapshots keyed by tripId, owned by
 * [DefaultTripObservationRepository] (one instance per app). Data flows one way: the adapters
 * (Adapters.kt) distill API responses into [TripObservation]s, the repository's pollers record
 * them; consumers only read, via [lookupTripState] — a cheap map get, fine for per-frame loops.
 * Lookups and writes both promote, so actively watched trips are never the eviction victims.
 *
 * Backed by a plain access-order [LinkedHashMap] (not `android.util.LruCache`) so the data layer
 * carries no Android dependency and is exercisable in JVM unit tests. Every method is
 * `@Synchronized`: writes are get-transform-put rather than atomic, and the access-order map
 * mutates on read, so all access is serialized.
 */
internal class TripStateCache {

    private val trips =
            object : LinkedHashMap<String, TripState>(16, 0.75f, /* accessOrder = */ true) {
                override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, TripState>
                ): Boolean = size > MAX_TRACKED_TRIPS
            }

    /**
     * The current snapshot for [tripId], or null if the trip has never been recorded (or has
     * been evicted). Promotes the trip in the retention order.
     */
    @Synchronized
    fun lookupTripState(tripId: String?): TripState? = tripId?.let { trips[it] }

    /**
     * Applies [transform] to the current snapshot for [tripId] (or a fresh empty one) and stores
     * the result — the compute-and-put that the map doesn't provide.
     */
    @Synchronized
    private fun update(tripId: String, transform: (TripState) -> TripState) {
        trips[tripId] = transform(trips[tripId] ?: TripState.empty(tripId))
    }

    // --- Writes ---

    /** Records [observation] into its trip's snapshot. */
    fun record(observation: TripObservation, localTimeMs: Long) {
        update(observation.tripId) { it.withObservation(observation, localTimeMs) }
    }

    fun putSchedule(tripId: String?, schedule: ObaTripSchedule?) {
        if (tripId != null && schedule != null) {
            update(tripId) { it.withSchedule(schedule) }
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
    @Synchronized
    fun getTrackedTripIds(): Set<String> = trips.keys.toSet()

    /** Drops all tracked trips. For tests only. */
    @Synchronized
    @VisibleForTesting
    fun clearAllTrips() {
        trips.clear()
    }
}
