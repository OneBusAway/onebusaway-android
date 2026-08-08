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

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The one obvious way out of a pin (#2053). Pinning is a deliberately quiet long press; unpinning must
 * not be, so what matters here is that the button is *there* whenever the drawer is showing the pinned
 * trip — and absent when it isn't, since it says "this trip" and would otherwise be lying.
 */
class UnpinTripButtonTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun theDrawerShowingThePinnedTripOffersToUnpinIt() {
        var unpinned = 0
        show(onUnpinTrip = { unpinned++ })

        composeRule.onNodeWithTag(UNPIN_TRIP_TEST_TAG).assertIsDisplayed().performClick()

        assertEquals(1, unpinned)
    }

    @Test
    fun aDrawerShowingAnUnpinnedTripOffersNothingToUnpin() {
        show(onUnpinTrip = null)

        composeRule.onAllNodesWithTag(UNPIN_TRIP_TEST_TAG).assertCountEquals(0)
    }

    private fun show(onUnpinTrip: (() -> Unit)?) {
        composeRule.setContent {
            ObaTheme {
                TripResultsList(
                    state = TripResultsUiState.Success(
                        options = listOf(option("R1")),
                        selectedIndex = 0,
                        directions = emptyList()
                    ),
                    onUnpinTrip = onUnpinTrip
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun option(shortName: String) = ItineraryOption(
        symbols = listOf(
            ModeSymbol.Transit(
                LegBadge(
                    routes = listOf(RouteBadge(shortName, routeColor = null)),
                    mode = TransitMode.BUS,
                    join = RouteBadgeJoin.ANY_OF
                )
            )
        ),
        durationMinutes = 30,
        startTime = ServerTime(0L),
        endTime = ServerTime(30 * 60_000L)
    )
}
