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

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks

/**
 * On-device regression test for the per-row service-alert indicator (issue #1687 Bug 2), which sits
 * overlaid on the badge section's top-right corner rather than inline in the row. Renders the real
 * [RouteArrivalRow] and asserts the warning glyph is composed, displayed, tappable, and lined up with
 * the corner star — the refactor from an inline child to a corner overlay must not drop it, make it
 * unreachable, or misalign it. (The group-scan that decides *when* to show it —
 * [RouteRowGroup.activeAlertSituationId] — is covered by cheap JVM tests in RouteRowGroupingTest.)
 */
class ArrivalAlertIndicatorTest {

    // See EtaStripJustifyTest for why the v1 (Unconfined) rule is used here (issue #1792).
    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTappableAlertIndicatorWhenArrivalHasActiveAlert() {
        val trip = previewArrival("40", "Northgate", etaMinutes = 3)
        val actions = ArrivalActions(
            tripId = trip.tripId,
            routeId = "route_40",
            routeShortName = "40",
            routeLongName = "Downtown Seattle",
            routeColor = 0xFF0A5B3E.toInt(),
            scheduleUrl = null,
            agencyName = null,
            blockId = null,
            alertSituationId = "situation-1",
        )

        composeRule.setContent {
            RouteArrivalRow(
                group = RouteRowGroup(listOf(trip)),
                actionsFor = { actions },
                isFavorite = false,
                callbacks = previewRowCallbacks(),
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alertDescription = context.getString(R.string.stop_info_arrival_service_alert)
        composeRule.onNodeWithContentDescription(alertDescription)
            .assertIsDisplayed()
            .assertHasClickAction()

        // The alert glyph and the corner favorite star should sit at the same vertical level (the
        // triangle's top flush with the star's top). Both use an identical 28dp box / 20dp glyph, so
        // equal top bounds means equal glyph tops. The star reads as "add star" here (isFavorite=false).
        val starDescription = context.getString(R.string.bus_options_menu_add_star)
        val alertTop = composeRule.onNodeWithContentDescription(alertDescription)
            .getUnclippedBoundsInRoot().top
        val starTop = composeRule.onNodeWithContentDescription(starDescription)
            .getUnclippedBoundsInRoot().top
        assertEquals(starTop.value, alertTop.value, 1.5f)
    }
}
