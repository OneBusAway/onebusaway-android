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

import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/** Semantic coverage for the category emphasis in the itinerary option picker (#2054). */
class ItineraryWinnerHighlightTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun option(
        route: String,
        durationMinutes: Long,
        walkMeters: Double,
        departureMinutes: Long,
        arrivalMinutes: Long
    ) = ItineraryOption(
        symbols = listOf(
            ModeSymbol.Transit(
                LegBadge(
                    listOf(RouteBadge(route, routeColor = null)),
                    TransitMode.BUS,
                    RouteBadgeJoin.ANY_OF
                )
            )
        ),
        durationMinutes = durationMinutes,
        startTime = ServerTime(departureMinutes * 60_000L),
        endTime = ServerTime(arrivalMinutes * 60_000L),
        walkDistanceMeters = walkMeters
    )

    private val state = TripResultsUiState.Success(
        options = listOf(
            option("8", durationMinutes = 10, walkMeters = 0.0, departureMinutes = 0, arrivalMinutes = 10),
            option("48", durationMinutes = 20, walkMeters = 500.0, departureMinutes = 0, arrivalMinutes = 20)
        ),
        selectedIndex = 0,
        directions = emptyList()
    )

    @Test
    fun winningValuesAnnounceTheirCategories() {
        composeRule.setContent {
            TripResultsHeader(
                state,
                onSelectOption = {},
                scheduleWinnerMode = ScheduleWinnerMode.EARLIEST_ARRIVAL
            )
        }

        val description = listOf(
            R.string.trip_plan_winner_shortest_travel_time,
            R.string.trip_plan_winner_least_walking,
            R.string.trip_plan_winner_earliest_arrival
        ).joinToString { context.getString(it) }
        composeRule.onNode(hasStateDescription(description)).assertExists()
    }

    @Test
    fun aZeroDistanceWinnerStillShowsItsWalkingValue() {
        composeRule.setContent {
            TripResultsHeader(
                state,
                onSelectOption = {},
                scheduleWinnerMode = ScheduleWinnerMode.EARLIEST_ARRIVAL
            )
        }

        val zeroDistance = ConversionUtils.getFormattedDistanceParts(0.0, context)
            .joinToString(separator = "") { it.text }
        composeRule.onNodeWithText(zeroDistance).assertExists()
    }

    @Test
    fun highlightingDoesNotChangeCardSelection() {
        var selected = -1
        composeRule.setContent {
            TripResultsHeader(
                state,
                onSelectOption = { selected = it },
                scheduleWinnerMode = ScheduleWinnerMode.EARLIEST_ARRIVAL
            )
        }

        composeRule.onNodeWithText("48").performClick()

        assertEquals(1, selected)
    }
}
