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

import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.ShapeEntry
import org.onebusaway.android.api.contract.StopReference

/** The route the demo timetable and the tour both key on — King County Metro's 49. */
const val DEMO_TEST_ROUTE_ID = "1_100447"

/** The stop the tour focuses, and the one these tests measure arrivals at. */
const val DEMO_TEST_ANCHOR_STOP_ID = "1_11140"

/** A stop the fixture contains but the route does not call at — see [demoTestFixture]. */
const val DEMO_TEST_UNSERVED_STOP_ID = "stop_3"

/**
 * A synthetic demo deployment for the JVM tests: a straight 5 km line of six evenly spaced stops.
 *
 * Real geometry isn't needed to exercise the simulator — only monotonic distances along a shape — and a
 * synthetic one keeps these tests independent of whether the bundled fixture is re-cut. The ids *are*
 * the real King County Metro ones the bundled fixture carries, because [DemoScenario] keys its service
 * table on them; a fixture with invented ids would simply run no buses.
 *
 * Shared rather than copied per test class: it is the input to every assertion about the demo system,
 * so two divergent copies would let a test pass against geometry no other test sees. The anchor sits
 * fourth along the line, far enough in that a bus can be short of it.
 *
 * Note the stop set is deliberately one larger than the route: the anchor takes the fourth position, so
 * [DEMO_TEST_UNSERVED_STOP_ID] exists as a stop but is not on the route. That is a case worth having —
 * "a stop this deployment knows, that this route does not call at" is different from an id nobody has
 * heard of, and both are asserted.
 */
fun demoTestFixture(): DemoTransitFixture = DemoTransitFixture(
    agency = AgencyReference(id = "1", name = "Demo Transit", timezone = "America/Los_Angeles"),
    anchorStopId = DEMO_TEST_ANCHOR_STOP_ID,
    routes = listOf(RouteReference(id = DEMO_TEST_ROUTE_ID, shortName = "49", agencyId = "1")),
    stops = (0..5).map {
        StopReference(id = "stop_$it", name = "Stop $it", lat = 47.6 + it * 0.001, lon = -122.32)
    } +
        StopReference(id = DEMO_TEST_ANCHOR_STOP_ID, name = "Anchor", lat = 47.615, lon = -122.3246),
    routeStops = mapOf(
        DEMO_TEST_ROUTE_ID to DemoRouteStops(
            directionId = "0",
            name = "U-District Station",
            stopIds = listOf("stop_0", "stop_1", "stop_2", DEMO_TEST_ANCHOR_STOP_ID, "stop_4", "stop_5"),
            stopDistances = listOf(0.0, 1000.0, 2000.0, 3000.0, 4000.0, 5000.0),
            // A two-point encoded polyline; only its decoded length matters to these assertions.
            polyline = ShapeEntry(points = "_p~iF~ps|U_ulLnnqC", length = 2),
            totalDistance = 5000.0
        )
    )
)
