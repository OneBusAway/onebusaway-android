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

import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.util.GeoPoint

/**
 * Verifies the trip-log tap wiring in [TripResultsList]. Tapping a **leg header** highlights it on the
 * map (a transit leg → its route via `onFocusRouteLeg`; a walk leg → its polyline via `onFocusLeg`); its
 * minor events — a walk's turn steps, a ride's intermediate stops — expand only via the leg's own chevron
 * control, never as a side effect of the map-focus tap (#2040). A revealed step/stop then focuses its own
 * point. A transit leg's Board stop shows its live ETA strip. Drives the real click wiring by node text
 * (and, for the chevron, its content description), not coordinates.
 */
class DirectionRowFocusTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val startPoint = GeoPoint(47.6090, -122.3290)
    private val walkLegPoints = listOf(GeoPoint(47.6100, -122.3300), GeoPoint(47.6120, -122.3320))
    private val transitLegPoints = listOf(GeoPoint(47.6150, -122.3350), GeoPoint(47.6200, -122.3400))
    private val boardPoint = GeoPoint(47.6150, -122.3350)
    private val alightPoint = GeoPoint(47.6200, -122.3400)
    private val stopMidPoint = GeoPoint(47.6175, -122.3375)
    private val stepPoint = GeoPoint(47.6110, -122.3310)

    private val start = TripLogEntry.Terminal(TerminalKind.START, ServerTime(0L), "Origin Plaza", startPoint)

    private val walk = TripLogEntry.Walk(
        mode = StreetMode.WALK,
        durationMinutes = 4,
        distanceMeters = 320.0,
        isTransfer = false,
        steps = listOf(LogStep("Turn left onto Pike St", point = stepPoint)),
        legPoints = walkLegPoints,
        focusPoint = walkLegPoints.first(),
        legIndices = setOf(0)
    )

    private val routeLeg = RouteLegRef(
        routeId = "1_100",
        headsign = "Rainier Beach",
        board = RouteStopRef("1_500", "500", "Pine St & 3rd Ave", boardPoint),
        alight = RouteStopRef("1_600", "600", "Rainier & Alaska", alightPoint)
    )

    private val transitTitle = "Route 8 Line"

    private val transit = TripLogEntry.Transit(
        routeShortName = "8",
        routeDisplayName = transitTitle,
        mode = TransitMode.BUS,
        routeColorHex = "1B6EF3",
        headsign = "Rainier Beach",
        reachStopTime = ServerTime(3 * 60_000L),
        boardTime = ServerTime(4 * 60_000L),
        exitTime = ServerTime(20 * 60_000L),
        durationMinutes = 16,
        realtime = RealtimeState.OnTime,
        rideEvents = listOf(RideEvent.Stop(LogStop("Capitol Hill Station", stopMidPoint))),
        routeLeg = routeLeg,
        legPoints = transitLegPoints,
        legIndices = setOf(1)
    )

    private val arrive = TripLogEntry.Terminal(TerminalKind.ARRIVE, ServerTime(32 * 60_000L), "Alaska Junction", alightPoint)

    private fun state(directions: List<TripLogEntry>) = TripResultsUiState.Success(
        options = listOf(
            ItineraryOption(
                symbols = listOf(ModeSymbol.Transit(LegBadge(listOf(RouteBadge("8", routeColor = null)), TransitMode.BUS, RouteBadgeJoin.ANY_OF))),
                durationMinutes = 32L,
                startTime = ServerTime(0L),
                endTime = ServerTime(32 * 60_000L)
            )
        ),
        selectedIndex = 0,
        directions = directions
    )

    private val fullState = state(listOf(start, walk, transit, arrive))

    // Isolates one expandable leg at a time, so its chevron's content description is the only node
    // carrying the expand/collapse label — fullState has two expandable legs sharing that label.
    private val walkOnlyState = state(listOf(start, walk, arrive))
    private val transitOnlyState = state(listOf(start, transit, arrive))

    private val walkAction = context.getString(R.string.step_by_step_non_transit_mode_walk_action)
    private val midStopName = "Capitol Hill Station"

    @Test
    fun tappingWalkHeader_framesTheLeg_withoutRevealingItsSteps() {
        var framed: FocusedLeg? = null
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(state = walkOnlyState, onFocusLeg = { framed = it }, onFocusPoint = { focused = it })
        }

        // The walk's turn steps are collapsed to start.
        composeRule.onNodeWithText(walk.steps.single().text).assertDoesNotExist()

        // Tapping the walk header frames the leg but does not reveal its steps (#2040) — that's the
        // chevron's job, checked separately below.
        composeRule.onNodeWithText(walkAction).performClick()
        // The focus carries which itinerary leg it is, so the map can recede the rest of the trip
        // around it rather than drawing this leg alone (#2048).
        assertEquals(FocusedLeg(walkLegPoints, setOf(0)), framed)
        composeRule.onNodeWithText(walk.steps.single().text).assertDoesNotExist()

        // Tapping the chevron reveals the steps without re-framing the leg.
        framed = null
        composeRule.onNodeWithContentDescription(context.getString(R.string.trip_plan_expand_leg))
            .performClick()
        assertNull(framed)
        composeRule.onNodeWithText(walk.steps.single().text).assertExists()

        // The revealed step focuses its own point.
        composeRule.onNodeWithText(walk.steps.single().text).performClick()
        assertEquals(stepPoint, focused)
    }

    @Test
    fun tappingTransitHeader_highlightsRoute_withoutRevealingIntermediateStops() {
        var captured: Pair<RouteLegRef, FocusedLeg>? = null
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(
                state = transitOnlyState,
                onFocusRouteLeg = { rl, leg -> captured = rl to leg },
                onFocusPoint = { focused = it }
            )
        }

        // The intermediate stop is collapsed to start.
        composeRule.onNodeWithText(midStopName).assertDoesNotExist()

        // Tapping the transit header highlights the route but does not reveal the stops (#2040).
        composeRule.onNodeWithText(transitTitle).performClick()
        assertEquals("1_100", captured?.first?.routeId)
        assertEquals(FocusedLeg(transitLegPoints, setOf(1)), captured?.second)
        composeRule.onNodeWithText(midStopName).assertDoesNotExist()

        // Tapping the chevron reveals the stops without re-highlighting the route.
        captured = null
        composeRule.onNodeWithContentDescription(context.getString(R.string.trip_plan_expand_leg))
            .performClick()
        assertNull(captured)
        composeRule.onNodeWithText(midStopName).assertExists()

        // The revealed stop focuses its own point.
        composeRule.onNodeWithText(midStopName).performClick()
        assertEquals(stopMidPoint, focused)
    }

    @Test
    fun theChevronAnnouncesWhatItsTapDoesToTheSteps() {
        composeRule.setContent { TripResultsList(state = walkOnlyState) }

        // Collapsed: the chevron offers to reveal.
        composeRule.onNodeWithContentDescription(context.getString(R.string.trip_plan_expand_leg))
            .assertExists()

        // …and once expanded it offers the inverse.
        composeRule.onNodeWithContentDescription(context.getString(R.string.trip_plan_expand_leg))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.trip_plan_collapse_leg))
            .assertExists()
    }

    @Test
    fun aLegWithNoMinorEventsShowsNoChevron() {
        val bare = walk.copy(steps = emptyList())
        composeRule.setContent { TripResultsList(state = state(listOf(bare, arrive))) }

        // Nothing to reveal, so no chevron is rendered at all.
        composeRule.onNodeWithContentDescription(context.getString(R.string.trip_plan_expand_leg))
            .assertDoesNotExist()
    }

    @Test
    fun transitLeg_showsTheBoardStopEtaStrip() {
        composeRule.setContent {
            TripResultsList(
                state = fullState,
                stopEtaStrip = { _, stop -> Text("ETASTRIP@${stop.name}") }
            )
        }

        // The Board strip is shown; the Alight stop has no ETA strip.
        composeRule.onNodeWithText("ETASTRIP@${routeLeg.board?.name}").assertExists()
        composeRule.onNodeWithText("ETASTRIP@${routeLeg.alight?.name}").assertDoesNotExist()
    }

    @Test
    fun tappingBoardStopLabel_zoomsToThatStop() {
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(state = fullState, onFocusPoint = { focused = it })
        }

        composeRule.onNodeWithText(routeLeg.board!!.name!!).performClick()
        assertEquals(boardPoint, focused)
    }

    @Test
    fun tappingStartTerminal_zoomsToItsPoint() {
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(state = fullState, onFocusPoint = { focused = it })
        }

        composeRule.onNodeWithText(start.place).performClick()
        assertEquals(startPoint, focused)
    }

    @Test
    fun walkLegWithoutPolyline_fallsBackToFocusingItsPoint() {
        val noGeometry = walk.copy(legPoints = emptyList(), focusPoint = boardPoint, steps = emptyList())
        var framed: FocusedLeg? = null
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(
                state = state(listOf(noGeometry, arrive)),
                onFocusLeg = { framed = it },
                onFocusPoint = { focused = it }
            )
        }

        composeRule.onNodeWithText(walkAction).performClick()

        assertNull(framed)
        assertEquals(boardPoint, focused)
    }

    @Test
    fun walkLegWithNeitherPolylineNorPoint_movesTheMapNowhere() {
        val inert = walk.copy(legPoints = emptyList(), focusPoint = null, steps = emptyList())
        var framed: FocusedLeg? = null
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(
                state = state(listOf(inert, arrive)),
                onFocusLeg = { framed = it },
                onFocusPoint = { focused = it }
            )
        }

        composeRule.onNodeWithText(walkAction).performClick()

        assertNull(framed)
        assertNull(focused)
    }
}
