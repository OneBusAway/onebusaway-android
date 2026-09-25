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
package org.onebusaway.android.ui.mylists

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.tracking.TrackedRoute
import org.onebusaway.android.tracking.TrackedRouteKey
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * A trackable arrival badge takes the row's long press for itself (#2166) — but `combinedClickable`
 * is a terminal gesture handler, so taking the long press takes the *tap* as well. These pin the
 * badge's two gestures apart: a tap opens the stop like the rest of the row, a long press does not.
 */
class ArrivalBadgeTapTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun tappingATrackableBadgeOpensTheStop() {
        var opened = 0
        setStarredStopRow(onClick = { opened++ })

        composeRule.onNodeWithText(BADGE_TEXT).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun longPressingATrackableBadgeOffersTrackingInsteadOfOpeningTheStop() {
        var opened = 0
        setStarredStopRow(onClick = { opened++ })

        // Driven through the semantics action rather than an injected hold, for the reason
        // RouteArrivalRowLongPressTest documents: the gesture timing is racy on the CI emulator.
        composeRule.onNodeWithText(BADGE_TEXT).performSemanticsAction(SemanticsActions.OnLongClick)

        val trackLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.bus_options_menu_track_route)
        // The menu opens in its own window, which can lay out a frame after the main composition
        // reports idle.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(trackLabel).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, opened)
    }

    /** A starred-stops row showing one trackable badge, with [onClick] as the stop's own tap. */
    private fun setStarredStopRow(onClick: () -> Unit) {
        val stop = StopListItem(
            id = "1_100",
            name = "Pine St & 3rd Ave",
            rawDirection = "N",
            directionText = "Northbound",
            lat = 47.61,
            lon = -122.33,
            isFavorite = true,
            arrivals = StopArrivals.Loaded(
                listOf(
                    ArrivalBadge(
                        text = BADGE_TEXT,
                        colorRes = R.color.stop_info_ontime_fill,
                        trackable = TrackedRoute(
                            key = TrackedRouteKey("1_100", "1_40", "Downtown Seattle"),
                            routeName = "40",
                            stopName = "Pine St & 3rd Ave",
                            stopLat = 47.61,
                            stopLon = -122.33
                        )
                    )
                )
            )
        )
        composeRule.setContent {
            StopRow(item = stop, onClick = onClick, actions = emptyList(), onToggleTracking = {})
        }
    }

    private companion object {
        const val BADGE_TEXT = "40 — 4 min"
    }
}
