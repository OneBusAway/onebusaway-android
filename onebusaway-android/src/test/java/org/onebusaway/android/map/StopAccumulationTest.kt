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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.elements.ObaStopElement
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopMarker

/**
 * Unit tests for the stop-accumulation logic [MapViewModel.showStops] / [MapViewModel.clearStops]
 * delegate to — the easily-broken part of the data-shaping: the 200-cap clears the accumulation *but
 * keeps the focused stop*, and the same focus-retention drives `clearStops(false)`. Split into pure
 * functions ([capStopAccumulation] / [retainOnlyFocusedStop]) over plain [StopMarker]s so it runs on
 * the JVM — the per-stop marker build (which touches an Android `Location`) is exercised on device.
 */
class StopAccumulationTest {

    private fun marker(id: String): StopMarker =
        StopMarker(id, GeoPoint(0.0, 0.0), "null", ObaRoute.TYPE_BUS, ObaStopElement(id, 0.0, 0.0, id, id))

    private fun accumOf(vararg ids: String): LinkedHashMap<String, StopMarker> =
        LinkedHashMap<String, StopMarker>().apply { ids.forEach { put(it, marker(it)) } }

    private fun LinkedHashMap<String, StopMarker>.ids() = keys.toSet()

    // --- capStopAccumulation ---

    @Test
    fun `below the cap is a no-op`() {
        val accum = accumOf("a", "b")
        capStopAccumulation(accum, focusedId = "a", cap = 5)
        assertEquals(setOf("a", "b"), accum.ids())
    }

    @Test
    fun `at the cap clears the accumulation but keeps the focused stop`() {
        val accum = accumOf("a", "b", "c")
        capStopAccumulation(accum, focusedId = "b", cap = 3)
        assertEquals(setOf("b"), accum.ids())
    }

    @Test
    fun `at the cap with no focus clears everything`() {
        val accum = accumOf("a", "b", "c")
        capStopAccumulation(accum, focusedId = null, cap = 3)
        assertEquals(emptySet<String>(), accum.ids())
    }

    @Test
    fun `at the cap with a focus id that is not accumulated clears everything`() {
        val accum = accumOf("a", "b", "c")
        capStopAccumulation(accum, focusedId = "gone", cap = 3)
        assertEquals(emptySet<String>(), accum.ids())
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
