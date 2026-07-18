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

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * Verifies the itinerary line items are tap-to-focus at the leaf level only: tapping a leaf
 * [DirectionItem] (no sub-steps) invokes `onFocusPoint` with its point, an expandable row only toggles
 * its sub-steps (never focuses the map), and a revealed leaf sub-item focuses its own point. Exercises
 * the real [TripResultsList] click wiring by node text (identity), not screen coordinates.
 */
class DirectionRowFocusTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val walkPoint = GeoPoint(47.6100, -122.3300)
    private val stopAPoint = GeoPoint(47.6200, -122.3400)
    private val boardPoint = GeoPoint(47.6150, -122.3350)

    private val walk = DirectionItem(
        iconRes = DirectionItem.NO_ICON,
        text = "1. Walk to Pine St & 3rd Ave",
        focusPoint = walkPoint,
    )

    private val stopA = DirectionItem(
        iconRes = DirectionItem.NO_ICON,
        text = "Capitol Hill Station",
        focusPoint = stopAPoint,
    )

    private val board = DirectionItem(
        iconRes = DirectionItem.NO_ICON,
        text = "2. Board Route 8",
        isTransit = true,
        subItems = listOf(stopA),
        focusPoint = boardPoint,
    )

    // The option card is incidental here (this test drives the directions list, not the header); any
    // valid ItineraryOption suffices.
    private val state = TripResultsUiState.Success(
        options = listOf(
            ItineraryOption(
                mode = ModeSummary.Label("Route 8"),
                durationMinutes = 32L,
                startTime = ServerTime(0L),
                endTime = ServerTime(0L),
            )
        ),
        selectedIndex = 0,
        directions = listOf(walk, board),
    )

    @Test
    fun tappingWalkRowFocusesItsPoint() {
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(state = state, onFocusPoint = { focused = it })
        }

        composeRule.onNodeWithText(walk.text).performClick()

        assertEquals(walkPoint, focused)
    }

    @Test
    fun tappingExpandableRowOnlyExpands_thenLeafSubStopFocusesItsOwnPoint() {
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(state = state, onFocusPoint = { focused = it })
        }

        // Sub-steps are collapsed to start.
        composeRule.onNodeWithText(stopA.text).assertDoesNotExist()

        // An expandable row (chevron) only reveals its sub-steps — it does NOT move the map, even though
        // it carries a boarding point.
        composeRule.onNodeWithText(board.text).performClick()
        assertNull(focused)
        composeRule.onNodeWithText(stopA.text).assertExists()

        // The revealed leaf sub-step focuses its own point.
        composeRule.onNodeWithText(stopA.text).performClick()
        assertEquals(stopAPoint, focused)
    }

    @Test
    fun rowWithoutAPointDoesNotFocus() {
        val pointless = DirectionItem(iconRes = DirectionItem.NO_ICON, text = "No coordinates here")
        var focused: GeoPoint? = null
        composeRule.setContent {
            TripResultsList(
                state = state.copy(directions = listOf(pointless)),
                onFocusPoint = { focused = it },
            )
        }

        composeRule.onNodeWithText(pointless.text).performClick()

        assertNull(focused)
    }
}
