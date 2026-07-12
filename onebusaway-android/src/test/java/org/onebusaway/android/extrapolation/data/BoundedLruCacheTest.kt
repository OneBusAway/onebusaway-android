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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the generic [BoundedLruCache] shell (no Android dependencies). */
class BoundedLruCacheTest {

    @Test
    fun `returns the stored value for a hit and null for a miss`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache.put("a", 1)

        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `evicts the least-recently-used entry past the bound`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a") // promote a to most-recently-used
        cache.put("c", 3) // overflow -> evicts the eldest, now b

        assertNull("the least-recently-used entry is evicted", cache.get("b"))
        assertEquals("the promoted entry survives eviction", 1, cache.get("a"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun `keys reflects the current working set`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3) // evicts a

        val keys = cache.keys()
        assertEquals(2, keys.size)
        assertFalse("a" in keys)
        assertTrue("b" in keys)
        assertTrue("c" in keys)
    }

    @Test
    fun `clear drops all entries`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache.put("a", 1)

        cache.clear()

        assertNull(cache.get("a"))
        assertTrue(cache.keys().isEmpty())
    }

    @Test
    fun `compute seeds from default when absent and returns the new value`() {
        val cache = BoundedLruCache<String, Int>(2)

        val result = cache.compute("a", default = { 0 }) { it + 1 }

        assertEquals(1, result)
        assertEquals(1, cache.get("a"))
    }

    @Test
    fun `compute transforms the existing value rather than the default`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache.put("a", 5)

        val result = cache.compute("a", default = { 0 }) { it + 1 }

        assertEquals(6, result)
        assertEquals(6, cache.get("a"))
    }
}
