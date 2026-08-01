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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.neutralBadgeChipColor
import org.onebusaway.android.util.neutralBadgeChipTextColor
import org.onebusaway.android.util.routeBadgeChipColor
import org.onebusaway.android.util.routeBadgeChipTextColor

/**
 * Pure-logic tests for what a stop marker's route label reads, how it is laid out and how it is coloured
 * (#2107) — the zoom band that shows one at all, the balanced columns a stop with more routes than fit
 * takes, and that the cells are the arrivals drawer's badge rather than the basemap's line colour. The
 * bitmap build and the marker reconcile are exercised on device.
 */
class StopRouteLabelTest {

    private val vivid = 0xFF1E88E5.toInt()

    private fun route(name: String, color: Int? = vivid) = StopRoute(name, color)

    private fun routes(count: Int) = (1..count).map { route("$it") }

    /** The laid-out grid read as names, `null` for the blank cells padding the last column. */
    private fun names(routes: List<StopRoute>) = stopRouteLabelColumns(routes).map { column -> column.map { it?.shortName } }

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
    fun `the band is read as an ordering, so a band added above ROUTES still labels`() {
        // StopBand widens — a band above ROUTES shows everything it does and more — so every band from
        // ROUTES up labels. An equality test would switch the labels off at the zoom that wants them most.
        val stop = marker(listOf(route("8")))
        val labelling = StopBand.entries.filter { stopRouteLabel(stop, it).isNotEmpty() }
        assertEquals(StopBand.entries.filter { it >= StopBand.ROUTES }, labelling)
    }

    @Test
    fun `a stop with no routes resolved yet draws no label even in the routes band`() {
        assertEquals(emptyList<StopRoute>(), stopRouteLabel(marker(emptyList()), StopBand.ROUTES))
        assertEquals(emptyList<List<StopRoute?>>(), stopRouteLabelColumns(emptyList()))
    }

    // --- layout: columns, balanced, every route named ---

    @Test
    fun `routes that fit stay one column, in the order given`() {
        assertEquals(listOf(listOf("8", "40", "550")), names(listOf(route("8"), route("40"), route("550"))))
        assertEquals(1, stopRouteLabelColumns(routes(STOP_ROUTE_LABEL_MAX_ROWS)).size)
    }

    @Test
    fun `one route over the cap opens a second column rather than lengthening`() {
        // 6 routes, 5 rows allowed: two columns of 3 — read down, then across.
        assertEquals(listOf(listOf("1", "2", "3"), listOf("4", "5", "6")), names(routes(6)))
    }

    @Test
    fun `columns are balanced, not filled and spilled`() {
        // 7 in two columns reads 4 + 3, not 5 + 2: both are as wide, so the taller one is only taller.
        assertEquals(listOf(listOf("1", "2", "3", "4"), listOf("5", "6", "7", null)), names(routes(7)))
    }

    @Test
    fun `a third column opens only when two full ones would overflow`() {
        assertEquals(2, stopRouteLabelColumns(routes(10)).size)
        assertEquals(3, stopRouteLabelColumns(routes(11)).size)
        // 11 over three columns is 4 + 4 + 3, so the grid is 4 rows with one blank.
        assertEquals(listOf(4, 4, 4), stopRouteLabelColumns(routes(11)).map { it.size })
    }

    @Test
    fun `every route is named however many there are — no label ever says 'and N more'`() {
        for (count in 1..40) {
            val named = stopRouteLabelColumns(routes(count)).flatten().filterNotNull().map(StopRoute::shortName)
            assertEquals("$count routes", routes(count).map(StopRoute::shortName), named)
        }
    }

    @Test
    fun `the grid is rectangular, so the pill it fills has no hole`() {
        for (count in 1..40) {
            val columns = stopRouteLabelColumns(routes(count))
            assertEquals("$count routes", 1, columns.map { it.size }.distinct().size)
            // Only the trailing cells are blank — a hole in the middle would break the reading order.
            val cells = columns.flatten()
            assertTrue("$count routes", cells.dropWhile { it != null }.all { it == null })
        }
    }

    @Test
    fun `no column is ever taller than the cap`() {
        for (count in 1..40) {
            assertTrue("$count routes", stopRouteLabelColumns(routes(count)).all { it.size <= STOP_ROUTE_LABEL_MAX_ROWS })
        }
    }

    @Test
    fun `every column names at least one route`() {
        // A column is drawn as wide as its own widest name, so an all-blank one would be a bare sliver of
        // pill — and balancing is what rules it out, since it never opens a column it can't fill.
        for (count in 1..40) {
            assertTrue(
                "$count routes",
                stopRouteLabelColumns(routes(count)).all { column -> column.any { it != null } }
            )
        }
    }

    @Test
    fun `the default cap is five rows`() {
        // A literal anchor, so retuning the constant is a deliberate change that has to update this test.
        assertEquals(5, STOP_ROUTE_LABEL_MAX_ROWS)
        assertEquals(listOf(5), stopRouteLabelColumns(routes(5)).map { it.size })
        assertEquals(listOf(3, 3), stopRouteLabelColumns(routes(6)).map { it.size })
    }

    // --- colour: the arrivals drawer's badge, not the basemap's line ---

    @Test
    fun `a cell is the drawer's badge chip — its faded fill and the ink paired with it`() {
        val cell = stopRouteLabelGrid(listOf(route("8")), dark = false).single().single()
        assertEquals("8", cell.routeShortName)
        assertEquals(routeBadgeChipColor(vivid, dark = false), cell.color)
        assertEquals(routeBadgeChipTextColor(vivid, dark = false), cell.textColor)
    }

    @Test
    fun `a route with no usable colour takes the whole neutral chip, fill and ink together`() {
        for (source in listOf(null, 0xFF808080.toInt())) {
            val cell = stopRouteLabelGrid(listOf(route("8", source)), dark = false).single().single()
            assertEquals("neutral fill for $source", neutralBadgeChipColor(dark = false), cell.color)
            assertEquals("neutral ink for $source", neutralBadgeChipTextColor(dark = false), cell.textColor)
        }
    }

    @Test
    fun `a blank cell names nothing and takes the neutral chip, so the pill stays whole`() {
        val blank = stopRouteLabelGrid(routes(7), dark = false).last().last()
        assertEquals("", blank.routeShortName)
        assertEquals(neutralBadgeChipColor(dark = false), blank.color)
    }

    @Test
    fun `the cells flip with the theme, which is why the source is carried this far`() {
        val light = stopRouteLabelGrid(listOf(route("8")), dark = false).single().single()
        val night = stopRouteLabelGrid(listOf(route("8")), dark = true).single().single()
        assertNotEquals(light.color, night.color)
        assertNotEquals(light.textColor, night.textColor)
    }
}
