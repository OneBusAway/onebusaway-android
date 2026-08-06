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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The NOW pill is reachable (issue #2177).
 *
 * It used to be selected by an exact-zero test, so it was on screen for at most the single minute a
 * departure spent at zero, and a bus that had just pulled out fell through to the numeric branch and
 * rendered "-1min" — while the trip-tracking notification, showing the same departure, said "Now".
 * The cutoff is [org.onebusaway.android.time.isEtaNow] now, shared by both surfaces, so this pins
 * the whole window rather than the one instant.
 *
 * Deliberately not `@SmokeTest`: the API-23 floor subset (#1818) keeps one strip render test, and
 * that stays [EtaStripRenderTest].
 */
class EtaStripNowPillRenderTest {

    // Unconfined composition — see createUnconfinedComposeRule (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val now = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.stop_info_eta_now)

    /**
     * Renders one pill per [etaMinutes]. The values are stable for the test's duration: previewArrival
     * anchors the server "now" at 0 and liveEta floors each side to its own minute, so each pill holds
     * its exact value for the first 60s.
     */
    private fun showStrip(vararg etaMinutes: Long) {
        composeRule.setContent {
            Box(Modifier.width(320.dp)) {
                EtaStrip(
                    // Distinct trip ids: the strip's LazyRow keys on trip-instance identity and a
                    // duplicate key throws (see EtaStrip's itemsIndexed).
                    trips = etaMinutes.mapIndexed { i, eta ->
                        previewArrival("8", "Rainier Beach", etaMinutes = eta, tripId = "trip_$i")
                    },
                    actionsFor = { null },
                    callbacks = previewRowCallbacks()
                )
            }
        }
    }

    @Test
    fun everyDepartureInsideTheNowWindowReadsNow() {
        // -2 is the far edge of NOW_WINDOW; 1 is the first pill outside it on the upcoming side.
        showStrip(-2, -1, 0, 1)

        composeRule.onAllNodesWithText(now).assertCountEquals(3)
        // The regression itself: nothing inside the window fell through to a negative countdown.
        composeRule.onAllNodesWithText("-", substring = true).assertCountEquals(0)
    }

    @Test
    fun aBusThatIsActuallyGoneStillSaysHowLongAgo() {
        // Past the window the strip goes back to a countdown — the arrivals response keeps departed
        // trips for a few minutes, and a bus that left four minutes ago is gone, not here.
        showStrip(-4)

        composeRule.onAllNodesWithText(now).assertCountEquals(0)
        composeRule.onAllNodesWithText("-4", substring = true).assertCountEquals(1)
    }
}
