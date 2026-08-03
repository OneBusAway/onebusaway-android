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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.AlertSeverity
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Accessibility coverage for the two severity indicators added to trip results by #2143. */
class AlertAccessibilityTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val severeLabel get() = context.getString(R.string.service_alert_severity_severe)

    @Test
    fun alertBannerAnnouncesItsSeverity() {
        composeRule.setContent {
            ObaTheme {
                TripAlertBanner(
                    TripAlertItem(
                        contentId = "suspension",
                        summary = "Service is suspended",
                        description = null,
                        url = null,
                        severity = AlertSeverity.ERROR
                    )
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasStateDescription(severeLabel)).assertExists()
    }

    @Test
    fun alertedLegMarkerAnnouncesTheSameSeverity() {
        val option = ItineraryOption(
            symbols = listOf(
                ModeSymbol.Transit(
                    badge = LegBadge(
                        routes = listOf(RouteBadge("1 Line", routeColor = null)),
                        mode = TransitMode.RAIL,
                        join = RouteBadgeJoin.ANY_OF
                    ),
                    alert = AlertSeverity.ERROR
                )
            ),
            durationMinutes = 10,
            startTime = ServerTime(0L),
            endTime = ServerTime(10 * 60_000L)
        )
        composeRule.setContent {
            ObaTheme {
                TripResultsHeader(
                    state = TripResultsUiState.Success(
                        options = listOf(option),
                        selectedIndex = 0,
                        directions = emptyList()
                    ),
                    onSelectOption = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.directions_leg_service_alert, severeLabel),
            useUnmergedTree = true
        ).assertExists()
    }
}
