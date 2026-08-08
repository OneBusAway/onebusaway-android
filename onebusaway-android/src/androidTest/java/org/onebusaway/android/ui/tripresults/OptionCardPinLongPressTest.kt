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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The pin gesture on the itinerary picker (#2053). The card's tap already means "show me this option",
 * so pinning had to go on a long press — and the risk of that change is the tap: a `combinedClickable`
 * that swallowed it, or opened the menu on it, would break choosing an option altogether while still
 * looking right.
 */
class OptionCardPinLongPressTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pinLabel = context.getString(R.string.trip_plan_pin)
    private val unpinLabel = context.getString(R.string.trip_plan_unpin)

    @Test
    fun longPressingACardOffersToPinThatOption() {
        var toggled: Int? = null
        show(onTogglePin = { toggled = it })

        longPressCard(index = 1)
        composeRule.onNodeWithText(pinLabel).performClick()

        assertEquals("the menu must act on the card that raised it", 1, toggled)
    }

    @Test
    fun thePinnedOptionOffersToUnpinInstead() {
        show(pinnedOptionIndex = 1)

        longPressCard(index = 1)

        composeRule.onNodeWithText(unpinLabel).assertExists()
        composeRule.onAllNodesWithText(pinLabel).assertCountEquals(0)
    }

    @Test
    fun anotherOptionStillOffersToPinWhileOneIsPinned() {
        // Only one trip can be pinned, so pressing a different card offers to move the pin, not to
        // remove it.
        show(pinnedOptionIndex = 1)

        longPressCard(index = 0)

        composeRule.onNodeWithText(pinLabel).assertExists()
    }

    @Test
    fun aPlainTapStillSelectsAndOpensNoMenu() {
        // The regression this whole test class exists for.
        var selected: Int? = null
        show(onSelectOption = { selected = it })

        composeRule.onNodeWithText("R2").performClick()

        assertEquals(1, selected)
        composeRule.onAllNodesWithText(pinLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText(unpinLabel).assertCountEquals(0)
    }

    @Test
    fun aHeaderGivenNoPinWiringCarriesNoLongPressAtAll() {
        // The defaults exist so the render-only harnesses keep working. "No pin" has to mean the cards
        // carry no long press — not a menu that opens and offers an item wired to nothing, which is what
        // a defaulted no-op callback would have produced. So the assertion is on the action, not the
        // menu: nothing announces a secondary action a card cannot perform.
        composeRule.setContent {
            ObaTheme {
                TripResultsHeader(state = successState(), onSelectOption = {})
            }
        }
        composeRule.waitForIdle()

        // On the *presence* of the action, not on its label: a long press registered under some other
        // label would still be a secondary action the card cannot perform, and a label-only check would
        // wave it through.
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(pinLabel).assertCountEquals(0)
    }

    @Test
    fun aWiredHeaderGivesEveryCardTheLongPress() {
        show()

        composeRule.onAllNodes(hasPinLongPressAction()).assertCountEquals(2)
    }

    /**
     * Long-press the card at [index] and wait for its menu. Driven through the card's OnLongClick
     * *semantics action* rather than an injected gesture: `combinedClickable` registers the same lambda
     * for both, so this exercises the real wiring without depending on the long-press timeout elapsing
     * against the test's virtual frame clock (see RouteArrivalRowLongPressTest for the CI flake that
     * taught us this).
     */
    private fun longPressCard(index: Int) {
        composeRule.onAllNodes(hasPinLongPressAction())[index]
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()
    }

    /** A card that announces the picker's secondary action — the pin menu's own long-press label. */
    private fun hasPinLongPressAction(): SemanticsMatcher {
        val menuLabel = context.getString(R.string.trip_plan_pin_menu_label)
        return SemanticsMatcher("has long-press action labeled \"$menuLabel\"") { node ->
            node.config.getOrNull(SemanticsActions.OnLongClick)?.label == menuLabel
        }
    }

    private fun show(
        pinnedOptionIndex: Int? = null,
        onSelectOption: (Int) -> Unit = {},
        onTogglePin: (Int) -> Unit = {}
    ) {
        composeRule.setContent {
            ObaTheme {
                TripResultsHeader(
                    state = successState(),
                    onSelectOption = onSelectOption,
                    pinnedOptionIndex = pinnedOptionIndex,
                    onTogglePin = onTogglePin
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun successState() = TripResultsUiState.Success(
        options = listOf(option("R1"), option("R2")),
        selectedIndex = 0,
        directions = emptyList()
    )

    private fun option(shortName: String) = ItineraryOption(
        symbols = listOf(
            ModeSymbol.Transit(
                LegBadge(
                    routes = listOf(RouteBadge(shortName, routeColor = null)),
                    mode = TransitMode.BUS,
                    join = RouteBadgeJoin.ANY_OF
                )
            )
        ),
        durationMinutes = 30,
        startTime = ServerTime(0L),
        endTime = ServerTime(30 * 60_000L)
    )
}
