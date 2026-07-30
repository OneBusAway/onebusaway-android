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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.util.GeoPoint

/**
 * Verifies that a ride the vehicle changes route during (#2000) names every route it runs as, in the two
 * places a ride is drawn — and names them in the form each place can carry:
 *  - the itinerary option card has one line to stand for the whole ride, so it joins them into one
 *    roundel, "5 > 12" (#2049). The card used to name only the route boarded, leaving the picker
 *    promising a 5 all the way while the drawer's own "stay on board" row said otherwise;
 *  - the trip log has a row per segment, so it splits them (#2071): the header badges the 5 the rider
 *    boards, and the 12 appears at the seam row, where the rider is told to stay aboard for it. The
 *    header used to carry the joined roundel too, promising a 12 several stops before it existed.
 *
 * The instruction the seam must *not* pick up is the interchangeable one: an interlined ride is one
 * vehicle, so "whichever comes first" (see [DirectionRowAlternativeRoutesTest]) would tell the rider to
 * board a 12 that is the same bus they are already on.
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
        badge = RouteBadge("12", 0xFFD62828.toInt()),
        routeDisplayName = "12",
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

    /** Every route the ride runs as is named twice over: once on the option card, once in the log. */
    @Test
    fun anInterlinedRideNamesEveryRouteItRunsAsInThePickerAndTheLog() {
        composeRule.setContent { TripResultsList(state = state) }

        composeRule.onAllNodesWithText("5").assertCountEquals(2)
        composeRule.onAllNodesWithText("12").assertCountEquals(2)
    }

    /**
     * …but the log names them where the rider reaches them (#2071): the 12 is drawn *below* the board
     * stop, at its seam, and never beside the route the rider is boarding. Anchored on the board stop's
     * own row rather than on node order, so it fails for the reason it is named — the header carrying a
     * "5 > 12" roundel would put a 12 above that line, and no 5 may appear below it.
     */
    @Test
    fun theLogBadgesTheRouteBoardedAtTheHeaderAndTheOneItBecomesAtTheSeam() {
        composeRule.setContent { TripResultsList(state = state) }

        val boardStopBottom = composeRule
            .onNodeWithText("3rd Ave & Pine St")
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        fun topsOf(name: String) = composeRule.onAllNodesWithText(name)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.top }

        assertEquals("one 12 below the board stop — the seam's", 1, topsOf("12").count { it > boardStopBottom })
        assertEquals("no 5 below the board stop", 0, topsOf("5").count { it > boardStopBottom })
    }

    /** The badge and the narrative tell one story: the 12 is where the ride goes, not a bus to board. */
    @Test
    fun theRideStillSaysToStayOnBoardAndNeverToTakeWhicheverComesFirst() {
        composeRule.setContent { TripResultsList(state = state) }

        composeRule
            .onNodeWithText(context.getString(R.string.step_by_step_transit_stay_on_board))
            .assertExists()
        composeRule.onNodeWithText("Interlaken Park").assertExists()
        composeRule
            .onAllNodesWithText(context.getString(R.string.directions_whichever_comes_first))
            .assertCountEquals(0)
    }
}
