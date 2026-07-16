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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey

/**
 * Unit tests for the pure route-map presentation assembler — the three-mode precedence and the
 * adjacency-colour/underlay interaction that produced the #1899 regression. No controller, no IO, no
 * Android — the Location-dependent focused-stop projection enters as a thunk (its geometry is covered by
 * the instrumented [RouteStopProjectionTest]).
 */
class RouteMapPresentationPlanTest {

    private val basePolylines = listOf(line(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)))
    private val baseStops = RouteStopPresentation(emptyList(), emptyList(), emptyMap(), emptyMap())

    // --- Mode 2: no stop-focus session ---

    @Test
    fun `no focus and no selection draws the plain base route`() {
        val plan = assemble(focusTrips = null, selected = null)

        assertSame(basePolylines, plan.polylines)
        assertSame(basePolylines, plan.framingPolylines)
        assertSame(baseStops, plan.stopPresentation)
        assertEquals(emptyList<RouteBadge>(), plan.badges)
        assertTrue(plan.routeModeScalesStopsWithZoom)
    }

    @Test
    fun `outside route mode the base branch does not scale stops with zoom`() {
        val plan = assemble(isActive = false, emphasizedRoute = null, focusTrips = null, selected = null)

        assertFalse(plan.routeModeScalesStopsWithZoom)
    }

    // --- Mode 3: stop-focus session ---

    @Test
    fun `emphasized route not among focused trips keeps the base route and its stops underneath`() {
        val emphasized = RouteDirectionKey("45", 0)
        val plan = assemble(
            emphasizedRoute = emphasized,
            focusTrips = setOf(trip("t-79", "79", directionId = 1)),
            focusedGeometry = geometryFor("79", 1),
            selected = null,
            projectedFocusStops = { fail("projection is only computed when focused-stop siblings draw") },
        )

        // Adjacency line for route 79 + the base route drawn underneath it.
        assertEquals(2, plan.polylines.size)
        assertSame(baseStops, plan.stopPresentation)
        assertSame(basePolylines, plan.framingPolylines)
    }

    @Test
    fun `emphasized route among focused trips drops the base route and builds focused-stop presentation`() {
        val emphasized = RouteDirectionKey("45", 0)
        val projected = mapOf("stop-a" to GeoPoint(2.0, 2.0))
        var projections = 0
        val plan = assemble(
            emphasizedRoute = emphasized,
            focusTrips = setOf(trip("t-45", "45", directionId = 0)),
            focusedGeometry = geometryFor("45", 0),
            focusedStops = FocusedTripStops(
                stopIdsByTripId = mapOf("t-45" to listOf("stop-a")),
                stopsById = mapOf("stop-a" to stop("stop-a")),
            ),
            focusedRoutes = listOf(route("45", "45")),
            selected = null,
            projectedFocusStops = { projections++; projected },
        )

        // Only the adjacency line — the base route is not drawn underneath its own focus.
        assertEquals(1, plan.polylines.size)
        val stops = plan.stopPresentation!!
        assertEquals(listOf("stop-a"), stops.stops.map { it.id })
        assertEquals(projected, stops.projectedPoints)
        assertEquals(1, projections)
    }

    @Test
    fun `stop focus without route mode emits badges and no base underlay`() {
        val plan = assemble(
            isActive = false,
            emphasizedRoute = null,
            focusTrips = setOf(trip("t-79", "79", directionId = 1)),
            focusedGeometry = geometryFor("79", 1),
            focusedStops = FocusedTripStops(
                stopIdsByTripId = mapOf("t-79" to listOf("stop-b")),
                stopsById = mapOf("stop-b" to stop("stop-b")),
            ),
            focusedRoutes = listOf(route("79", "79")),
            selected = null,
            projectedFocusStops = { emptyMap() },
        )

        // No emphasized route -> route badges are emitted, and there's no active base route to frame to.
        assertEquals(listOf("79"), plan.badges.map { it.routeId })
        assertEquals(emptyList<RoutePolyline>(), plan.framingPolylines)
        assertEquals(1, plan.polylines.size)
    }

    // --- Mode 1: selected vehicle, and the #1899 adjacency-colour/underlay interaction ---

    @Test
    fun `whole-route selection sits on the direction underlay with the GTFS fallback colour`() {
        val underlay = listOf(line(GeoPoint(9.0, 9.0), GeoPoint(9.0, 10.0)))
        val selectedStops = RouteStopPresentation(emptyList(), emptyList(), emptyMap(), emptyMap())
        val plan = assemble(
            emphasizedRoute = RouteDirectionKey("45", 0),
            focusTrips = null,
            focusedGeometry = FocusedTripGeometry(emptyList()),
            routeColors = emptyMap(), // no adjacency entry -> GTFS fallback + underlay kept
            selected = SelectedTripRenderInput(
                presentation = selectedTrip("45", 0),
                routeColorFallback = 0xFF00FF00.toInt(),
                directionUnderlay = { underlay },
                stopPresentation = { selectedStops },
            ),
        )

        // Underlay + exact trip, in that order; the trip carries the GTFS fallback colour.
        assertEquals(2, plan.polylines.size)
        assertSame(underlay.single(), plan.polylines.first())
        val trip = plan.polylines.last()
        assertEquals(0xFF00FF00.toInt(), trip.color)
        assertEquals(listOf(trip), plan.framingPolylines)
        assertSame(selectedStops, plan.stopPresentation)
    }

    @Test
    fun `selection inside stop focus keeps the adjacency colour and drops the generic underlay`() {
        val underlay = listOf(line(GeoPoint(9.0, 9.0), GeoPoint(9.0, 10.0)))
        val adjacencyColor = 0xFF123456.toInt()
        val plan = assemble(
            emphasizedRoute = RouteDirectionKey("45", 0),
            focusTrips = setOf(trip("t-45", "45", directionId = 0)),
            focusedGeometry = FocusedTripGeometry(emptyList()),
            routeColors = mapOf(RouteDirectionKey("45", 0) to adjacencyColor),
            selected = SelectedTripRenderInput(
                presentation = selectedTrip("45", 0),
                routeColorFallback = 0xFF00FF00.toInt(),
                directionUnderlay = { underlay },
                stopPresentation = { RouteStopPresentation(emptyList(), emptyList(), emptyMap(), emptyMap()) },
            ),
        )

        // Only the exact trip — the generic direction underlay is dropped because the route already
        // reads via its adjacency colour (the #1899 regression case).
        assertEquals(1, plan.polylines.size)
        assertEquals(adjacencyColor, plan.polylines.single().color)
    }

    @Test
    fun `selection inside stop focus with no adjacency entry for its own direction still drops the underlay`() {
        val underlay = listOf(line(GeoPoint(9.0, 9.0), GeoPoint(9.0, 10.0)))
        val plan = assemble(
            emphasizedRoute = RouteDirectionKey("45", 0),
            // The focused stop's own trips don't include this exact direction (e.g. an
            // opposite-direction vehicle) -> routeColors carries no adjacency entry for it.
            focusTrips = setOf(trip("t-79", "79", directionId = 1)),
            focusedGeometry = FocusedTripGeometry(emptyList()),
            routeColors = emptyMap(),
            selected = SelectedTripRenderInput(
                presentation = selectedTrip("45", 0),
                routeColorFallback = 0xFF00FF00.toInt(),
                directionUnderlay = { underlay },
                stopPresentation = { RouteStopPresentation(emptyList(), emptyList(), emptyMap(), emptyMap()) },
            ),
        )

        // Stop focus being active alone gates the underlay -- not whether an adjacency colour was
        // found for this exact direction (the #1899 regression fixed by #1902). Only the exact trip
        // draws, in the GTFS fallback colour since there's no adjacency entry to carry instead.
        assertEquals(1, plan.polylines.size)
        assertEquals(0xFF00FF00.toInt(), plan.polylines.single().color)
    }

    @Test
    fun `a degenerate selected trip falls through to the base route`() {
        val plan = assemble(
            focusTrips = null,
            selected = SelectedTripRenderInput(
                presentation = SelectedTripPresentation(
                    points = listOf(GeoPoint(0.0, 0.0)), // < 2 points: not drawable
                    stopIds = emptyList(),
                    routeDirection = RouteDirectionKey("45", 0),
                ),
                routeColorFallback = 0xFF00FF00.toInt(),
                directionUnderlay = { fail("an undrawable trip must not build the underlay") },
                stopPresentation = { fail("an undrawable trip must not project its stops") },
            ),
        )

        assertSame(basePolylines, plan.polylines)
        assertSame(baseStops, plan.stopPresentation)
    }

    @Test
    fun `a drawable selection wins over a stop-focus session`() {
        val selectedStops = RouteStopPresentation(emptyList(), emptyList(), emptyMap(), emptyMap())
        val plan = assemble(
            emphasizedRoute = RouteDirectionKey("45", 0),
            focusTrips = setOf(trip("t-45", "45", directionId = 0)),
            focusedGeometry = geometryFor("45", 0),
            routeColors = emptyMap(),
            selected = SelectedTripRenderInput(
                presentation = selectedTrip("45", 0),
                routeColorFallback = 0xFF00FF00.toInt(),
                directionUnderlay = { emptyList() },
                stopPresentation = { selectedStops },
            ),
            projectedFocusStops = { fail("selection branch must not project focused stops") },
        )

        assertSame(selectedStops, plan.stopPresentation)
        assertEquals(1, plan.framingPolylines.size)
    }

    // --- helpers ---

    private fun assemble(
        isActive: Boolean = true,
        emphasizedRoute: RouteDirectionKey? = RouteDirectionKey("45", 0),
        focusTrips: Set<FocusedTrip>?,
        focusedGeometry: FocusedTripGeometry = FocusedTripGeometry(emptyList()),
        focusedStops: FocusedTripStops = FocusedTripStops(emptyMap(), emptyMap()),
        focusedRoutes: List<ObaRoute> = emptyList(),
        routeColors: Map<RouteDirectionKey, Int> = emptyMap(),
        selected: SelectedTripRenderInput?,
        projectedFocusStops: () -> Map<String, GeoPoint> = { emptyMap() },
    ) = assembleRouteMapPresentation(
        isActive = isActive,
        emphasizedRoute = emphasizedRoute,
        basePolylines = basePolylines,
        baseStopPresentation = baseStops,
        focusTrips = focusTrips,
        focusedGeometry = focusedGeometry,
        focusedStops = focusedStops,
        focusedRoutes = focusedRoutes,
        routeColors = routeColors,
        selected = selected,
        projectedFocusStops = projectedFocusStops,
    )

    private fun geometryFor(routeId: String, directionId: Int) = FocusedTripGeometry(
        listOf(
            FocusedTripShape(
                "$routeId-shape", routeId, 0xFF808080.toInt(),
                listOf(GeoPoint(1.0, 0.0), GeoPoint(1.0, 1.0)), directionId,
            )
        )
    )

    private fun selectedTrip(routeId: String, directionId: Int) = SelectedTripPresentation(
        points = listOf(GeoPoint(4.0, 0.0), GeoPoint(4.0, 1.0)),
        stopIds = emptyList(),
        routeDirection = RouteDirectionKey(routeId, directionId),
    )

    private fun line(vararg points: GeoPoint) =
        RoutePolyline(color = 1, points = points.toList(), widthProfile = ROUTE_LINE_WIDTH_PROFILE)

    private fun trip(tripId: String, routeId: String, directionId: Int) =
        FocusedTrip(tripId, routeId, "$routeId-shape", 0xFF808080.toInt(), directionId)

    private fun fail(message: String): Nothing = throw AssertionError(message)

    private fun stop(id: String): org.onebusaway.android.models.ObaStop =
        org.onebusaway.android.api.adapters.ObaStopElement(id = id, lat = 47.0, lon = -122.0)

    private fun route(id: String, shortName: String) = object : ObaRoute {
        override val id = id
        override val shortName = shortName
        override val longName: String? = null
        override val description: String? = null
        override val type = ObaRoute.TYPE_BUS
        override val url: String? = null
        override val color: Int? = null
        override val textColor: Int? = null
        override val agencyId = "agency"
    }
}
