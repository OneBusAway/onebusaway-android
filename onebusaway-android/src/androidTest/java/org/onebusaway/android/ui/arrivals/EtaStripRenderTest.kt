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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.SmokeTest
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * On-device render smoke for the ETA strip: renders the real [EtaStrip] and asserts a pill's ETA text
 * actually composed. Carries `@SmokeTest` (moved here from the retired SlideBoxTest/EtaStripJustifyTest):
 * the API-23 floor smoke subset (#1818) needs one strip test exercising Compose rendering on a 2015-era
 * runtime.
 */
@SmokeTest
class EtaStripRenderTest {

    // Unconfined composition — see createUnconfinedComposeRule (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun rendersPills() {
        composeRule.setContent {
            Box(Modifier.width(320.dp)) {
                EtaStrip(
                    // Distinct trip ids per pill: the strip's LazyRow keys on trip-instance identity
                    // and a duplicate key throws (see EtaStrip's itemsIndexed).
                    trips = listOf(
                        previewArrival("8", "Rainier Beach", etaMinutes = -2, tripId = "trip_1"),
                        previewArrival("8", "Rainier Beach", etaMinutes = 5, tripId = "trip_2"),
                        previewArrival("40", "Northgate", etaMinutes = 12, tripId = "trip_3")
                    ),
                    actionsFor = { null },
                    callbacks = previewRowCallbacks()
                )
            }
        }

        // Assert on a pill's ETA text, not just any clickable node: the flanking chevron gutters are
        // clickable even when hidden, so a bare hasClickAction() match would pass with zero pills
        // composed. The recent-past "-2" countdown is the strip's only "-2" — the gutters render only an
        // icon, and no clock subline contains it — so this uniquely proves the LazyRow built its items.
        // The value is stable for the test's duration: previewArrival anchors serverNow at 0 and
        // liveEta divides each side into whole minutes, so the pill reads exactly -2 for the first 60s.
        composeRule.onNodeWithText("-2", substring = true).assertExists()
    }
}
