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

/**
 * [anchorDirectionId] (resolve the launch anchor stop → its direction) and [stopsForDirection]
 * (narrow a route's stops to a direction) — the pure repository/controller direction helpers.
 */
class RouteDirectionFocusTest {

    private fun stop(id: String, vararg directions: Int) =
        RouteMapStop(ObaStopElement(id = id), directions.toSet())

    // Outbound (direction 0) serves a/b, inbound (direction 1) serves c/d.
    private val stops = listOf(stop("a", 0), stop("b", 0), stop("c", 1), stop("d", 1))

    // ----- anchorDirectionId -----

    @Test
    fun nullAnchorResolvesNoDirection() {
        assertNull(stops.anchorDirectionId(anchorStopId = null))
    }

    @Test
    fun anchorInOneDirectionResolvesItsId() {
        assertEquals(1, stops.anchorDirectionId(anchorStopId = "c"))
        assertEquals(0, stops.anchorDirectionId(anchorStopId = "a"))
    }

    @Test
    fun ambiguousSharedAnchorResolvesNoDirection() {
        // Anchoring on a stop that serves both directions is ambiguous -> whole route.
        val withShared = stops + stop("s", 0, 1)
        assertNull(withShared.anchorDirectionId(anchorStopId = "s"))
    }

    @Test
    fun anchorNotAmongStopsResolvesNoDirection() {
        assertNull(stops.anchorDirectionId(anchorStopId = "zzz"))
    }

    @Test
    fun ungroupedAnchorResolvesNoDirection() {
        // A stop with no direction membership (route without a numeric direction grouping) -> whole route.
        val withUngrouped = stops + stop("u")
        assertNull(withUngrouped.anchorDirectionId(anchorStopId = "u"))
    }

    // ----- stopsForDirection -----

    @Test
    fun nullDirectionKeepsWholeRoute() {
        assertEquals(listOf("a", "b", "c", "d"), stops.stopsForDirection(null).map { it.id })
    }

    @Test
    fun directionFiltersToItsStops() {
        assertEquals(listOf("c", "d"), stops.stopsForDirection(1).map { it.id })
    }

    @Test
    fun filteredStopsPreserveSourceOrder() {
        // A source order of d before c must survive the filter to direction 1.
        val reordered = listOf(stop("d", 1), stop("c", 1), stop("a", 0))
        assertEquals(listOf("d", "c"), reordered.stopsForDirection(1).map { it.id })
    }

    @Test
    fun sharedStopInTargetDirectionIsIncluded() {
        // A stop serving both directions shows for the selected one (not dropped).
        val withShared = stops + stop("s", 0, 1)
        assertEquals(listOf("a", "b", "s"), withShared.stopsForDirection(0).map { it.id })
    }
}
