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
package org.onebusaway.android.extrapolation.data

import org.onebusaway.android.models.TripRouteInfo

/**
 * A bounded, access-ordered LRU of [TripRouteInfo] keyed by trip id, owned by
 * [DefaultTripObservationRepository]. Backs [TripObservationRepository.resolveNeighborTrip]'s
 * tap-driven memoization (#1691): a trip's route/shape never changes, so once resolved it's cheap to
 * re-select the same vehicle. Sized like [ShapeCache] against the same [MAX_TRACKED_TRIPS] ceiling —
 * this is a much smaller, occasional cache (populated only on a vehicle selection, not every poll), so
 * eviction pressure here is negligible in practice.
 */
internal class NeighborTripCache {

    private val trips =
            object : LinkedHashMap<String, TripRouteInfo>(16, 0.75f, /* accessOrder = */ true) {
                override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, TripRouteInfo>
                ): Boolean = size > MAX_TRACKED_TRIPS
            }

    /** The cached route info for [tripId], or null if never resolved (or evicted). */
    @Synchronized
    fun get(tripId: String): TripRouteInfo? = trips[tripId]

    /** Stores [info] under [tripId], promoting it in the retention order. */
    @Synchronized
    fun put(tripId: String, info: TripRouteInfo) {
        trips[tripId] = info
    }
}
