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
package org.onebusaway.android.ui.nav

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.map.ShowRouteRequest

/**
 * Unit tests for the typed reveal reads + atomic consumes on the HOME [SavedStateHandle]:
 * [SavedStateHandle.consumeStopReveal] (the `RESULT_MAP_STOP_*` keys [NavController.revealStopOnMap]
 * writes) and the [putRouteReveal]/[consumeRouteReveal] round trip (the `RESULT_MAP_ROUTE_*` keys),
 * which must carry every [ShowRouteRequest] field across the navigation hop.
 */
class MapRevealTest {

    @Test
    fun `route reveal round-trips every ShowRouteRequest field`() {
        val handle = SavedStateHandle()
        val request = ShowRouteRequest(
            routeId = "1_100",
            directionStopId = "1_75403",
            focusTripId = "1_604112894",
            initialDirectionId = 1,
        )

        handle.putRouteReveal(request)

        assertEquals(request, handle.consumeRouteReveal())
    }

    @Test
    fun `route reveal round-trips a plain whole-route request`() {
        val handle = SavedStateHandle()

        handle.putRouteReveal(ShowRouteRequest("1_100"))

        assertEquals(ShowRouteRequest("1_100"), handle.consumeRouteReveal())
    }

    @Test
    fun `route consume clears every reveal key`() {
        val handle = SavedStateHandle()
        handle.putRouteReveal(
            ShowRouteRequest("1_100", "1_75403", focusTripId = "t", initialDirectionId = 0)
        )

        handle.consumeRouteReveal()

        assertNull(handle.consumeRouteReveal())
        assertNull(handle.get<String>(RESULT_MAP_ROUTE_DIRECTION_STOP_ID))
        assertNull(handle.get<String>(RESULT_MAP_ROUTE_FOCUS_TRIP_ID))
        assertNull(handle.get<Int>(RESULT_MAP_ROUTE_INITIAL_DIRECTION_ID))
    }

    @Test
    fun `reads a complete stop reveal and clears all three keys`() {
        val handle = SavedStateHandle(
            mapOf(
                RESULT_MAP_STOP_ID to "stop_1",
                RESULT_MAP_STOP_LAT to 47.6,
                RESULT_MAP_STOP_LON to -122.3,
            )
        )

        val reveal = handle.consumeStopReveal()

        assertEquals(StopReveal("stop_1", 47.6, -122.3), reveal)
        assertNull(handle.get<String>(RESULT_MAP_STOP_ID))
        assertNull(handle.get<Double>(RESULT_MAP_STOP_LAT))
        assertNull(handle.get<Double>(RESULT_MAP_STOP_LON))
    }

    @Test
    fun `drops a partial reveal but still consumes the keys`() {
        // The corrupted / half-restored case: a stop id without its coordinates.
        val handle = SavedStateHandle(mapOf(RESULT_MAP_STOP_ID to "stop_1"))

        val reveal = handle.consumeStopReveal()

        assertNull(reveal)
        assertNull(handle.get<String>(RESULT_MAP_STOP_ID))
    }

    @Test
    fun `returns null when nothing was staged`() {
        assertNull(SavedStateHandle().consumeStopReveal())
    }
}
