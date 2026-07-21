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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

class RouteArrivalRowLongPressTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun longPressOpensScheduleWithoutOverflowButton() {
        val trip = previewArrival("40", "Northgate", etaMinutes = 3)
        val scheduleUrl = "https://example.com/routes/40"
        var openedScheduleUrl: String? = null
        val callbacks = ArrivalRowCallbacks(
            onRouteFavorite = {},
            onShowVehiclesOnMap = {},
            onEtaClick = {},
            onShowTripStatus = {},
            onSetReminder = {},
            onShowRouteSchedule = { openedScheduleUrl = it },
            onReportArrivalProblem = {},
            onShowAlert = {}
        )
        val actions = ArrivalActions(
            tripId = trip.tripId,
            routeId = trip.routeId,
            routeShortName = trip.shortName.orEmpty(),
            routeLongName = "Downtown Seattle",
            routeColor = 0xFF0A5B3E.toInt(),
            scheduleUrl = scheduleUrl,
            agencyName = null,
            blockId = null
        )

        composeRule.setContent {
            RouteArrivalRow(
                group = RouteRowGroup(listOf(trip)),
                actionsFor = { actions },
                isFavorite = false,
                callbacks = callbacks
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The route-level action moved off a dedicated overflow button and onto the row's long press,
        // so the overflow affordance must be gone.
        composeRule.onAllNodesWithContentDescription(
            context.getString(R.string.stop_info_item_options_title)
        ).assertCountEquals(0)

        // Drive the long press through the row's OnLongClick *semantics action* rather than an injected
        // longClick() gesture. combinedClickable registers the same lambda as both the gesture and the
        // accessibility action, so invoking the action exercises the real wiring — but deterministically,
        // without depending on the long-press timeout elapsing against the test's virtual frame clock
        // (that timing was racy on the CI emulator: the hold sometimes registered as a plain tap, so the
        // menu never opened). onLongClickLabel disambiguates the row's schedule action from the ETA
        // pill's own trip-actions long press.
        val scheduleLabel = context.getString(R.string.bus_options_menu_show_route_schedule)
        composeRule.onNode(
            SemanticsMatcher("has long-press action labeled \"$scheduleLabel\"") { node ->
                node.config.getOrNull(SemanticsActions.OnLongClick)?.label == scheduleLabel
            }
        ).performSemanticsAction(SemanticsActions.OnLongClick)

        // The centered menu opens in a Dialog (a separate window); on slower devices (e.g. the CI
        // emulator) the main composition can report idle a frame before that window is laid out, so
        // wait for the schedule item to appear before acting on it rather than asserting immediately.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(scheduleLabel).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(scheduleLabel).performClick()

        assertEquals(scheduleUrl, openedScheduleUrl)
    }
}
