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
package org.onebusaway.android.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * On-device tests for the SlideBox's single-owner glide regimes: the anchor chase, the follow-the-end
 * reveal, and the edge-triggered hand-back between them. The follow-vs-anchor case is the regression
 * test for issue #1801 — under the old two-coroutine design (a CHASER and a FOLLOWER each driving the
 * same ScrollState), an anchor ≠ end situation livelocked in mutual animateScrollTo cancellation and
 * the scroll never converged, so the settle assertions below would time out.
 */
class SlideBoxTest {

    // See EtaStripJustifyTest for why the deprecated rule (Unconfined composition) is kept — under
    // the v2 rule this project's onGloballyPositioned/effect chains never run (#1792).
    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

    /** The anchor offset the content declares, in px — mutated mid-test to drive the regimes. */
    private val anchor = mutableIntStateOf(ANCHOR_PX)
    private var followEnd by mutableStateOf(false)
    private lateinit var state: SlideBoxState

    /** [boxCount] 60dp boxes in a 150dp viewport — ten leaves plenty of content past both targets. */
    private fun setSlideBoxContent(boxCount: Int = 10, onPullFired: () -> Unit = {}) {
        composeRule.setContent {
            state = rememberSlideBoxState()
            Box(Modifier.width(150.dp)) {
                SlideBox(
                    state = state,
                    anchorPx = { anchor.intValue },
                    followEnd = followEnd,
                    onPullFired = onPullFired,
                    onUserScroll = {},
                ) {
                    repeat(boxCount) { Box(Modifier.size(60.dp)) }
                }
            }
        }
    }

    @Test
    fun restsOnTheDeclaredAnchor() {
        setSlideBoxContent()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.scroll.value == ANCHOR_PX }
    }

    @Test
    fun followEndWinsOverTheAnchor_regression1801() {
        setSlideBoxContent()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.scroll.value == ANCHOR_PX }

        // The #1801 shape: the reveal regime engages while the anchor still points mid-content.
        composeRule.runOnIdle { followEnd = true }

        // The old two-owner design never converged here (mutual cancellation at CPU speed); the
        // single owner glides to the end and stays.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.scroll.maxValue > ANCHOR_PX && state.scroll.value == state.scroll.maxValue
        }
    }

    @Test
    fun handBackKeepsTheRevealUntilTheAnchorNextMoves() {
        setSlideBoxContent()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.scroll.value == ANCHOR_PX }
        composeRule.runOnIdle { followEnd = true }
        composeRule.waitUntil(timeoutMillis = 5_000) { state.scroll.value == state.scroll.maxValue }

        // Hand the scroll back: the box must NOT chase the (stale) anchor — the reveal's result
        // stays on screen. waitForIdle runs any wrongly-started glide to completion, so the assert
        // catches a yank-back rather than racing it.
        composeRule.runOnIdle { followEnd = false }
        composeRule.waitForIdle()
        assertEquals(state.scroll.maxValue, state.scroll.value)

        // Only the anchor's next MOVE re-engages the chase.
        composeRule.runOnIdle { anchor.intValue = ANCHOR_PX + 60 }
        composeRule.waitUntil(timeoutMillis = 5_000) { state.scroll.value == ANCHOR_PX + 60 }
    }

    @Test
    fun pullingPastTheEndFiresOnRelease() {
        // Content barely wider than the 150dp viewport (3 × 60dp), so one full-width swipe reaches
        // the end (~30dp of scroll) and then builds ~55dp of post-resistance pull — past the 48dp
        // arm threshold — before the release.
        anchor.intValue = 0
        var fired = 0
        setSlideBoxContent(boxCount = 3, onPullFired = { fired++ })
        composeRule.waitUntil(timeoutMillis = 5_000) { state.scroll.maxValue > 0 }

        composeRule.onRoot().performTouchInput { swipeLeft() }

        composeRule.waitUntil(timeoutMillis = 5_000) { fired == 1 }
    }

    private companion object {
        /** Mid-content in px: past the first box on any density, far short of maxValue. */
        const val ANCHOR_PX = 100
    }
}
