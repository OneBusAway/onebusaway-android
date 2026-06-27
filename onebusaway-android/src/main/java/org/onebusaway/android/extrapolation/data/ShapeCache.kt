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

import org.onebusaway.android.util.Polyline

/**
 * Bound matches the trip LRU's [MAX_TRACKED_TRIPS]: distinct shapes can't outnumber the trips that
 * reference them, so a cache this size never evicts a shape a live trip still holds — that
 * guarantee is what keeps the dedup complete (a smaller bound could evict a still-referenced shape
 * and force a sibling trip to re-fetch a second copy). Orphaned shapes are likewise retained no
 * longer than the same ceiling.
 */
internal const val MAX_CACHED_SHAPES = MAX_TRACKED_TRIPS

/**
 * A bounded, access-ordered LRU of [Polyline] shapes keyed by shapeId, owned by
 * [DefaultTripObservationRepository]. Trips that ride the same route share one shapeId, so caching
 * by shape — rather than letting every trip's [TripState] hold its own copy — collapses those into
 * a single shared [Polyline] instance, deduplicating the (for rail, sizable) shapes across the up
 * to [MAX_TRACKED_TRIPS] trips the store tracks.
 *
 * Synchronized for the same reason [TripStateCache] is: lookups and writes both promote, so the
 * access-order map mutates on read, and callers arrive on arbitrary threads — all access must be
 * serialized.
 */
internal class ShapeCache {

    private val shapes =
            object : LinkedHashMap<String, Polyline>(16, 0.75f, /* accessOrder = */ true) {
                override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, Polyline>
                ): Boolean = size > MAX_CACHED_SHAPES
            }

    /** The cached shape for [shapeId], or null if it was never cached (or has been evicted). */
    @Synchronized
    fun get(shapeId: String): Polyline? = shapes[shapeId]

    /** Stores [polyline] under [shapeId], promoting it in the retention order. */
    @Synchronized
    fun put(shapeId: String, polyline: Polyline) {
        shapes[shapeId] = polyline
    }
}
