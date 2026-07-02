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
package org.onebusaway.android.map.googlemapsv2

import android.graphics.Bitmap
import androidx.collection.LruCache

/**
 * Memoizes the [V] wrapper (a Google `BitmapDescriptor`, or a counting stand-in in tests) for a marker
 * icon, keyed by a **stable logical key** rather than the bitmap instance. The motivating case is
 * `GoogleMapRenderer`'s vehicle reconcile, which re-stamps a gliding vehicle's icon on each heading-octant
 * change at ~20Hz — hundreds of `BitmapDescriptorFactory.fromBitmap(...)` allocations/sec on a busy route
 * without this.
 *
 * Keying by a logical id (e.g. type+octant+color), not by the [Bitmap] itself, is deliberate: the source
 * bitmap caches are small, bounded LRUs, so on a busy route they evict and re-create the same logical icon
 * as a *new* Bitmap instance — identity keying would inherit that thrash and defeat the cache. With a
 * logical key, [get] reuses the descriptor across that churn, and the bitmap supplier (the expensive
 * decode/tint that produces the Bitmap) runs **only on a miss** — so a steady-state hot path does no bitmap
 * work at all.
 *
 * Bounded by the constructor's `maxSize`: a still-shown marker retains its own native texture, so evicting
 * an entry here is harmless (it's just re-wrapped on next request). Generic over [V] purely for
 * testability — [wrap] is the one and only allocator, so a test can pass a counting fake and assert
 * wrappers stay bounded.
 *
 * **Not thread-safe.** Both [get] and [clear] are non-atomic check-then-act over the backing LRU and are
 * expected to run on the main thread only (the renderer's reconcile/draw path), so no synchronization.
 */
class BitmapDescriptorCache<V : Any>(maxSize: Int, private val wrap: (Bitmap) -> V) {

    private val cache = LruCache<String, V>(maxSize)

    /**
     * The cached wrapper for [key], invoking [bitmap] and wrapping its result only on a cache miss.
     *
     * @param bitmap supplies the source [Bitmap] to wrap; called only when [key] is absent.
     */
    fun get(key: String, bitmap: () -> Bitmap): V =
        cache.get(key) ?: wrap(bitmap()).also { cache.put(key, it) }

    /** Drop every wrapper, releasing the native textures once the markers using them are gone. */
    fun clear() = cache.evictAll()
}
