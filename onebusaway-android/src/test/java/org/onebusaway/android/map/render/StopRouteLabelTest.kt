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
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.neutralBadgeChipColor
import org.onebusaway.android.util.neutralBadgeChipTextColor
import org.onebusaway.android.util.routeBadgeChipColor
import org.onebusaway.android.util.routeBadgeChipTextColor

/**
 * Pure-logic tests for what a stop marker's route label reads and how it is coloured (#2107) — the zoom
 * band that shows one at all, the overflow rule that keeps a downtown bay's label from burying its
 * neighbours, and that the rows take the arrivals drawer's badge rather than the basemap's line colour.
 * The bitmap build and the marker reconcile are exercised on device.
 */
class StopRouteLabelTest {

    private val vivid = 0xFF1E88E5.toInt()

    private fun route(name: String, color: Int? = vivid) = StopRoute(name, color)

    private fun marker(routes: List<StopRoute>) = StopMarker(
        "stop",
        GeoPoint(0.0, 0.0),
        "null",
        ObaRoute.TYPE_BUS,
        ObaStopElement("stop", 0.0, 0.0, "stop", "stop"),
        routes = routes
    )

    @Test
    fun `only the routes band names a stop's routes`() {
        val stop = marker(listOf(route("8"), route("40")))
        assertEquals(emptyList<StopRoute>(), stopRouteLabel(stop, StopBand.DOT))
        assertEquals(emptyList<StopRoute>(), stopRouteLabel(stop, StopBand.FULL))
        assertEquals(stop.routes, stopRouteLabel(stop, StopBand.ROUTES))
    }

    @Test
    fun `a stop with no routes resolved yet draws no label even in the routes band`() {
        assertEquals(emptyList<StopRoute>(), stopRouteLabel(marker(emptyList()), StopBand.ROUTES))
    }

    @Test
    fun `routes that fit are all named, in the order given`() {
        val routes = listOf(route("8"), route("40"), route("550"))
        assertEquals(routes, stopRouteLabel(routes, maxRows = 3))
    }

    @Test
    fun `overflow keeps one row back to count what it dropped`() {
        val routes = (1..10).map { route("route$it") }
        val rows = stopRouteLabel(routes, maxRows = 4)
        assertEquals(4, rows.size)
        assertEquals(routes.take(3), rows.take(3))
        // 10 routes, 3 named: the last row accounts for the other 7 rather than leaving them unsaid.
        assertEquals("+7", rows.last().shortName)
        // Colourless, so it draws as the neutral chip — it is a count, not one more route.
        assertEquals(null, rows.last().routeColor)
    }

    @Test
    fun `exactly one row over the cap still overflows, counting the two it displaced`() {
        val routes = listOf(route("a"), route("b"), route("c"))
        assertEquals(listOf("a", "+2"), stopRouteLabel(routes, maxRows = 2).map(StopRoute::shortName))
    }

    @Test
    fun `a label with no room to both name and count is a producer bug, not a silent empty label`() {
        assertThrows(IllegalArgumentException::class.java) {
            stopRouteLabel(listOf(route("8"), route("40")), maxRows = 1)
        }
    }

    @Test
    fun `the default cap is five rows`() {
        // A literal anchor, so retuning the constant is a deliberate change that has to update this test.
        assertEquals(5, STOP_ROUTE_LABEL_MAX_ROWS)
        assertEquals(5, stopRouteLabel((1..9).map { route("$it") }).size)
    }

    // --- colour: the arrivals drawer's badge, not the basemap's line ---

    @Test
    fun `a row is the drawer's badge chip — its faded fill and the ink paired with it`() {
        val row = stopRouteLabelRows(listOf(route("8")), dark = false).single()
        assertEquals("8", row.routeShortName)
        assertEquals(routeBadgeChipColor(vivid, dark = false), row.color)
        assertEquals(routeBadgeChipTextColor(vivid, dark = false), row.textColor)
    }

    @Test
    fun `a route with no usable colour takes the whole neutral chip, fill and ink together`() {
        for (source in listOf(null, 0xFF808080.toInt())) {
            val row = stopRouteLabelRows(listOf(route("8", source)), dark = false).single()
            assertEquals("neutral fill for $source", neutralBadgeChipColor(dark = false), row.color)
            assertEquals("neutral ink for $source", neutralBadgeChipTextColor(dark = false), row.textColor)
        }
    }

    @Test
    fun `the rows flip with the theme, which is why the source is carried this far`() {
        val light = stopRouteLabelRows(listOf(route("8")), dark = false).single()
        val night = stopRouteLabelRows(listOf(route("8")), dark = true).single()
        assertNotEquals(light.color, night.color)
        assertNotEquals(light.textColor, night.textColor)
    }
}
