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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks

/**
 * On-device regression test for the strip justifying to its first non-negative ETA (recent-past
 * pills overflowing off the left, see EtaStrip's `start` parameter). Renders the real composable —
 * not a fake — and asserts the scroll actually happened, since a broken justify silently leaves
 * scrollState at 0. Covers both a strip that overflows its host and one whose pills fit (where the
 * justify only works because SlideBox floors the content width — see EtaStrip's `minReachablePx`).
 */
class EtaStripJustifyTest {

    // The suggested androidx.compose.ui.test.junit4.v2.createComposeRule runs composition on a
    // StandardTestDispatcher instead of Unconfined; under it, this composable's Modifier.onGloballyPositioned
    // callback never fires at all (confirmed via on-device logcat — composition reaches the pill, but the
    // callback never runs within a 5s poll), not just the expected LaunchedEffect timing shift. Tracked in
    // https://github.com/OneBusAway/onebusaway-android/issues/1792.
    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

    /** A strip that overflows its host: the recent-past pills must scroll off to justify. */
    @Test
    fun justifiesPastRecentPastEtas() {
        assertJustifiesLeadingEdge(
            hostWidth = 160.dp,
            trips = listOf(
                previewArrival("8", "Rainier Beach", etaMinutes = -9),
                previewArrival("8", "Rainier Beach", etaMinutes = -2),
                previewArrival("40", "Northgate", etaMinutes = 3),
                previewArrival("40", "Northgate", etaMinutes = 11),
                previewArrival("40", "Northgate", etaMinutes = 19),
                previewArrival("40", "Northgate", etaMinutes = 27),
            ),
        )
    }

    /**
     * The pills-fit case: one recent-past + one upcoming pill in a host wide enough that both fit
     * without overflowing. horizontalScroll's maxValue is then 0, so unless SlideBox floors the
     * content width (minReachablePx + one viewport) scrollTo(pinnedOffsetPx) clamps back to 0 and the
     * recent-past pill stays on screen. Asserts the justify still happens with no natural overflow.
     */
    @Test
    fun justifiesEvenWhenPillsFit() {
        assertJustifiesLeadingEdge(
            hostWidth = 320.dp,
            trips = listOf(
                previewArrival("8", "Rainier Beach", etaMinutes = -2),
                previewArrival("8", "Rainier Beach", etaMinutes = 5),
            ),
        )
    }

    /**
     * Renders the real [EtaStrip] in a [hostWidth]-wide host, justified to its first upcoming ETA,
     * and asserts the strip actually scrolls the recent-past pills off the leading edge.
     *
     * The measure -> onGloballyPositioned -> pinnedOffsetPx write -> LaunchedEffect -> scrollTo chain
     * spans a recomposition, so a single waitForIdle() can return before scrollTo has run. Poll
     * instead; this throws (failing the test) if the strip never scrolls within the timeout — a
     * broken justify silently leaves scrollState at 0.
     */
    private fun assertJustifiesLeadingEdge(hostWidth: Dp, trips: List<ArrivalInfo>) {
        val firstUpcomingIndex = trips.indexOfFirst { it.eta >= 0 }
        lateinit var scrollState: ScrollState

        composeRule.setContent {
            scrollState = rememberScrollState()
            Box(Modifier.width(hostWidth)) {
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

        composeRule.waitUntil(timeoutMillis = 5_000) { scrollState.value > 0 }
    }
}
