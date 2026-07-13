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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * On-device tests for the SlideBox's single-owner anchor-chasing glide. This is the regression test
 * for issue #1801 — under the old two-coroutine design (a CHASER and a FOLLOWER each driving the same
 * ScrollState), a moving anchor could livelock in mutual animateScrollTo cancellation and the scroll
 * never converged, so the settle assertions below would time out.
 */
class SlideBoxTest {

    // See EtaStripJustifyTest for why the deprecated rule (Unconfined composition) is kept — under
    // the v2 rule this project's onGloballyPositioned/effect chains never run (#1792).
    @Suppress("DEPRECATION")
    @get:Rule
    val composeRule = createComposeRule()

    /** The anchor offset the content declares, in px — mutated mid-test to drive the glide. */
    private val anchor = mutableIntStateOf(ANCHOR_PX)
    private lateinit var scroll: ScrollState

    /** [boxCount] 60dp boxes in a 150dp viewport — ten leaves plenty of content past the target. */
    private fun setSlideBoxContent(boxCount: Int = 10) {
        composeRule.setContent {
            scroll = rememberScrollState()
            Box(Modifier.width(150.dp)) {
                SlideBox(scroll = scroll, anchorPx = { anchor.intValue }) {
                    repeat(boxCount) { Box(Modifier.size(60.dp)) }
                }
            }
        }
    }

    @Test
    fun restsOnTheDeclaredAnchor() {
        setSlideBoxContent()
        composeRule.waitUntil(timeoutMillis = 5_000) { scroll.value == ANCHOR_PX }
    }

    @Test
    fun glidesToALaterAnchor() {
        setSlideBoxContent()
        composeRule.waitUntil(timeoutMillis = 5_000) { scroll.value == ANCHOR_PX }

        // The #1801 shape: the anchor moves again while the box is already at rest.
        composeRule.runOnIdle { anchor.intValue = ANCHOR_PX + 60 }

        composeRule.waitUntil(timeoutMillis = 5_000) { scroll.value == ANCHOR_PX + 60 }
    }

    private companion object {
        /** Mid-content in px: past the first box on any density, far short of maxValue. */
        const val ANCHOR_PX = 100
    }
}
