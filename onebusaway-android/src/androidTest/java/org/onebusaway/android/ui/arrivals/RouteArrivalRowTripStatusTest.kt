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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The labelled way into trip details on the map board (#2428): a row drilled into one of its trips
 * carries a visible "Show trip status" button for that trip, and every pill names its long-press for
 * TalkBack. Which trip the button resolves to is pinned on the JVM by `FocusedTripTest`.
 */
class RouteArrivalRowTripStatusTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val label get() = context.getString(R.string.bus_options_menu_show_trip_details)

    private val soonest = previewArrival("40", "Northgate", etaMinutes = 3, tripId = "trip_1")

    // Scheduled-only: no live vehicle, which the button must serve as well as a tracked trip.
    private val scheduled = previewArrival("40", "Northgate", etaMinutes = 24, predicted = false, tripId = "trip_2")

    @Test
    fun selectedRowOpensItsDrilledIntoTrip() {
        var opened: ArrivalInfo? = null
        setRow(selected = true, selectedTripId = scheduled.tripId, onShowTripStatus = { opened = it })

        composeRule.onNodeWithText(label).assertHasClickAction().performClick()

        assertEquals(scheduled.tripId, opened?.tripId)
    }

    @Test
    fun unselectedRowHasNoButton() {
        setRow(selected = false, selectedTripId = scheduled.tripId)

        composeRule.onAllNodesWithText(label).assertCountEquals(0)
    }

    @Test
    fun routeSelectionWithoutATripHasNoButton() {
        // A row-body tap selects the route but names no trip, so there is no one trip to open.
        setRow(selected = true, selectedTripId = null)

        composeRule.onAllNodesWithText(label).assertCountEquals(0)
    }

    @Test
    fun pillLongPressIsNamedForTalkBack() {
        setRow(selected = false, selectedTripId = null)

        composeRule.onAllNodes(
            SemanticsMatcher("has long-press action labeled \"$label\"") { node ->
                node.config.getOrNull(SemanticsActions.OnLongClick)?.label == label
            }
        ).assertCountEquals(2)
    }

    private fun setRow(
        selected: Boolean,
        selectedTripId: String?,
        onShowTripStatus: (ArrivalInfo) -> Unit = {}
    ) {
        composeRule.setContent {
            RouteArrivalRow(
                group = RouteRowGroup(listOf(soonest, scheduled)),
                actionsFor = { null },
                isFavorite = false,
                callbacks = previewRowCallbacks(onShowTripStatus = onShowTripStatus),
                selected = selected,
                selectedTripId = selectedTripId
            )
        }
    }
}
