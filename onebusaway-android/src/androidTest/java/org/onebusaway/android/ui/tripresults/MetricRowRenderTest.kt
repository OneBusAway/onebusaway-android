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
package org.onebusaway.android.ui.tripresults

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

/**
 * Pixel-level checks on an option card's metric rows (#2076): the leading glyph and the value beside it
 * have to read as one line of type — the same top edge, the same bottom edge, sitting on the same
 * baseline — rather than a glyph hung around a smaller number, which is what a plain centred icon box
 * gives you (the hourglass used to overhang the digits by ~1.5dp at the bottom).
 *
 * Only pixels can answer this: the assertion is about where each element's *ink* landed, and neither
 * the icon's box nor the text's line box is its ink — both reserve transparent margin (the vector's
 * padding; the font's ascent/descent). So each node is captured and scanned for the first and last row
 * carrying ink, and those absolute positions are compared.
 */
class MetricRowRenderTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    /**
     * The duration row — the issue's own example. Its hourglass inks exactly the band the "32min"
     * beside it does.
     */
    @Test
    fun durationGlyphAndValueShareTheirTopAndBottomEdges() {
        renderCard()

        val glyph = inkBand(composeRule.onNodeWithTag(MetricGlyph.DURATION.testTag, useUnmergedTree = true))
        val value = inkBand(composeRule.onNodeWithText(durationText(), useUnmergedTree = true))

        assertClose("top edge", glyph.first, value.first)
        assertClose("bottom edge", glyph.second, value.second)
    }

    /**
     * The walk row is levelled the same way — which is the half of this that a shared icon size can't
     * deliver: the walker inks a larger fraction of its viewport than the hourglass does, so at one
     * common box the two rows' glyphs come out different heights, one of them wrong against its value.
     */
    @Test
    fun walkGlyphAndValueShareTheirTopAndBottomEdges() {
        renderCard()

        val glyph = inkBand(composeRule.onNodeWithTag(MetricGlyph.WALK.testTag, useUnmergedTree = true))
        val value = inkBand(composeRule.onNodeWithText(walkDistanceText(), useUnmergedTree = true))

        assertClose("top edge", glyph.first, value.first)
        assertClose("bottom edge", glyph.second, value.second)
    }

    /** One option card, alone on screen so each glyph and value matches a single node. */
    private fun renderCard() {
        composeRule.setContent {
            // Pinned density, so what these assertions see doesn't depend on the screen they run on.
            CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                ObaTheme {
                    TripResultsHeader(
                        state = TripResultsUiState.Success(
                            options = listOf(
                                ItineraryOption(
                                    symbols = listOf(
                                        ModeSymbol.Street(StreetMode.WALK),
                                        ModeSymbol.Transit(
                                            LegBadge(
                                                listOf(RouteBadge("8", 0xFF1B6EF3.toInt())),
                                                TransitMode.BUS,
                                                RouteBadgeJoin.ANY_OF
                                            )
                                        )
                                    ),
                                    durationMinutes = DURATION_MINUTES,
                                    startTime = ServerTime(0L),
                                    endTime = ServerTime(DURATION_MINUTES * 60_000L),
                                    walkDistanceMeters = WALK_METERS
                                )
                            ),
                            selectedIndex = 0,
                            directions = emptyList()
                        ),
                        onSelectOption = {}
                    )
                }
            }
        }
        // Let the composition attach and draw before any pixel is read: captureToImage needs a live
        // hierarchy, and these tests share an instrumentation process with the other Compose suites.
        composeRule.waitForIdle()
    }

    /**
     * The values exactly as the card renders them, built from the same formatters rather than pinned as
     * literals — the walk distance in particular depends on the device's units preference and locale.
     */
    private fun durationText(): String = DisplayFormat.formatEtaParts(targetContext, DURATION_MINUTES).joinToString("") { it.text }

    private fun walkDistanceText(): String = ConversionUtils.getFormattedDistanceParts(WALK_METERS, targetContext).joinToString("") { it.text }

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The first and last rows of this node that carry ink, in root pixels. "Ink" is any pixel far enough
     * from the node's dominant color — the card surface behind it — to be part of a glyph or a letter;
     * the threshold sits well below a fully-drawn pixel so an antialiased edge still counts, and counts
     * the same way for the icon and for the text.
     */
    private fun inkBand(node: SemanticsNodeInteraction): Pair<Int, Int> {
        val pixels = node.captureToImage().toPixelMap()
        val top = node.getUnclippedBoundsInRoot().top.value * DENSITY
        val background = (0 until pixels.height)
            .flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
            .groupingBy { it }
            .eachCount()
            .maxBy { it.value }
            .key
        val inked = (0 until pixels.height).filter { y ->
            (0 until pixels.width).any { x ->
                abs(pixels[x, y].luminance() - background.luminance()) > INK_THRESHOLD
            }
        }
        assertTrue("no ink found in the captured node", inked.isNotEmpty())
        return (top + inked.first()).toInt() to (top + inked.last()).toInt()
    }

    private fun assertClose(what: String, glyph: Int, value: Int) = assertTrue(
        "$what: glyph at $glyph, value at $value — more than ${TOLERANCE_PX}px apart",
        abs(glyph - value) <= TOLERANCE_PX
    )

    private companion object {
        const val DURATION_MINUTES = 32L
        const val WALK_METERS = 800.0

        /** The density every case renders at, pinned so the pixel tolerance means a fixed fraction of a dp. */
        const val DENSITY = 2f

        /**
         * How far apart the two edges may land: a pixel of rounding on each side (the glyph's alignment
         * line is rounded to whole pixels) plus the round digits' overshoot above the cap line. The
         * regression this guards against was three times this at the bottom edge.
         */
        const val TOLERANCE_PX = 2

        /** Far enough from the surface behind it to be ink, low enough that an antialiased edge counts. */
        const val INK_THRESHOLD = 0.15f
    }
}
