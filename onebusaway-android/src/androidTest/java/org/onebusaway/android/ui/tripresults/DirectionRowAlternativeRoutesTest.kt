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
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.util.GeoPoint

/**
 * Verifies that a ride's interchangeable routes (#2010) reach the rider in both places they're drawn:
 * the itinerary option card's joined roundel at the top, and the ride's own joined badge in the trip
 * log (with its "whichever comes first" caption). A ride with a single route badges only that route and
 * says nothing about alternatives.
 *
 * The on-device counterpart to [org.onebusaway.android.directions.model.InterchangeableRoutesTest]
 * (which decides *which* routes qualify) and `RouteBadgesTest` (which builds the badge).
 */
class DirectionRowAlternativeRoutesTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val boardPoint = GeoPoint(47.8158, -122.2942)
    private val alightPoint = GeoPoint(47.6114, -122.3376)

    /** The issue's own case: two lines sharing the track from Lynnwood into downtown Seattle. */
    private val interlinedLegRef = RouteLegRef(
        routeId = "40_2LINE",
        headsign = "Downtown Redmond",
        board = RouteStopRef("40_N23-T2", "N23", "Lynnwood City Center", boardPoint),
        alight = RouteStopRef("40_1108", "1108", "Westlake", alightPoint),
        alternatives = listOf(
            AlternativeRouteRef(
                routeId = "40_100479",
                headsign = "Federal Way Downtown",
                shortName = "1 Line",
                routeColor = 0xFF00A651.toInt()
            )
        ),
        badge = LegBadge(
            listOf(
                RouteBadge("1 Line", 0xFF00A651.toInt()),
                RouteBadge("2 Line", 0xFF0075C4.toInt())
            )
        )
    )

    private val ride = TripLogEntry.Transit(
        routeShortName = "2 Line",
        routeDisplayName = "2 Line",
        routeColorHex = "0075C4",
        headsign = "Downtown Redmond",
        boardTime = ServerTime(2 * 60_000L),
        exitTime = ServerTime(32 * 60_000L),
        durationMinutes = 30,
        realtime = RealtimeState.Unknown,
        rideEvents = emptyList(),
        routeLeg = interlinedLegRef,
        legPoints = listOf(boardPoint, alightPoint)
    )

    private fun state(routeLeg: RouteLegRef) = TripResultsUiState.Success(
        options = listOf(
            ItineraryOption(
                mode = ModeSummary.Routes(listOf(routeLeg.badge)),
                durationMinutes = 32L,
                startTime = ServerTime(0L),
                endTime = ServerTime(32 * 60_000L)
            )
        ),
        selectedIndex = 0,
        directions = listOf(ride.copy(routeLeg = routeLeg))
    )

    /** Both roundels — the option card's and the ride's — name each interchangeable route. */
    @Test
    fun interchangeableRoutesAreBadgedInThePickerAndTheLog() {
        composeRule.setContent { TripResultsList(state = state(interlinedLegRef)) }

        // One node per route in each of the two badges: the option card's and the ride's.
        composeRule.onAllNodesWithText("1 Line").assertCountEquals(2)
        composeRule.onAllNodesWithText("2 Line").assertCountEquals(2)
    }

    /** The ride carries the caption that makes the badge an instruction, not just a label. */
    @Test
    fun interchangeableRideSaysToTakeWhicheverComesFirst() {
        composeRule.setContent { TripResultsList(state = state(interlinedLegRef)) }

        composeRule
            .onNodeWithText(context.getString(R.string.directions_whichever_comes_first))
            .assertExists()
    }

    /** A ride the rider can't substitute anything for badges its own route and adds no caption. */
    @Test
    fun rideWithoutInterchangeableRoutesBadgesOnlyItsOwnRoute() {
        val soloRef = interlinedLegRef.copy(
            alternatives = emptyList(),
            badge = LegBadge(listOf(RouteBadge("2 Line", 0xFF0075C4.toInt())))
        )
        composeRule.setContent { TripResultsList(state = state(soloRef)) }

        composeRule.onAllNodesWithText("1 Line").assertCountEquals(0)
        composeRule
            .onAllNodesWithText(context.getString(R.string.directions_whichever_comes_first))
            .assertCountEquals(0)
    }

    /**
     * The badge's color comes from the route's GTFS color, tolerating the '#' some feeds prefix. Lives
     * on-device because parsing it goes through `android.graphics.Color`, which the JVM tests
     * (`RouteBadgesTest`, which covers the naming and ordering) can't call.
     */
    @Test
    fun badgeTakesItsColorFromTheRoutesGtfsColor() {
        val badge = TripLeg(
            mode = TripMode.TRAM,
            routeId = "40:2LINE",
            routeShortName = "2 Line",
            routeColor = "#0075C4"
        ).plannedBadge()

        assertEquals("2 Line", badge.shortName)
        assertEquals(0xFF0075C4.toInt(), badge.routeColor)
    }
}
