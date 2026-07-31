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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.GeoPoint

/**
 * Unit tests for [StopFocusController]'s focus handoff — the part of the focused-stop layer that has
 * real concurrent behaviour: a *first* focus publishes each half the moment it resolves, a *replacing*
 * focus stages both halves and swaps once, a re-sent identical focus refreshes only its route metadata,
 * and a [StopFocusController.clear] mid-load must not let the abandoned load publish afterwards.
 *
 * The repository's two halves are gated on deferreds so each ordering is exercised deterministically:
 * on an unconfined dispatcher, completing one half runs the controller's reaction to it before the test
 * body resumes, so every assertion sees a settled state without advancing a clock. The nearby-stops
 * loader enters as the two lambdas the controller actually calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopFocusControllerTest {

    /** A repository whose two halves complete only when the test says so, counting each call. */
    private class GatedRepository : FocusedTripRepository {
        var geometry = CompletableDeferred<FocusedTripGeometry>()
        var stops = CompletableDeferred<FocusedTripStops>()
        var geometryCalls = 0
        var stopCalls = 0

        override suspend fun getGeometry(trips: Set<FocusedTrip>): FocusedTripGeometry {
            geometryCalls++
            return geometry.await()
        }

        override suspend fun getStops(trips: Set<FocusedTrip>): FocusedTripStops {
            stopCalls++
            return stops.await()
        }

        /** Re-arm both halves for the next focus, leaving the call counts intact. */
        fun rearm() {
            geometry = CompletableDeferred()
            stops = CompletableDeferred()
        }
    }

    private val repository = GatedRepository()
    private var routeActive = true
    private var nearbyStarts = 0
    private var nearbyStops = 0
    private var publishes = 0

    private fun TestScope.controller() = StopFocusController(
        focusedTripRepository = repository,
        startNearbyStops = { nearbyStarts++ },
        stopNearbyStops = { nearbyStops++ },
        // A child of backgroundScope (so an unfinished load is torn down with the test) that dispatches
        // eagerly, mirroring the Main.immediate scope the controller runs on in production.
        scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        isRouteActive = { routeActive },
        onPresentationChanged = { publishes++ }
    )

    @Test
    fun `a first focus shows the stop immediately and each half as it lands`() = runTest {
        val controller = controller()

        controller.focus("stop-1", setOf(trip("trip-1", "route-1")), listOf(route("route-1")))

        // The session itself is applied synchronously — the layer exists before either half resolves.
        assertEquals("stop-1", controller.focusedStopId)
        assertEquals(setOf(trip("trip-1", "route-1")), controller.presentation.trips)
        assertEquals(FocusedTripGeometry.EMPTY, controller.presentation.geometry)
        assertEquals(1, nearbyStarts)

        repository.geometry.complete(geometryOf("shape-1", "route-1"))

        // The geometry appears on its own rather than waiting for the schedules alongside it.
        assertEquals(listOf("shape-1"), controller.presentation.geometry.shapes.map { it.shapeId })
        assertEquals(FocusedTripStops.EMPTY, controller.presentation.stops)

        repository.stops.complete(stopsOf("trip-1", "stop-a"))

        assertEquals(listOf("stop-a"), controller.presentation.stops.stopIdsByTripId["trip-1"])
    }

    @Test
    fun `replacing a focus keeps the old presentation whole until both halves are ready`() = runTest {
        val controller = controller()
        controller.focus("stop-1", setOf(trip("trip-1", "route-1")), listOf(route("route-1")))
        repository.geometry.complete(geometryOf("shape-1", "route-1"))
        repository.stops.complete(stopsOf("trip-1", "stop-a"))
        repository.rearm()

        controller.focus("stop-2", setOf(trip("trip-2", "route-2")), listOf(route("route-2")))

        // Still the complete first focus — a replacement must not blink through a half-loaded state.
        assertEquals("stop-1", controller.focusedStopId)
        assertEquals(listOf("shape-1"), controller.presentation.geometry.shapes.map { it.shapeId })

        // Nor may one resolved half of the replacement leak in ahead of the other.
        repository.geometry.complete(geometryOf("shape-2", "route-2"))
        assertEquals("stop-1", controller.focusedStopId)
        assertEquals(listOf("shape-1"), controller.presentation.geometry.shapes.map { it.shapeId })

        repository.stops.complete(stopsOf("trip-2", "stop-b"))

        assertEquals("stop-2", controller.focusedStopId)
        assertEquals(listOf("shape-2"), controller.presentation.geometry.shapes.map { it.shapeId })
        assertEquals(listOf("stop-b"), controller.presentation.stops.stopIdsByTripId["trip-2"])
    }

    @Test
    fun `re-sending the same focus refreshes the routes without reloading`() = runTest {
        val controller = controller()
        val trips = setOf(trip("trip-1", "route-1"))
        controller.focus("stop-1", trips, listOf(route("route-1", shortName = "10")))
        repository.geometry.complete(geometryOf("shape-1", "route-1"))
        repository.stops.complete(stopsOf("trip-1", "stop-a"))
        val colors = controller.routeColors.value

        // The arrivals poll hands back equal-but-recreated route wrappers every few seconds.
        controller.focus("stop-1", trips, listOf(route("route-1", shortName = "10 Express")))

        assertEquals(1, repository.geometryCalls)
        assertEquals(1, repository.stopCalls)
        assertEquals(listOf("10 Express"), controller.presentation.routes.map { it.shortName })
        // The resolved halves survive the metadata refresh, and the colour map keeps its identity so
        // RouteMapController's per-frame colour memo isn't invalidated by an arrivals poll.
        assertEquals(listOf("shape-1"), controller.presentation.geometry.shapes.map { it.shapeId })
        assertSame(colors, controller.routeColors.value)
    }

    @Test
    fun `clearing mid-load drops the focus and the abandoned load publishes nothing`() = runTest {
        val controller = controller()
        controller.focus("stop-1", setOf(trip("trip-1", "route-1")), listOf(route("route-1")))
        repository.geometry.complete(geometryOf("shape-1", "route-1"))

        controller.clear()
        val publishesAtClear = publishes

        assertNull(controller.focusedStopId)
        assertNull(controller.presentation.trips)
        assertTrue(controller.routeColors.value.isEmpty())
        // A route owns the stop layer, so the nearby-stops loader stays off.
        assertEquals(1, nearbyStops)

        repository.stops.complete(stopsOf("trip-1", "stop-a"))

        assertNull(controller.focusedStopId)
        assertEquals(FocusedTripStops.EMPTY, controller.presentation.stops)
        assertEquals(publishesAtClear, publishes)
    }

    @Test
    fun `clearing with no route showing hands the stop layer back to the nearby loader`() = runTest {
        routeActive = false
        val controller = controller()
        controller.focus("stop-1", setOf(trip("trip-1", "route-1")), listOf(route("route-1")))
        val startsWhileFocused = nearbyStarts

        controller.clear()

        assertEquals(0, nearbyStops)
        assertEquals(startsWhileFocused + 1, nearbyStarts)
    }

    @Test
    fun `clearing when nothing is focused is a no-op`() = runTest {
        val controller = controller()

        controller.clear()

        assertEquals(0, publishes)
        assertEquals(0, nearbyStops)
        assertFalse(repository.geometryCalls > 0)
    }

    private fun trip(tripId: String, routeId: String) = FocusedTrip(tripId, routeId, "shape-$tripId", null)

    private fun geometryOf(shapeId: String, routeId: String) = FocusedTripGeometry(
        listOf(FocusedTripShape(shapeId, routeId, null, listOf(GeoPoint(47.6, -122.3))))
    )

    private fun stopsOf(tripId: String, stopId: String) = FocusedTripStops(
        stopIdsByTripId = mapOf(tripId to listOf(stopId)),
        stopsById = mapOf(stopId to ObaStopElement(id = stopId, lat = 47.6, lon = -122.3))
    )

    private fun route(id: String, shortName: String = id) = object : ObaRoute {
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
