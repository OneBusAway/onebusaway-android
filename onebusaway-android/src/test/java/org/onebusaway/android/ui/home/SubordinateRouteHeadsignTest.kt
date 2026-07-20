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
package org.onebusaway.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests for [subordinateRouteHeadsign] — the focus banner's subordinate-route headsign resolution. */
class SubordinateRouteHeadsignTest {

    private fun selection(originHeadsign: String?, routeId: String = "1_100", directionId: Int? = 0) =
        StopRouteSelection(
            originHeadsign = originHeadsign,
            legs = listOf(RouteLeg(routeId = routeId, shortName = "8", directionId = directionId)),
        )

    @Test
    fun prefersOriginHeadsign_withoutConsultingArrivals() {
        var consulted = false
        val result = subordinateRouteHeadsign(selection(originHeadsign = "Capitol Hill")) { _, _ ->
            consulted = true
            "Downtown"
        }
        assertEquals("Capitol Hill", result)
        assertEquals(false, consulted)
    }

    @Test
    fun resolvesFromArrivals_whenOriginHeadsignAbsent() {
        // The map route-label entry leaves originHeadsign null; resolve by the leg's (route, direction).
        val result = subordinateRouteHeadsign(selection(originHeadsign = null, routeId = "1_100", directionId = 1)) { routeId, directionId ->
            if (routeId == "1_100" && directionId == 1) "Downtown Seattle" else null
        }
        assertEquals("Downtown Seattle", result)
    }

    @Test
    fun nullWhenNothingResolves() {
        assertNull(subordinateRouteHeadsign(selection(originHeadsign = null)) { _, _ -> null })
    }

    @Test
    fun nullWhenNoSelection() {
        assertNull(subordinateRouteHeadsign(null) { _, _ -> "Downtown" })
    }
}
