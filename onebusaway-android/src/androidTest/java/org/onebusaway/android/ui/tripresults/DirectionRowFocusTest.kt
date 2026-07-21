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
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.util.GeoPoint

/**
 * Verifies the leg-card tap wiring in [TripResultsList]: tapping a card **body** frames the whole leg
 * (`onFocusLeg` with its polyline), falling back to the leg's point when it has no polyline. A walk/other
 * leg's turn-by-turn steps collapse behind an **expand button** (which only reveals them, never moves the
 * map) and tapping a revealed sub-step focuses its own point; a transit leg's Board/Alight ETA strips are
 * always shown. Drives the real click wiring by node text / content description, not coordinates.
 */
class DirectionRowFocusTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val walkLegPoints = listOf(GeoPoint(47.6100, -122.3300), GeoPoint(47.6120, -122.3320))
    private val transitLegPoints = listOf(GeoPoint(47.6150, -122.3350), GeoPoint(47.6200, -122.3400))
    private val stopAPoint = GeoPoint(47.6175, -122.3375)
    private val boardPoint = GeoPoint(47.6150, -122.3350)

    private val walk = DirectionItem(
        iconRes = DirectionItem.NO_ICON,
        text = "1. Walk to Pine St & 3rd Ave",
        legPoints = walkLegPoints
    )

    private val stopA = DirectionItem(
        iconRes = DirectionItem.NO_ICON,
        text = "Capitol Hill Station",
        focusPoint = stopAPoint
    )

    private val transit = DirectionItem(
        iconRes = DirectionItem.NO_ICON,
        text = "2. Route 8",
        isTransit = true,
        subItems = listOf(stopA),
        legPoints = transitLegPoints,
        focusPoint = boardPoint
    )

    // The option card is incidental here (this test drives the directions list, not the header); any
    // valid ItineraryOption suffices.
    private val state = TripResultsUiState.Success(
        options = listOf(
            ItineraryOption(
                mode = ModeSummary.Label("Route 8"),
                durationMinutes = 32L,
                startTime = ServerTime(0L),
                endTime = ServerTime(0L)
            )
        ),
        selectedIndex = 0,
        directions = listOf(walk, transit)
    )

    @Test
    fun tappingLegBodyFramesTheWholeLeg() {
        var framed: List<GeoPoint>? = null
        composeRule.setContent {
            TripResultsList(state = state, onFocusLeg = { framed = it })
        }

        composeRule.onNodeWithText(walk.text).performClick()

        assertEquals(walkLegPoints, framed)
    }

    @Test
    fun walkLegExpandButtonRevealsSubSteps_withoutMovingTheMap_thenSubStepFocuses() {
        var framed: List<GeoPoint>? = null
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(
                state = state,
                onFocusLeg = { framed = it },
                onFocusPoint = { focused = it }
            )
        }

        // A walk/other leg's turn-by-turn steps are collapsed to start.
        composeRule.onNodeWithText(stopA.text).assertDoesNotExist()

        // The expand button reveals the sub-steps — and moves neither the leg frame nor a point.
        composeRule.onNodeWithContentDescription(context.getString(R.string.trip_plan_expand_leg))
            .performClick()
        assertNull(framed)
        assertNull(focused)
        composeRule.onNodeWithText(stopA.text).assertExists()

        // The revealed sub-step focuses its own point.
        composeRule.onNodeWithText(stopA.text).performClick()
        assertEquals(stopAPoint, focused)
    }

    @Test
    fun legWithoutPolylineFallsBackToFocusingItsPoint() {
        val noGeometry = DirectionItem(
            iconRes = DirectionItem.NO_ICON,
            text = "Short hop",
            focusPoint = boardPoint
        )
        var framed: List<GeoPoint>? = null
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(
                state = state.copy(directions = listOf(noGeometry)),
                onFocusLeg = { framed = it },
                onFocusPoint = { focused = it }
            )
        }

        composeRule.onNodeWithText(noGeometry.text).performClick()

        assertNull(framed)
        assertEquals(boardPoint, focused)
    }

    @Test
    fun legWithoutAPointOrPolylineDoesNothing() {
        val inert = DirectionItem(iconRes = DirectionItem.NO_ICON, text = "No coordinates here")
        var framed: List<GeoPoint>? = null
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(
                state = state.copy(directions = listOf(inert)),
                onFocusLeg = { framed = it },
                onFocusPoint = { focused = it }
            )
        }

        composeRule.onNodeWithText(inert.text).performClick()

        assertNull(framed)
        assertNull(focused)
    }

    // ---- Transit leg: route highlight (leg row) vs. inline ETA strip (Board / Alight sub-items) ----

    private val boardStop = RouteStopRef("1_500", "500", "Pine St & 3rd Ave", boardPoint)
    private val alightStop = RouteStopRef("1_600", "600", "Rainier & Alaska", GeoPoint(47.6200, -122.3400))
    private val routeItem = DirectionItem(
        iconRes = DirectionItem.NO_ICON,
        text = "3. Route 8",
        isTransit = true,
        legPoints = transitLegPoints,
        routeLeg = RouteLegRef(
            routeId = "1_100",
            headsign = "Rainier Beach",
            board = boardStop,
            alight = alightStop
        )
    )
    private val routeState = state.copy(directions = listOf(routeItem))

    // A stand-in ETA strip that marks which stop it was asked to render.
    private fun stripMarker(name: String?) = "ETASTRIP@$name"

    @Test
    fun tappingTransitLegRow_highlightsRoute() {
        var captured: Pair<RouteLegRef, List<GeoPoint>>? = null
        composeRule.setContent {
            TripResultsList(state = routeState, onFocusRouteLeg = { rl, pts -> captured = rl to pts })
        }

        composeRule.onNodeWithText(routeItem.text).performClick()

        assertEquals("1_100", captured?.first?.routeId)
        assertEquals(transitLegPoints, captured?.second)
    }

    @Test
    fun transitLeg_showsOnlyTheBoardStopEtaStrip() {
        composeRule.setContent {
            TripResultsList(
                state = routeState,
                stopEtaStrip = { _, stop, _ -> Text(stripMarker(stop.name)) }
            )
        }

        val boardName = boardStop.name!!
        val alightName = alightStop.name!!
        // The Board strip is shown (no expand toggle); the Alight stop has no ETA strip.
        composeRule.onNodeWithText(stripMarker(boardName)).assertExists()
        composeRule.onNodeWithText(stripMarker(alightName)).assertDoesNotExist()
    }

    @Test
    fun tappingBoardOrAlightLabel_zoomsToThatStop() {
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(state = routeState, onFocusPoint = { focused = it })
        }

        composeRule.onNodeWithText(boardStop.name!!).performClick()
        assertEquals(boardStop.point, focused)

        composeRule.onNodeWithText(alightStop.name!!).performClick()
        assertEquals(alightStop.point, focused)
    }
}
