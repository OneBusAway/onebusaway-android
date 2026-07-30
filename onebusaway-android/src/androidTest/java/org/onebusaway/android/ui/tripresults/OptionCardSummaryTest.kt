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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * Layout coverage for an option card's summary line — the row of mode glyphs and route roundels that
 * says what the trip is (#2081):
 *  - a trip with too many legs to fit the card's width **wraps** rather than drawing one long card that
 *    pushes every other option off the picker;
 *  - the wrap is a ceiling, not a floor: a short trip still gets a card sized to itself (the summary's
 *    tinted band fills the card, and a `fillMaxWidth` that measured against the picker's unbounded
 *    horizontal scroll would otherwise blow every card out to the same width);
 *  - and a badge that is *itself* wider than the wrap — several interchangeable routes on one ride —
 *    keeps every route it names, instead of being measured into the space that's left and losing its
 *    last segments to a zero width.
 *
 * A layout test, so it runs on-device against real text measurement, with the density pinned so the
 * numbers don't depend on the screen it lands on.
 */
class OptionCardSummaryTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun aSummaryTooWideForTheCardWrapsOntoAnotherLine() {
        show(option(walk(), bus("R1"), walk(), bus("R2"), walk(), bus("R3"), walk()))

        val first = symbol("R1")
        val last = symbol("R3")

        assertTrue(
            "expected the summary to wrap: R1 at $first, R3 at $last",
            last.top >= first.bottom
        )
        // And to wrap where the width genuinely runs out, not a symbol early. The card takes its width
        // from what the summary reports it needs; if measuring it again at that width broke the lines
        // anywhere else, the card would carry an extra line and a strip of dead space (see [SymbolFlow]
        // on stable packing).
        assertEquals("expected R2 to still share R1's line", first.top, symbol("R2").top)
    }

    @Test
    fun aShortSummaryKeepsItsCardNarrowerThanALongOne() {
        show(
            option(bus("Q1")),
            option(walk(), bus("R1"), walk(), bus("R2"), walk(), bus("R3"), walk())
        )

        val short = card("Q1")
        val long = card("R1")

        assertTrue(
            "expected the one-leg card to be narrower: ${short.width} vs ${long.width}",
            short.width < long.width
        )
    }

    /**
     * The names are all three digits, so any two segments of the badge that were measured the same come
     * out the same width — which makes an unequal one exactly the failure this guards: a segment that
     * was handed what width was left over rather than the width it asked for.
     */
    @Test
    fun anOversizedBadgeKeepsEveryRouteItNames() {
        val names = listOf("111", "112", "113", "114", "115", "116")
        show(option(walk(), interchangeable(names), walk()))

        val segments = names.map { symbol(it) }

        assertTrue(
            "expected every route to be drawn, but the segments were ${segments.map { it.width }}",
            segments.all { it.width > 0.dp }
        )
        val widest = segments.maxOf { it.width }
        val narrowest = segments.minOf { it.width }
        assertTrue(
            "expected equal-width segments, but they ran $narrowest–$widest",
            widest - narrowest <= SEGMENT_WIDTH_TOLERANCE
        )
    }

    // ---- fixtures -------------------------------------------------------------------------------

    private fun walk() = ModeSymbol.Street(StreetMode.WALK)

    private fun bus(name: String) = interchangeable(listOf(name))

    private fun interchangeable(names: List<String>) = ModeSymbol.Transit(
        LegBadge(names.map { RouteBadge(it, routeColor = null) }, TransitMode.BUS, RouteBadgeJoin.ANY_OF)
    )

    private fun option(vararg symbols: ModeSymbol) = ItineraryOption(
        symbols = symbols.toList(),
        durationMinutes = 30,
        startTime = ServerTime(0L),
        endTime = ServerTime(30 * 60_000L),
        walkDistanceMeters = 400.0
    )

    private fun show(vararg options: ItineraryOption) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                ObaTheme {
                    TripResultsHeader(
                        TripResultsUiState.Success(
                            options = options.toList(),
                            selectedIndex = 0,
                            directions = emptyList()
                        ),
                        onSelectOption = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** One route name inside a card — the unmerged tree, since the card itself merges its symbols. */
    private fun symbol(name: String) = composeRule.onNodeWithText(name, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** The whole card holding [name]: `clickable` merges the card's descendants into one node. */
    private fun card(name: String) = composeRule.onNodeWithText(name).getUnclippedBoundsInRoot()
}

/** Pinned so text measurement — and so where the summary wraps — is the same on every screen. */
private const val DENSITY = 2f

/** Room for the odd rounding of a segment's width to a whole pixel, and nothing more. */
private val SEGMENT_WIDTH_TOLERANCE = 1.dp
