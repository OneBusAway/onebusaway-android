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
package org.onebusaway.android.ui.routeinfo

import org.onebusaway.android.api.adapters.ObaStopElement

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.models.RouteStopGroup

/**
 * Pure-logic coverage for the presentation mapping [RouteStopGroup] -> [RouteDirection]: per-stop
 * field copy and null/blank direction + null group-name normalization.
 */
class RouteDirectionsMapperTest {

    @Test
    fun mapsGroupsToDirectionsPreservingOrderAndFields() {
        val groups = listOf(
            RouteStopGroup(
                name = "Mount Baker Transit Center",
                stops = listOf(
                    ObaStopElement("1_a", 47.6, -122.33, "Spring St & 3rd Ave", "101", "NE"),
                    // Blank direction normalizes to empty string.
                    ObaStopElement("1_b", 47.61, -122.34, "Pine St", "102", ""),
                )
            )
        )

        val directions = groups.toRouteDirections()

        assertEquals(1, directions.size)
        val dir = directions[0]
        assertEquals("Mount Baker Transit Center", dir.name)
        assertEquals(listOf("1_a", "1_b"), dir.stops.map { it.id })
        assertEquals("Spring St & 3rd Ave", dir.stops[0].name)
        assertEquals("NE", dir.stops[0].direction)
        assertEquals(47.6, dir.stops[0].latitude, 0.0)
        assertEquals("", dir.stops[1].direction)
    }

    @Test
    fun nullGroupNameNormalizesToEmpty() {
        val directions = listOf(RouteStopGroup(name = null, stops = emptyList())).toRouteDirections()
        assertEquals("", directions[0].name)
    }
}
