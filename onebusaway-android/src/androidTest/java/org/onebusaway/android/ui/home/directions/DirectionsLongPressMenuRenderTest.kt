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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.Channel
import org.onebusaway.android.ui.compose.assertDominant
import org.onebusaway.android.ui.compose.components.LONG_PRESS_MENU_EDGE_MARGIN
import org.onebusaway.android.ui.compose.components.LONG_PRESS_MENU_MAX_WIDTH
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.tripplan.TripEndpointSlot

/**
 * On-device checks on the map's long-press menu (#2112). The redesign made two claims that only a
 * render can hold: the menu is the same centered modal every other long-press menu in the app opens
 * (it was a bottom sheet, which on the map covers the very point that was pressed), and its rows are
 * marked with the trip-plan rail's own green/red endpoint dots rather than generic pin icons.
 */
class DirectionsLongPressMenuRenderTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun bothEndsOfTheTripAreOfferedAndReportTheirSlot() {
        var chosen: TripEndpointSlot? = null
        renderMenu(onChooseSlot = { chosen = it })

        composeRule.onNodeWithText(FROM_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(TO_LABEL).assertIsDisplayed()

        composeRule.onNodeWithText(FROM_LABEL).performClick()
        assertEquals(TripEndpointSlot.FROM, chosen)

        composeRule.onNodeWithText(TO_LABEL).performClick()
        assertEquals(TripEndpointSlot.TO, chosen)
    }

    /**
     * A compact centered card, not a sheet. Width is what separates the two: the shared
     * `CenteredLongPressMenu` caps itself at 320dp and keeps a margin from both screen edges, while a
     * bottom sheet spans the screen. Dialog semantics would not do — a `ModalBottomSheet` reports
     * `isDialog()` too (verified on device), so asserting that would pass for the layout this issue
     * replaced.
     *
     * The bound is the cap *or* the inset screen, whichever is smaller, because the two swap places:
     * the CI emulator's screen is 320dp — the cap itself — so on that device only the margin tells a
     * card from a sheet, and asserting against the cap alone would pass for a full-width sheet.
     */
    @Test
    fun theMenuIsACompactCardRatherThanAFullWidthSheet() {
        renderMenu()

        val rowWidth = composeRule.onNodeWithText(FROM_LABEL).getUnclippedBoundsInRoot().width
        val screenWidth = with(composeRule.density) {
            InstrumentationRegistry.getInstrumentation()
                .targetContext.resources.displayMetrics.widthPixels.toDp()
        }
        val widest = minOf(
            LONG_PRESS_MENU_MAX_WIDTH,
            screenWidth - LONG_PRESS_MENU_EDGE_MARGIN * 2f
        )
        assertTrue(
            "the menu should be a card no wider than $widest on this $screenWidth screen, not a " +
                "sheet, but its rows measured $rowWidth",
            rowWidth <= widest
        )
    }

    /** The rows carry the rail's endpoint dots — green for the origin, red for the destination. */
    @Test
    fun theRowsAreMarkedWithTheRailsEndpointDots() {
        renderMenu()

        assertDominant(dot(DirectionsLongPressMenuTestTags.FROM_DOT), Channel.GREEN, FROM_LABEL)
        assertDominant(dot(DirectionsLongPressMenuTestTags.TO_DOT), Channel.RED, TO_LABEL)
    }

    /**
     * The centre of a row's endpoint dot. Captured from the dot's own box, so nothing here depends
     * on Material's internal menu-item padding; unmerged because the clickable row merges its
     * children's semantics.
     */
    private fun dot(tag: String): Color {
        val pixels = composeRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun renderMenu(onChooseSlot: (TripEndpointSlot) -> Unit = {}) {
        composeRule.setContent {
            ObaTheme {
                DirectionsLongPressMenu(onChooseSlot = onChooseSlot, onDismiss = {})
            }
        }
    }

    private companion object {
        const val FROM_LABEL = "Directions from here"
        const val TO_LABEL = "Directions to here"
    }
}
