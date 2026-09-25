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
package org.onebusaway.android.ui.arrivals

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The stop→route→trip focus (#2205) as the drawer shows it: the focused trip's ETA pill is outlined,
 * and only that one. Asserted through the pill's `selected` semantics — the handle the outline is
 * published under — rather than by sampling pixels, so the test states the meaning, not the colour.
 *
 * The two pills are told apart by the "NOW" label rather than by their countdowns: the ETA and clock
 * lines are digits, which a device in the wrong timezone can make ambiguous, while the label is read
 * from resources and appears on exactly one of the two.
 */
class EtaPillFocusOutlineTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val nowLabel: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.stop_info_eta_now)

    @Test
    fun onlyTheFocusedTripsPillIsOutlined() {
        setRow(selectedTripId = TRIP_NOW)

        assertSelectedPillCount(1)
        // ...and it is the focused trip's pill, not merely some pill.
        assertSelectedNowPillCount(1)
    }

    @Test
    fun theOutlineFollowsWhichTripIsFocused() {
        setRow(selectedTripId = TRIP_LATER)

        assertSelectedPillCount(1)
        assertSelectedNowPillCount(0)
    }

    @Test
    fun noPillIsOutlinedWhenTheFocusStopsAtTheRoute() {
        setRow(selectedTripId = null)

        assertSelectedPillCount(0)
    }

    private fun assertSelectedPillCount(expected: Int) {
        composeRule.onAllNodes(isSelected(), useUnmergedTree = true).assertCountEquals(expected)
    }

    /** How many outlined pills are the "NOW" one — i.e. whether the outline landed on [TRIP_NOW]. */
    private fun assertSelectedNowPillCount(expected: Int) {
        composeRule.onAllNodes(
            isSelected() and hasAnyDescendant(hasText(nowLabel)),
            useUnmergedTree = true
        ).assertCountEquals(expected)
    }

    /** A selected two-trip route row, drilled into [selectedTripId] (null = the plain route focus). */
    private fun setRow(selectedTripId: String?) {
        val trips = listOf(
            previewArrival("40", "Northgate", etaMinutes = 0, tripId = TRIP_NOW),
            previewArrival("40", "Northgate", etaMinutes = 9, tripId = TRIP_LATER)
        )
        composeRule.setContent {
            RouteArrivalRow(
                group = RouteRowGroup(trips),
                actionsFor = { null },
                isFavorite = false,
                callbacks = previewRowCallbacks(),
                // The row is the map's selected route, which is what supplies the outline colour the
                // pill borrows.
                mapRouteColor = 0xFF0A5B3E.toInt(),
                selected = true,
                selectedTripId = selectedTripId
            )
        }
    }

    private companion object {
        const val TRIP_NOW = "trip_now"
        const val TRIP_LATER = "trip_later"
    }
}
