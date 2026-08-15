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
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripCardState
import org.onebusaway.android.ui.tripresults.LegBadge
import org.onebusaway.android.ui.tripresults.ModeSymbol
import org.onebusaway.android.ui.tripresults.TransitMode

/**
 * The parked trip's button (#2229). The pin it replaced could be off screen, so what matters here is
 * that the trip says where it goes and that one tap on it means "take me back" — the two things the
 * marker's info window was the only place to say.
 */
class PinnedTripFabTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val title = resources.getString(R.string.trip_plan_pinned_trip_title, "Ballard")
    private val resumeLabel = resources.getString(R.string.trip_plan_pinned_resume)

    @Test
    fun theButtonNamesTheParkedTripAndResumesItOnTap() {
        var resumed = 0
        show(onResume = { resumed++ })

        composeRule.onNodeWithText(title).assertIsDisplayed().performClick()

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
            .onNodeWithText(title)
            .assert(
                SemanticsMatcher("click action is labelled \"$resumeLabel\"") { node ->
                    node.config.getOrNull(SemanticsActions.OnClick)?.label == resumeLabel
                }
            )
    }

    private fun show(onResume: () -> Unit = {}) {
        composeRule.setContent {
            ObaTheme {
                PinnedTripFab(
                    state = PinnedTripCardState(
                        destination = PinnedLabel.Text("Ballard"),
                        symbols = listOf(
                            ModeSymbol.Transit(
                                LegBadge(
                                    routes = listOf(RouteBadge("44", routeColor = null)),
                                    mode = TransitMode.BUS,
                                    join = RouteBadgeJoin.ANY_OF
                                )
                            )
                        ),
                        durationMinutes = 32
                    ),
                    onResume = onResume
                )
            }
        }
        composeRule.waitForIdle()
    }
}
