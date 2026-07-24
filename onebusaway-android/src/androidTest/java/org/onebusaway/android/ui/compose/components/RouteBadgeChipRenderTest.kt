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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * Pixel-level checks on the joined route badge (#2010) — the two things that make it read as one chip
 * of several routes rather than as separate badges:
 *  - its route colors meet **directly**, with none of the surface behind showing through as a seam;
 *  - they meet along a **slash-like diagonal**, not a vertical edge.
 *
 * Rendering assertions, so they live on-device: the badge is drawn over a garish background that must
 * not appear anywhere inside it, and the color boundary is located on a high and a low scan line to
 * confirm which way it leans. Reading a run of one color (rather than the first pixel that differs)
 * keeps the glyphs drawn on top of that color from being mistaken for the boundary.
 */
class RouteBadgeChipRenderTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    /** Nothing else in the badge is this color, so any pixel of it inside the chip is a gap. */
    private val backdrop = Color.Magenta

    private val link1 = RouteBadge("1 Line", 0xFF00A651.toInt())
    private val link2 = RouteBadge("2 Line", 0xFF0075C4.toInt())

    private fun renderJoinedBadge(routes: List<RouteBadge> = listOf(link1, link2)) {
        composeRule.setContent {
            ObaTheme {
                Box(Modifier.background(backdrop).padding(8.dp)) {
                    RouteBadgeChip(routes, Modifier.testTag(BADGE))
                }
            }
        }
        // Let the composition attach and draw before any pixel is read: captureToImage needs a live
        // hierarchy, and these tests share an instrumentation process with the other Compose suites.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BADGE).assertExists()
    }

    private fun badgePixels(): PixelMap = composeRule.onNodeWithTag(BADGE).captureToImage().toPixelMap()

    /**
     * The two route colors abut with no sliver of the surface behind them. Scanned across the badge's
     * middle band only: the chip's corners are rounded, so its very top and bottom rows legitimately
     * show the backdrop outside that rounding.
     */
    @Test
    fun joinedBadgeHasNoGapBetweenItsRouteColors() {
        renderJoinedBadge()

        val pixels = badgePixels()
        val leaked = interiorRows(pixels).flatMap { y ->
            (1 until pixels.width - 1).filter { x -> pixels[x, y] == backdrop }.map { x -> x to y }
        }

        assertEquals("backdrop visible inside the badge at $leaked", emptyList<Pair<Int, Int>>(), leaked)
    }

    /**
     * The boundary between the colors leans like a "/": it sits further right near the top of the badge
     * than near the bottom.
     */
    @Test
    fun joinedBadgeColorsMeetAlongASlashDiagonal() {
        renderJoinedBadge()

        val pixels = badgePixels()
        val rows = interiorRows(pixels)
        val nearTop = boundaryX(pixels, rows.first())
        val nearBottom = boundaryX(pixels, rows.last())

        assertTrue(
            "expected the boundary to lean right going up, but it was at x=$nearTop near the top " +
                "and x=$nearBottom near the bottom",
            nearTop > nearBottom + MIN_LEAN_PX
        )
    }

    /** A single-route badge is still one flat color — the diagonal is only for a joined one. */
    @Test
    fun singleRouteBadgeIsOneSolidColor() {
        renderJoinedBadge(routes = listOf(link1))

        val pixels = badgePixels()
        val leftEdge = pixels[1, pixels.height / 2]
        val rightEdge = pixels[pixels.width - 2, pixels.height / 2]

        assertEquals(leftEdge, rightEdge)
    }

    /** The rows to scan: the badge's middle band, clear of its rounded corners. */
    private fun interiorRows(pixels: PixelMap): List<Int> = (pixels.height / 4 until pixels.height * 3 / 4).toList()

    /**
     * Where the first route's color gives way to the second on this row: the last pixel still matching
     * the row's leftmost color. Taking the *last* match rather than the first mismatch steps over the
     * route name drawn on top of that color.
     */
    private fun boundaryX(pixels: PixelMap, y: Int): Int {
        val first = pixels[1, y]
        return (1 until pixels.width - 1).last { x -> pixels[x, y] == first }
    }

    private companion object {
        const val BADGE = "joined-route-badge"

        /** The lean has to be visible, not a rounding artifact — a couple of pixels at minimum. */
        const val MIN_LEAN_PX = 2
    }
}
