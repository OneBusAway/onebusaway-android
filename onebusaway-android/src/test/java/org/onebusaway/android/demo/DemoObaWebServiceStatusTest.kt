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
package org.onebusaway.android.demo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.ShapeEntry
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.contract.TripStatus

/**
 * What a demo vehicle's real-time status claims about *when* (#2164).
 *
 * The app doesn't draw the marker at the position a status reports — it anchors at the status's
 * `lastUpdateTime` and extrapolates the reported distance forward from there (`TripState.withStatus` →
 * `extrapolate`). So a status whose position is current but whose timestamp is backdated does not read
 * as "fresh data": it reads as a bus that has already travelled the age of the report past where its own
 * ETA math puts it. The tour's whole "the pill and the marker are the same fact" claim rests on the two
 * agreeing, so it is pinned here rather than left to whoever next retunes the reported feed age.
 *
 * The clock isn't injectable (the demo deployment stands in for a server, and reads
 * [DemoClock] internally), so these assertions are deliberately *relative* — they re-derive what the
 * status claims from the status's own timestamp, and never from the wall clock.
 */
class DemoObaWebServiceStatusTest {

    private val routeId = "1_100447"
    private val anchorStopId = "1_11140"

    /** The same synthetic straight-line fixture `DemoScenarioTest` uses: six stops over 5 km. */
    private val fixture = DemoTransitFixture(
        agency = AgencyReference(id = "1", name = "Demo Transit", timezone = "America/Los_Angeles"),
        anchorStopId = anchorStopId,
        routes = listOf(RouteReference(id = routeId, shortName = "49", agencyId = "1")),
        stops = (0..5).map {
            StopReference(id = "stop_$it", name = "Stop $it", lat = 47.6 + it * 0.001, lon = -122.32)
        } +
            StopReference(id = anchorStopId, name = "Anchor", lat = 47.615, lon = -122.3246),
        routeStops = mapOf(
            routeId to DemoRouteStops(
                directionId = "0",
                name = "U-District Station",
                stopIds = listOf("stop_0", "stop_1", "stop_2", anchorStopId, "stop_4", "stop_5"),
                stopDistances = listOf(0.0, 1000.0, 2000.0, 3000.0, 4000.0, 5000.0),
                polyline = ShapeEntry(points = "_p~iF~ps|U_ulLnnqC", length = 2),
                totalDistance = 5000.0
            )
        )
    )

    private val service = DemoObaWebService(fixture)

    @Test
    fun `a status describes the bus at the instant it says it was reported`() = runTest {
        val statuses = onRoadStatuses()
        assertTrue("expected at least one bus on the road", statuses.isNotEmpty())

        statuses.forEach { status ->
            val run = DemoScenario.runById(fixture, status.activeTripId)
            assertNotNull("status names a trip the scenario doesn't know: ${status.activeTripId}", run)
            requireNotNull(run)

            // The distance reported is the distance the run had covered at lastUpdateTime — not at the
            // moment of the request. Extrapolation advances it from that anchor, so any gap between the
            // two is a permanent lead the marker carries over the timetable.
            assertEquals(
                "reported distance disagrees with the reported time for ${run.tripId}",
                run.progressAlongShapeAt(status.lastUpdateTime),
                status.distanceAlongTrip ?: -1.0,
                1.0
            )
            // Position and distance are the same claim, so they have to be made about the same instant.
            val expected = requireNotNull(run.positionAt(status.lastUpdateTime))
            val position = requireNotNull(status.position)
            assertEquals(expected.latitude, position.lat, 1e-6)
            assertEquals(expected.longitude, position.lon, 1e-6)
        }
    }

    @Test
    fun `the location timestamp is the same instant as the trip timestamp`() = runTest {
        onRoadStatuses().forEach {
            assertEquals(it.lastUpdateTime, it.lastLocationUpdateTime)
        }
    }

    /** Every plottable vehicle currently out on the demo route. */
    private suspend fun onRoadStatuses(): List<TripStatus> = requireNotNull(
        service.tripsForRoute(routeId, includeStatus = true, includeSchedule = false).data
    ).list
        .mapNotNull { it.status }
        .filter { it.position != null }
}
