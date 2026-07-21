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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * On-device regression test for the strip justifying to its first non-negative ETA (recent-past
 * pills overflowing off the left). The strip derives this first-upcoming justify internally from
 * `trips`, so this also guards against the #1973 regression where forgetting to justify let the pin
 * start at 0 and glide-animate the recent-past pills off on entry. Renders the real composable — not
 * a fake — and asserts the scroll actually happened, since a broken justify silently leaves
 * scrollState at 0. Covers both a strip that overflows its host and one whose pills fit (where the
 * justify only works because SlideBox floors the content width — see EtaStrip's `minReachablePx`).
 */
class EtaStripJustifyTest {

    // Unconfined composition — see createUnconfinedComposeRule for why (this composable's
    // measure -> onGloballyPositioned -> LaunchedEffect -> scrollTo chain, and issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

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
                previewArrival("40", "Northgate", etaMinutes = 27)
            )
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
                previewArrival("8", "Rainier Beach", etaMinutes = 5)
            )
        )
    }

    /**
     * Renders the real [EtaStrip] in a [hostWidth]-wide host and asserts the strip actually scrolls
     * the recent-past pills off the leading edge, justifying to its first upcoming ETA.
     *
     * The measure -> onGloballyPositioned -> pinnedOffsetPx write -> LaunchedEffect -> scrollTo chain
     * spans a recomposition, so a single waitForIdle() can return before scrollTo has run. Poll
     * instead; this throws (failing the test) if the strip never scrolls within the timeout — a
     * broken justify silently leaves scrollState at 0.
     */
    private fun assertJustifiesLeadingEdge(hostWidth: Dp, trips: List<ArrivalInfo>) {
        lateinit var scrollState: ScrollState

        composeRule.setContent {
            scrollState = rememberScrollState()
            Box(Modifier.width(hostWidth)) {
                EtaStrip(
                    trips = trips,
                    actionsFor = { null },
                    callbacks = previewRowCallbacks(),
                    scrollState = scrollState
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { scrollState.value > 0 }
    }
}
