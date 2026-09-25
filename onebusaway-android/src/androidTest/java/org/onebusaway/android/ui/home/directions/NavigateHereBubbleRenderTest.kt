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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.ui.compose.Channel
import org.onebusaway.android.ui.compose.assertDominant
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * On-device checks on what a map long press now offers (#2243): one option, drawn over the pin the press
 * drops, and leaving the map beneath it free. Render-only claims — the placement arithmetic itself is
 * unit-tested in `NavigateHereBubbleTest`; what a render adds is that the bubble really lands where that
 * arithmetic puts it, and that nothing here eats a touch the map should have had.
 */
class NavigateHereBubbleRenderTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val label = context.getString(R.string.map_navigate_here)

    /** One option, and pressing it takes the offer. */
    @Test
    fun theOfferIsASingleActionThatNavigatesToThePressedPoint() {
        var navigated = 0
        renderOffer(onNavigate = { navigated++ })

        composeRule.onNodeWithText(label).performClick()

        assertEquals(1, navigated)
    }

    /**
     * The point of the redesign: the bubble hangs off the pin rather than being centred on the screen.
     * Asserted against the anchor it was given — the bubble sits above it, and horizontally over it —
     * which is what a card in the middle of the screen could never satisfy.
     */
    @Test
    fun theBubbleIsDrawnAtThePressRatherThanInTheMiddleOfTheScreen() {
        val anchor = ScreenOffset(x = 200f, y = 500f)
        renderOffer(anchor = anchor)

        val bubble = composeRule
            .onNodeWithTag(NavigateHereBubbleTestTags.BUBBLE)
            .getUnclippedBoundsInRoot()
        val anchorX = with(composeRule.density) { anchor.x.toDp() }
        val anchorY = with(composeRule.density) { anchor.y.toDp() }

        assertTrue(
            "the bubble should sit above the pressed point, but its bottom edge is ${bubble.bottom} " +
                "against a press at $anchorY",
            bubble.bottom <= anchorY
        )
        assertTrue(
            "the bubble should span the pressed point, but it runs ${bubble.left}..${bubble.right} " +
                "against a press at $anchorX",
            bubble.left <= anchorX && anchorX <= bubble.right
        )
    }

    /** The mark is the destination dot the trip form will show for that point, not a generic glyph. */
    @Test
    fun theOfferIsMarkedWithTheRailsDestinationDot() {
        renderOffer()

        val pixels = composeRule
            .onNodeWithTag(NavigateHereBubbleTestTags.DOT, useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        assertDominant(pixels[pixels.width / 2, pixels.height / 2], Channel.RED, label)
    }

    /**
     * The offer is not modal: everything it doesn't cover belongs to the map, which is what lets the
     * rider pan and zoom around the pinned place. So a tap beside the bubble reaches the map — where it
     * is the map, not this overlay, that answers the offer by putting its pin away.
     */
    @Test
    fun aTapBesideTheBubbleReachesTheMapBeneath() {
        var throughToTheMap = 0
        renderOffer(onMapTap = { throughToTheMap++ })

        composeRule.onRoot().performTouchInput { click(percentOffset(0.5f, 0.1f)) }

        assertEquals("a tap beside the offer belongs to the map", 1, throughToTheMap)
    }

    /** Panned off the map, the offer has nothing to stand beside, so it draws nothing at all. */
    @Test
    fun anOfferWhosePinIsOffTheMapDrawsNothing() {
        renderOffer(anchor = ScreenOffset(x = -400f, y = -400f))

        composeRule.onNodeWithTag(NavigateHereBubbleTestTags.BUBBLE).assertIsNotDisplayed()
    }

    private fun renderOffer(
        anchor: ScreenOffset = ScreenOffset(x = 200f, y = 500f),
        onNavigate: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onMapTap: () -> Unit = {}
    ) {
        composeRule.setContent {
            ObaTheme {
                // A stand-in for the map under the offer, so a dismissing tap can be shown to stop at
                // the offer rather than carrying on into what it was drawn over.
                Box(
                    Modifier
                        .fillMaxSize()
                        // No ripple or semantics of its own, so it can't be mistaken for the offer.
                        .clickable(interactionSource = null, indication = null, onClick = onMapTap)
                ) {
                    NavigateHereOffer(
                        anchor = { anchor },
                        onNavigate = onNavigate,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}
