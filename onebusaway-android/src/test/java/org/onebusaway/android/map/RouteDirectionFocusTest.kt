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
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.models.RouteMapStop

/** [focusDirection] — the pure "narrow a route to the stop-relevant direction" repository helper. */
class RouteDirectionFocusTest {

    private fun stop(id: String, vararg directions: Int) =
        RouteMapStop(ObaStopElement(id = id), directions.toSet())

    // Outbound (direction 0) serves a/b, inbound (direction 1) serves c/d.
    private val stops = listOf(stop("a", 0), stop("b", 0), stop("c", 1), stop("d", 1))

    @Test
    fun nullAnchorKeepsWholeRoute() {
        val focus = stops.focusDirection(anchorStopId = null)
        assertEquals(listOf("a", "b", "c", "d"), focus.stops.map { it.id })
        assertNull(focus.directionId)
    }

    @Test
    fun anchorInOneDirectionFiltersStopsAndResolvesId() {
        val focus = stops.focusDirection(anchorStopId = "c")
        assertEquals(listOf("c", "d"), focus.stops.map { it.id })
        assertEquals(1, focus.directionId)
    }

    @Test
    fun filteredStopsPreserveSourceOrder() {
        // A source order of d before c must survive the filter to direction 1.
        val reordered = listOf(stop("d", 1), stop("c", 1), stop("a", 0))
        assertEquals(listOf("d", "c"), reordered.focusDirection("c").stops.map { it.id })
    }

    @Test
    fun sharedStopInTargetDirectionIsIncluded() {
        // A stop serving both directions shows for the tapped one (not dropped).
        val withShared = stops + stop("s", 0, 1)
        val focus = withShared.focusDirection(anchorStopId = "a")
        assertEquals(listOf("a", "b", "s"), focus.stops.map { it.id })
        assertEquals(0, focus.directionId)
    }

    @Test
    fun ambiguousSharedAnchorKeepsWholeRoute() {
        // Anchoring on a stop that serves both directions is ambiguous -> whole route.
        val withShared = stops + stop("s", 0, 1)
        val focus = withShared.focusDirection(anchorStopId = "s")
        assertEquals(5, focus.stops.size)
        assertNull(focus.directionId)
    }

    @Test
    fun anchorNotAmongStopsKeepsWholeRoute() {
        val focus = stops.focusDirection(anchorStopId = "zzz")
        assertEquals(4, focus.stops.size)
        assertNull(focus.directionId)
    }

    @Test
    fun ungroupedAnchorKeepsWholeRoute() {
        // A stop with no direction membership (route without a numeric direction grouping) -> whole route.
        val withUngrouped = stops + stop("u")
        val focus = withUngrouped.focusDirection(anchorStopId = "u")
        assertEquals(5, focus.stops.size)
        assertNull(focus.directionId)
    }
}
