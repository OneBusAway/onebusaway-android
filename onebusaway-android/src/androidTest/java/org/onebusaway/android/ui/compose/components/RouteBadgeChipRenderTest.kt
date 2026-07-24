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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * Pixel-level checks on the joined route badge (#2010) — what makes it read as one bounded chip of
 * several routes rather than as separate badges, or as one long name:
 *  - its route colors meet **directly**, with none of the surface behind showing through as a gap;
 *  - they meet along a **slash-like diagonal**, not a vertical edge;
 *  - that meeting line is much darker than the routes it separates, and the badge's outline is black.
 *
 * Rendering assertions, so they live on-device: the badge is drawn over a garish background that must
 * not appear anywhere inside it, and the color boundary is located on a high and a low scan line to
 * confirm which way it leans. Scans start inside the outline ([EDGE_INSET]) and read a *run* of one
 * color rather than the first pixel that differs, so neither the outline nor the glyphs drawn on a
 * band are mistaken for the boundary.
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
            // Pinned density, so what these assertions see doesn't depend on the screen they run on.
            // Deliberately a low one: it is the hostile case for a hairline — the badge is only a few
            // dozen pixels across and the diagonal line between its routes is barely two pixels wide,
            // fully antialiased. (An earlier version of this test read single pixels and passed on a
            // dense phone while failing on CI's emulator.)
            CompositionLocalProvider(LocalDensity provides Density(LOW_DENSITY)) {
                ObaTheme {
                    Box(Modifier.background(backdrop).padding(8.dp)) {
                        RouteBadgeChip(routes, Modifier.testTag(BADGE))
                    }
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
            (EDGE_INSET until pixels.width - EDGE_INSET).filter { x -> pixels[x, y] == backdrop }.map { x -> x to y }
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

    /**
     * A single-route badge is one flat color inside its outline — no diagonal, no second color. Compares
     * the *dominant* color of the leftmost and rightmost interior columns rather than one pixel of each:
     * a route name can reach into either end of a narrow badge, but it never fills a whole column.
     */
    @Test
    fun singleRouteBadgeIsOneSolidColor() {
        renderJoinedBadge(routes = listOf(link1))

        val pixels = badgePixels()

        assertEquals(columnColor(pixels, EDGE_INSET), columnColor(pixels, pixels.width - 1 - EDGE_INSET))
    }

    /** The badge is bounded by a black outline, so it reads as one object against any background. */
    @Test
    fun badgeIsOutlinedInBlack() {
        renderJoinedBadge()

        val pixels = badgePixels()
        val middleRow = pixels.height / 2
        val middleColumn = pixels.width / 2

        assertEquals("left edge", Color.Black, pixels[0, middleRow])
        assertEquals("right edge", Color.Black, pixels[pixels.width - 1, middleRow])
        assertEquals("top edge", Color.Black, pixels[middleColumn, 0])
        assertEquals("bottom edge", Color.Black, pixels[middleColumn, pixels.height - 1])
    }

    /**
     * Where the two routes meet there is a black line — the thing that keeps routes sharing a color (or
     * having none) from reading as one long name. Asserted as "much darker than either route color" at
     * the meeting point rather than "exactly black" anywhere: the line is a diagonal hairline, so at a
     * low screen density every pixel of it is antialiased with the bands it separates and none comes out
     * pure black. The line is drawn regardless of the routes' colors, so two distinct colors — which
     * make the meeting point findable — exercise the same drawing the same-color case relies on.
     */
    @Test
    fun routesAreSeparatedByABlackLine() {
        renderJoinedBadge()

        val pixels = badgePixels()
        val rows = interiorRows(pixels)
        val bands = listOf(
            columnColor(pixels, EDGE_INSET),
            columnColor(pixels, pixels.width - 1 - EDGE_INSET)
        )
        val row = rows[rows.size / 2]
        val meeting = boundaryX(pixels, row)
        val darkest = ((meeting - 1)..(meeting + 2))
            .filter { it in 0 until pixels.width }
            .minOf { pixels[it, row].luminance() }

        assertTrue(
            "expected a dark line where the routes meet (x=$meeting): darkest luminance there was " +
                "$darkest, against route colors ${bands.map { it.luminance() }}",
            darkest < bands.minOf { it.luminance() } / 2f
        )
    }

    /** The most common color down one interior column — the band behind whatever glyphs cross it. */
    private fun columnColor(pixels: PixelMap, x: Int): Color = interiorRows(pixels)
        .groupingBy { y -> pixels[x, y] }
        .eachCount()
        .maxBy { it.value }
        .key

    /** The rows to scan: the badge's middle band, clear of its rounded corners. */
    private fun interiorRows(pixels: PixelMap): List<Int> = (pixels.height / 4 until pixels.height * 3 / 4).toList()

    /**
     * Where the first route's color gives way to the second on this row: the last pixel still matching
     * the row's leftmost color. Taking the *last* match rather than the first mismatch steps over the
     * route name drawn on top of that color.
     */
    private fun boundaryX(pixels: PixelMap, y: Int): Int {
        val scan = EDGE_INSET until pixels.width - EDGE_INSET
        val first = pixels[scan.first, y]
        return scan.last { x -> pixels[x, y] == first }
    }

    private companion object {
        const val BADGE = "joined-route-badge"

        /** The lean has to be visible, not a rounding artifact — a couple of pixels at minimum. */
        const val MIN_LEAN_PX = 2

        /** Scans start this far inside the badge, clear of its outline (1dp, so a few px at any density). */
        const val EDGE_INSET = 6

        /** The density every case renders at — low, so hairlines and glyphs get the least room. */
        const val LOW_DENSITY = 2f
    }
}
