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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.components.previewArrival

class RouteArrivalRowLongPressTest {

    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

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
            onShowAlert = {},
        )
        val actions = ArrivalActions(
            tripId = trip.tripId,
            routeId = trip.routeId,
            routeShortName = trip.shortName.orEmpty(),
            routeLongName = "Downtown Seattle",
            routeColor = 0xFF0A5B3E.toInt(),
            scheduleUrl = scheduleUrl,
            agencyName = null,
            blockId = null,
        )

        composeRule.setContent {
            RouteArrivalRow(
                group = RouteRowGroup(listOf(trip)),
                actionsFor = { actions },
                isFavorite = false,
                callbacks = callbacks,
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onAllNodesWithContentDescription(
            context.getString(R.string.stop_info_item_options_title)
        ).assertCountEquals(0)

        composeRule.onNodeWithText("Northgate").performTouchInput { longClick() }
        composeRule.onNodeWithText(
            context.getString(R.string.bus_options_menu_show_route_schedule)
        ).assertIsDisplayed().performClick()

        assertEquals(scheduleUrl, openedScheduleUrl)
    }
}
