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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks

/**
 * On-device regression test for the strip justifying to its first non-negative ETA (recent-past
 * pills overflowing off the left, see EtaStrip's `start` parameter). Renders the real composable —
 * not a fake — in a narrow host so the negative-ETA pills must scroll off to force layout, then
 * asserts the scroll actually happened, since a broken justify silently leaves scrollState at 0.
 */
class EtaStripJustifyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun justifiesPastRecentPastEtas() {
        val trips = listOf(
            previewArrival("8", "Rainier Beach", etaMinutes = -9),
            previewArrival("8", "Rainier Beach", etaMinutes = -2),
            previewArrival("40", "Northgate", etaMinutes = 3),
            previewArrival("40", "Northgate", etaMinutes = 11),
            previewArrival("40", "Northgate", etaMinutes = 19),
            previewArrival("40", "Northgate", etaMinutes = 27),
        )
        val firstUpcomingIndex = trips.indexOfFirst { it.eta >= 0 }
        lateinit var scrollState: ScrollState

        composeRule.setContent {
            scrollState = rememberScrollState()
            Box(Modifier.width(160.dp)) {
                EtaStrip(
                    trips = trips,
                    dataVersion = 1L,
                    actionsFor = { null },
                    callbacks = previewRowCallbacks(),
                    start = firstUpcomingIndex,
                    scrollState = scrollState,
                )
            }
        }

        composeRule.waitForIdle()

        assertTrue(
            "expected the strip to scroll the recent-past pills off the left edge, " +
                "but scrollState.value was ${scrollState.value}",
            scrollState.value > 0,
        )
    }
}
