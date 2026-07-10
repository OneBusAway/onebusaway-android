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
 * delegate to — the easily-broken part of the data-shaping: [trimToNearest] bounds the accumulation to
 * the stops nearest the viewport centre (never the focused stop), [evictStaleInViewport] cache-busts
 * stops a complete response omitted (#1754), and the same focus-retention drives `clearStops(false)`.
 * Split into pure functions over plain [StopMarker]s so it runs on the JVM — the per-stop marker build
 * (which touches an Android `Location`) is exercised on device.
 */
class StopAccumulationTest {

    private fun marker(id: String, lat: Double = 0.0, lon: Double = 0.0): StopMarker =
        StopMarker(id, GeoPoint(lat, lon), "null", ObaRoute.TYPE_BUS, ObaStopElement(id, lat, lon, id, id))

    private fun accumOf(vararg ids: String): LinkedHashMap<String, StopMarker> =
        LinkedHashMap<String, StopMarker>().apply { ids.forEach { put(it, marker(it)) } }

    private fun accumOf(vararg markers: StopMarker): LinkedHashMap<String, StopMarker> =
        LinkedHashMap<String, StopMarker>().apply { markers.forEach { put(it.id, it) } }

    private fun LinkedHashMap<String, StopMarker>.ids() = keys.toSet()

    // --- trimToNearest (bound to the cap nearest the viewport centre) ---

    @Test
    fun `at or below the cap is a no-op`() {
        val accum = accumOf("a", "b", "c")
        trimToNearest(accum, GeoPoint(0.0, 0.0), cap = 3, focusedId = "a")
        assertEquals(setOf("a", "b", "c"), accum.ids())
    }

    @Test
    fun `over the cap keeps the stops nearest the center`() {
        // center at (0,0): near < mid < far by longitude distance.
        val accum = accumOf(marker("near", 0.0, 1.0), marker("mid", 0.0, 3.0), marker("far", 0.0, 9.0))
        trimToNearest(accum, GeoPoint(0.0, 0.0), cap = 2, focusedId = null)
        assertEquals(setOf("near", "mid"), accum.ids())   // the farthest is dropped
    }

    @Test
    fun `over the cap never evicts the focused stop even when it is farthest`() {
        val accum = accumOf(marker("near", 0.0, 1.0), marker("mid", 0.0, 3.0), marker("far", 0.0, 9.0))
        // "far" would be dropped by distance, but being focused it's kept and counts toward the cap.
        trimToNearest(accum, GeoPoint(0.0, 0.0), cap = 2, focusedId = "far")
        assertEquals(setOf("near", "far"), accum.ids())
    }

    // --- evictStaleInViewport (the complete-response cache-bust) ---

    @Test
    fun `evicts in-viewport stops the complete response omitted`() {
        val accum = accumOf(marker("kept", 0.0, 0.0), marker("stale", 1.0, 1.0))
        // Viewport covers both; the response only carried "kept" — "stale" is dropped.
        evictStaleInViewport(
            accum, southWest = GeoPoint(-2.0, -2.0), northEast = GeoPoint(2.0, 2.0),
            present = setOf("kept"), focusedId = null,
        )
        assertEquals(setOf("kept"), accum.ids())
    }

    @Test
    fun `keeps omitted stops outside the viewport, and the focused stop`() {
        val accum = accumOf(
            marker("outside", 10.0, 10.0),   // omitted but out of the box → not the response's concern
            marker("focused", 1.0, 1.0),     // omitted + in the box, but focused → kept
            marker("stale", 0.5, 0.5),       // omitted + in the box → dropped
        )
        evictStaleInViewport(
            accum, southWest = GeoPoint(-2.0, -2.0), northEast = GeoPoint(2.0, 2.0),
            present = emptySet(), focusedId = "focused",
        )
        assertEquals(setOf("outside", "focused"), accum.ids())
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
