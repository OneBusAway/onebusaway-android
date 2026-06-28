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

import org.onebusaway.android.api.adapters.ObaStopElement

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopMarker

/**
 * Unit tests for the stop-accumulation logic [StopsMapController.showStops] / [StopsMapController.clearStops]
 * delegate to — the easily-broken part of the data-shaping: the LRU trim evicts least-recently-used
 * stops down to the 200-cap *but never the focused stop*, and the same focus-retention drives
 * `clearStops(false)`. Split into pure functions ([trimStopCache] / [retainOnlyFocusedStop]) over
 * plain [StopMarker]s so it runs on the JVM — the per-stop marker build (which touches an Android
 * `Location`) is exercised on device.
 */
class StopAccumulationTest {

    private fun marker(id: String): StopMarker =
        StopMarker(id, GeoPoint(0.0, 0.0), "null", ObaRoute.TYPE_BUS, ObaStopElement(id, 0.0, 0.0, id, id))

    private fun accumOf(vararg ids: String): LinkedHashMap<String, StopMarker> =
        LinkedHashMap<String, StopMarker>().apply { ids.forEach { put(it, marker(it)) } }

    private fun LinkedHashMap<String, StopMarker>.ids() = keys.toSet()

    // --- trimStopCache (the LRU eviction; accumOf is insertion- = eldest-first order) ---

    @Test
    fun `at or below the cap is a no-op`() {
        val accum = accumOf("a", "b", "c")
        trimStopCache(accum, focusedId = "a", cap = 3)
        assertEquals(setOf("a", "b", "c"), accum.ids())
    }

    @Test
    fun `over the cap evicts eldest-first down to the cap`() {
        val accum = accumOf("a", "b", "c", "d")
        trimStopCache(accum, focusedId = null, cap = 2)
        assertEquals(setOf("c", "d"), accum.ids())
    }

    @Test
    fun `over the cap never evicts the focused stop`() {
        val accum = accumOf("a", "b", "c", "d")
        // "a" is the eldest, but being focused it's skipped; the next-eldest evict instead.
        trimStopCache(accum, focusedId = "a", cap = 2)
        assertEquals(setOf("a", "d"), accum.ids())
    }

    @Test
    fun `over the cap with no focus evicts pure eldest`() {
        val accum = accumOf("a", "b", "c")
        trimStopCache(accum, focusedId = null, cap = 1)
        assertEquals(setOf("c"), accum.ids())
    }

    @Test
    fun `the access-ordered LRU bumps a re-seen stop so it outlives an untouched eldest`() {
        // The eviction is only "least-recently-USED" because stopAccum is access-ordered and showStops
        // re-touches each fetched stop. Build the map exactly as StopsMapController.stopAccum (access
        // order) and re-see the insertion-eldest "a" the way showStops does (getOrPut's get() on a hit
        // bumps it to most-recently-used), leaving "b" the eldest.
        val accum = LinkedHashMap<String, StopMarker>(16, 0.75f, true)
        listOf("a", "b", "c").forEach { accum[it] = marker(it) }
        accum.getOrPut("a") { marker("a") }

        trimStopCache(accum, focusedId = null, cap = 2)

        // LRU: the now-eldest "b" is evicted and the bumped "a" survives. A plain (insertion-ordered)
        // map — or dropping the getOrPut bump — would instead evict the first-inserted "a" and keep "b",
        // so this test fails if stopAccum loses its access-order config or showStops stops re-touching.
        assertEquals(setOf("a", "c"), accum.ids())
    }

    // --- retainOnlyFocusedStop (the clearStops(false) path) ---

    @Test
    fun `retain keeps only the focused stop`() {
        val accum = accumOf("a", "b", "c")
        retainOnlyFocusedStop(accum, focusedId = "c")
        assertEquals(setOf("c"), accum.ids())
    }

    @Test
    fun `retain with no focus empties the accumulation`() {
        val accum = accumOf("a", "b")
        retainOnlyFocusedStop(accum, focusedId = null)
        assertEquals(emptySet<String>(), accum.ids())
    }
}
