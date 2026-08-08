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
package org.onebusaway.android.ui.home.directions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.tripplan.pinned.PinnedLabel
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripCardState
import org.onebusaway.android.ui.tripresults.LegBadge
import org.onebusaway.android.ui.tripresults.ModeSymbol
import org.onebusaway.android.ui.tripresults.TransitMode

/**
 * The way back to a parked trip (#2053). Its two tap targets are nested — the ✕ sits inside a card
 * whose whole surface resumes — so the risk is a dismiss that also resumes, or a card body that only
 * reaches the ✕.
 */
class PinnedTripCardTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun itNamesTheDestinationAndTheRoutesTheTripRides() {
        show()

        composeRule.onNodeWithText(
            context.getString(R.string.trip_plan_pinned_trip_title, "Pike Place Market")
        ).assertIsDisplayed()
        composeRule.onNodeWithText("D Line", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anEndWithNoNameOfItsOwnFallsBackToItsFixedLabel() {
        show(destination = PinnedLabel.Resource(R.string.trip_plan_map_location))

        composeRule.onNodeWithText(
            context.getString(
                R.string.trip_plan_pinned_trip_title,
                context.getString(R.string.trip_plan_map_location)
            )
        ).assertIsDisplayed()
    }

    @Test
    fun tappingTheCardResumesTheTripWithoutUnpinningIt() {
        var resumed = 0
        var unpinned = 0
        show(onResume = { resumed++ }, onUnpin = { unpinned++ })

        composeRule.onNodeWithText(
            context.getString(R.string.trip_plan_pinned_trip_title, "Pike Place Market")
        ).performClick()

        assertEquals(1, resumed)
        assertEquals(0, unpinned)
    }

    @Test
    fun theDismissButtonUnpinsWithoutResuming() {
        var resumed = 0
        var unpinned = 0
        show(onResume = { resumed++ }, onUnpin = { unpinned++ })

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.trip_plan_unpin_content_description)
        ).performClick()

        assertEquals(1, unpinned)
        assertEquals("throwing the pin away must not open the trip", 0, resumed)
    }

    private fun show(
        destination: PinnedLabel = PinnedLabel.Text("Pike Place Market"),
        onResume: () -> Unit = {},
        onUnpin: () -> Unit = {}
    ) {
        composeRule.setContent {
            ObaTheme {
                PinnedTripCard(
                    state = PinnedTripCardState(
                        destination = destination,
                        symbols = listOf(
                            ModeSymbol.Transit(
                                LegBadge(
                                    routes = listOf(RouteBadge("D Line", routeColor = null)),
                                    mode = TransitMode.BUS,
                                    join = RouteBadgeJoin.ANY_OF
                                )
                            )
                        ),
                        durationMinutes = 24
                    ),
                    onResume = onResume,
                    onUnpin = onUnpin
                )
            }
        }
        composeRule.waitForIdle()
    }
}
