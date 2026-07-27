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
package org.onebusaway.android.ui.tripresults

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.util.GeoPoint

/**
 * Verifies that a ride the vehicle changes route during (#2000) is badged as what it is in both places a
 * ride is drawn — the itinerary option card at the top and the ride's own badge in the trip log: one
 * roundel naming every route ridden, "5 > 12" (#2049). The badge used to name only the route boarded,
 * leaving the picker promising a 5 all the way while the log's own "stay on board" row said otherwise.
 *
 * The instruction it must *not* pick up is the interchangeable one: a chevron badge is one vehicle, so
 * "whichever comes first" (see [DirectionRowAlternativeRoutesTest]) would tell the rider to board a 12
 * that is the same bus they are already on.
 */
class DirectionRowInterlineBadgeTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val boardPoint = GeoPoint(47.6062, -122.3321)
    private val seamPoint = GeoPoint(47.5764, -122.2977)
    private val alightPoint = GeoPoint(47.5301, -122.2688)

    /** The 5 becoming the 12 at the seam stop, with the rider aboard for both. */
    private val interlinedBadge = LegBadge(
        listOf(
            RouteBadge("5", 0xFF1B6EF3.toInt()),
            RouteBadge("12", 0xFFD62828.toInt())
        ),
        TransitMode.BUS,
        RouteBadgeJoin.THEN
    )

    private val transition = InterlineTransition(
        routeLabel = "12",
        headsign = "Interlaken Park",
        stop = RouteStopRef("1_550", "550", "Mount Baker Transit Center", seamPoint)
    )

    private val interlinedLegRef = RouteLegRef(
        routeId = "1_100",
        headsign = "Rainier Beach",
        board = RouteStopRef("1_500", "500", "3rd Ave & Pine St", boardPoint),
        alight = RouteStopRef("1_600", "600", "Rainier Ave S & S Alaska St", alightPoint),
        interlineTransitions = mapOf(1 to transition),
        badge = interlinedBadge
    )

    private val ride = TripLogEntry.Transit(
        routeShortName = "5",
        routeDisplayName = "5",
        mode = TransitMode.BUS,
        routeColorHex = "1B6EF3",
        headsign = "Rainier Beach",
        boardTime = ServerTime(2 * 60_000L),
        exitTime = ServerTime(32 * 60_000L),
        durationMinutes = 30,
        realtime = RealtimeState.Unknown,
        rideEvents = listOf(RideEvent.Transition(transition)),
        routeLeg = interlinedLegRef,
        legPoints = listOf(boardPoint, seamPoint, alightPoint)
    )

    private val state = TripResultsUiState.Success(
        options = listOf(
            ItineraryOption(
                symbols = listOf(ModeSymbol.Transit(interlinedBadge)),
                durationMinutes = 32L,
                startTime = ServerTime(0L),
                endTime = ServerTime(32 * 60_000L)
            )
        ),
        selectedIndex = 0,
        directions = listOf(ride)
    )

    /** Both roundels — the option card's and the ride's — name the route boarded and the one it becomes. */
    @Test
    fun anInterlinedRideBadgesEveryRouteItRunsAsInThePickerAndTheLog() {
        composeRule.setContent { TripResultsList(state = state) }

        // One node per route in each of the two badges: the option card's and the ride's.
        composeRule.onAllNodesWithText("5").assertCountEquals(2)
        composeRule.onAllNodesWithText("12").assertCountEquals(2)
    }

    /** The badge and the narrative tell one story: the 12 is where the ride goes, not a bus to board. */
    @Test
    fun theRideStillSaysToStayOnBoardAndNeverToTakeWhicheverComesFirst() {
        composeRule.setContent { TripResultsList(state = state) }

        composeRule
            .onNodeWithText(
                context.getString(
                    R.string.step_by_step_transit_interline,
                    "12 ${context.getString(R.string.step_by_step_transit_connector_headsign)} Interlaken Park"
                )
            )
            .assertExists()
        composeRule
            .onAllNodesWithText(context.getString(R.string.directions_whichever_comes_first))
            .assertCountEquals(0)
    }
}
