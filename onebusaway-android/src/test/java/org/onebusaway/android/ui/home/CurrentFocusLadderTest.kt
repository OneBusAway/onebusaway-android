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
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.tripresults.FocusedLeg
import org.onebusaway.android.util.GeoPoint

/**
 * The focus ladder ([peeledOneLevel]) and the trip rung that sits on every rung of it that draws a route
 * ([selectedTripId] / [withSelectedTrip]).
 *
 * The rung used to exist only over a focused stop, so the same gesture unwound differently depending on
 * how the rider reached the route (#2224). These cases are written per focus kind on purpose: the point
 * of the change is that the three answers are the *same*, and a per-kind test is what catches a fourth
 * route-bearing focus being added without one.
 */
class CurrentFocusLadderTest {

    private val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
    private val selection = StopRouteSelection(
        originHeadsign = "Downtown",
        legs = listOf(RouteLeg("65", "65", 0))
    )
    private val stopRoute = CurrentFocus.Stop(stop, selection)
    private val standaloneRoute = CurrentFocus.Route(RouteTarget("65", directionId = 0))
    private val legRoute = CurrentFocus.Directions(
        DirectionsSubFocus.Route(ShowRouteRequest("65", "board"), boardStop = stop)
    )

    /** Every focus that draws a route, in the plain (nothing drilled into) state. */
    private val routeBearing = listOf(stopRoute, standaloneRoute, legRoute)

    @Test
    fun `every route-bearing focus can hold and report a trip`() {
        routeBearing.forEach { focus ->
            assertNull(focus.toString(), focus.selectedTripId)
            assertEquals(focus.toString(), "trip", focus.withSelectedTrip("trip")?.selectedTripId)
        }
    }

    @Test
    fun `a focus that draws no route has nothing to drill into`() {
        listOf(
            CurrentFocus.None,
            CurrentFocus.BikeStation("dock"),
            // A stop with no route selected draws no route either, so no vehicle can be tapped over it.
            CurrentFocus.Stop(stop),
            CurrentFocus.Directions(),
            CurrentFocus.Directions(DirectionsSubFocus.Leg(FocusedLeg(listOf(GeoPoint(47.6, -122.3)), setOf(1))))
        ).forEach { focus ->
            assertNull(focus.toString(), focus.selectedTripId)
            assertNull(focus.toString(), focus.withSelectedTrip("trip"))
        }
    }

    @Test
    fun `the trip is the innermost rung wherever a route is drawn`() {
        routeBearing.forEach { focus ->
            val drilledIn = focus.withSelectedTrip("trip")
            // One peel gives up the vehicle and leaves the route exactly as it was.
            assertEquals(focus.toString(), focus, drilledIn?.peeledOneLevel())
        }
    }

    @Test
    fun `a stop's ladder runs trip then route then stop then root`() {
        assertEquals(
            listOf(
                CurrentFocus.Stop(stop, selection),
                CurrentFocus.Stop(stop),
                CurrentFocus.None
            ),
            CurrentFocus.Stop(stop, selection.copy(selectedTripId = "trip")).rungsBelow()
        )
    }

    @Test
    fun `a standalone route's ladder runs trip then route then root`() {
        assertEquals(
            listOf(standaloneRoute, CurrentFocus.None),
            standaloneRoute.copy(selectedTripId = "trip").rungsBelow()
        )
    }

    @Test
    fun `a directions leg's ladder runs trip then leg then overview then root`() {
        val subFocus = DirectionsSubFocus.Route(ShowRouteRequest("65", "board"), boardStop = stop)
        assertEquals(
            listOf(
                legRoute,
                CurrentFocus.Directions(),
                CurrentFocus.None
            ),
            CurrentFocus.Directions(subFocus.copy(selectedTripId = "trip")).rungsBelow()
        )
    }

    /** Every rung under this focus, outermost last — the walk `HomeViewModel` rebuilds undo history with. */
    private fun CurrentFocus.rungsBelow(): List<CurrentFocus> = generateSequence(this) { it.peeledOneLevel() }.drop(1).toList()
}
