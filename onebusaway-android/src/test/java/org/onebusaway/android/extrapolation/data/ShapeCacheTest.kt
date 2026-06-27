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

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.util.Polyline

/** Unit tests for the bounded, shapeId-keyed [ShapeCache] (no Android dependencies). */
class ShapeCacheTest {

    /** An empty-point polyline is enough to exercise caching; construction touches no Android API. */
    private fun polyline() = Polyline(emptyList())

    @Test
    fun `returns the stored instance for a hit and null for a miss`() {
        val cache = ShapeCache()
        val shape = polyline()
        cache.put("shapeA", shape)

        assertSame("a hit returns the cached instance", shape, cache.get("shapeA"))
        assertNull("a miss returns null", cache.get("shapeB"))
    }

    @Test
    fun `evicts the least-recently-used shape past the bound`() {
        val cache = ShapeCache()
        val shape0 = polyline()
        // Fill to the bound, then touch shape0 so it isn't the eviction victim.
        cache.put("shape0", shape0)
        for (i in 1 until MAX_CACHED_SHAPES) cache.put("shape$i", polyline())
        cache.get("shape0") // promote shape0 to most-recently-used
        cache.put("overflow", polyline()) // overflow -> evicts the eldest, now shape1

        assertNull("the least-recently-used shape is evicted", cache.get("shape1"))
        assertSame("the promoted shape survives eviction", shape0, cache.get("shape0"))
    }
}
