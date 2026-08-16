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
package org.onebusaway.android.ui.home.map

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.tripplan.pinned.PinnedLabel
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripSummary
import org.onebusaway.android.ui.tripresults.LegBadge
import org.onebusaway.android.ui.tripresults.ModeSymbol
import org.onebusaway.android.ui.tripresults.TransitMode

/**
 * The parked trip's button (#2229). The pin it replaced could be off screen, so what matters here is
 * that the trip says where it goes and that one tap on it means "take me back" — the two things the
 * marker's info window was the only place to say.
 *
 * Both are now carried by semantics rather than by drawn text: the button states the destination as its
 * content description and "resume" as its click label, so a rider using a screen reader gets what the
 * dropped "Pinned trip to X" heading used to spell out. That makes these the only check on either.
 */
class PinnedTripFabTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val name = resources.getString(R.string.trip_plan_pinned_trip_title, "Ballard")
    private val resumeLabel = resources.getString(R.string.trip_plan_pinned_resume)
    private val unpinLabel = resources.getString(R.string.trip_plan_unpin)
    private val unpinConfirm = resources.getString(R.string.trip_plan_pinned_unpin_confirm)
    private val cancel = resources.getString(R.string.cancel)

    @Test
    fun theButtonNamesTheParkedTripAndResumesItOnTap() {
        var resumed = 0
        show(onResume = { resumed++ })

        composeRule.onNodeWithContentDescription(name).assertIsDisplayed().performClick()

        assertEquals(1, resumed)
    }

    /**
     * "Resume this trip" is the button's *action*, announced as the click's label rather than drawn as a
     * line of its own. That label rides on a semantics merge (the FAB contributes the action, its content
     * the label), which is exactly the kind of thing that silently stops working — hence a test.
     */
    @Test
    fun theTapAnnouncesWhatItDoes() {
        show()

        composeRule
            .onNodeWithContentDescription(name)
            .assert(
                SemanticsMatcher("click action is labelled \"$resumeLabel\"") { node ->
                    node.config.getOrNull(SemanticsActions.OnClick)?.label == resumeLabel
                }
            )
    }

    /**
     * The ✕ is a small glyph beside a large button, and the pin is the only thing holding that plan —
     * nothing else remembers it. So it asks first, and a rider who says no keeps the trip.
     */
    @Test
    fun theUnpinButtonAsksBeforeItLetsTheTripGo() {
        var unpinned = 0
        show(onUnpin = { unpinned++ })

        composeRule.onNodeWithContentDescription(unpinLabel).performClick()
        composeRule.onNodeWithText(cancel).performClick()

        assertEquals("cancelling the confirmation must keep the trip", 0, unpinned)

        composeRule.onNodeWithContentDescription(unpinLabel).performClick()
        composeRule.onNodeWithText(unpinConfirm).performClick()

        assertEquals(1, unpinned)
    }

    /** The ✕ is its own target: a tap on it must not read as a tap on the button it sits in. */
    @Test
    fun theUnpinButtonDoesNotResumeTheTrip() {
        var resumed = 0
        show(onResume = { resumed++ })

        composeRule.onNodeWithContentDescription(unpinLabel).performClick()

        assertEquals(0, resumed)
    }

    private fun show(onResume: () -> Unit = {}, onUnpin: () -> Unit = {}) {
        composeRule.setContent {
            ObaTheme {
                PinnedTripFab(
                    state = PinnedTripSummary(
                        destination = PinnedLabel.Text("Ballard"),
                        symbols = listOf(
                            ModeSymbol.Transit(
                                LegBadge(
                                    routes = listOf(RouteBadge("44", routeColor = null)),
                                    mode = TransitMode.BUS,
                                    join = RouteBadgeJoin.ANY_OF
                                )
                            )
                        )
                    ),
                    onResume = onResume,
                    onUnpin = onUnpin
                )
            }
        }
        composeRule.waitForIdle()
    }
}
