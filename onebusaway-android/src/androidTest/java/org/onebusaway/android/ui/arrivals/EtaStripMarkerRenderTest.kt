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
package org.onebusaway.android.ui.arrivals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.components.ETA_STRIP_MARKER_TAG
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.EtaStripMarker
import org.onebusaway.android.ui.arrivals.components.previewArrival
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The strip's [EtaStripMarker] (#2125) — the rule the directions drawer draws at the moment the rider
 * reaches the stop. What matters is that it lands *between* the right two pills, since a rule on the
 * wrong side of a departure tells the rider the opposite of the truth, and that a screen reader gets the
 * whole sentence (the rule is a bare bar, and the dimming beside it carries no semantics of its own).
 */
class EtaStripMarkerRenderTest {

    // Unconfined composition — see createUnconfinedComposeRule (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val description = "You get to this stop at 3:19pm"
    private val passedDescription = "Leaves before you get here"

    /**
     * A strip of [names].size departures, one route each, leaving 3, 11, 19… minutes out, with the rule
     * at [reachStopMinutes] minutes out.
     * Each pill wears its route's badge so a pill can be located by a string no other node holds — an ETA
     * or clock-time match would risk colliding with a neighbour's subline.
     */
    private fun setContent(vararg names: String, reachStopMinutes: Long, width: Dp? = null) = composeRule.setContent {
        Box(width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth()) {
            EtaStrip(
                trips = names.mapIndexed { i, name ->
                    previewArrival(name, "Rainier Beach", etaMinutes = 3L + i * 8, tripId = "trip_$i")
                },
                actionsFor = { null },
                callbacks = previewRowCallbacks(),
                routeBadgeFor = { RouteBadge(it.shortName ?: error("preview route must have a name"), null) },
                // previewArrival anchors serverNow at 0, so a departure N minutes out sits at N * 60_000.
                marker = EtaStripMarker(
                    at = { ServerTime(reachStopMinutes * 60_000L) },
                    contentDescription = { description },
                    passedStateDescription = passedDescription
                )
            )
        }
    }

    private fun boundsOf(text: String) = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()

    @Test
    fun theRuleSitsBetweenTheDepartureItFollowsAndTheOneItPrecedes() {
        // A leaves 3 min out, B 11 min out; the rider gets there at 6 min.
        setContent("A", "B", reachStopMinutes = 6)

        val rule = composeRule.onNodeWithTag(ETA_STRIP_MARKER_TAG).getUnclippedBoundsInRoot()
        val missed = boundsOf("A")
        val catchable = boundsOf("B")

        assertTrue("rule at ${rule.left} must follow route A, which ends at ${missed.right}", missed.right <= rule.left)
        assertTrue("rule ending at ${rule.right} must precede route B, which starts at ${catchable.left}", rule.right <= catchable.left)
    }

    @Test
    fun aRiderWhoIsAlreadyThereGetsTheRuleAheadOfEveryDeparture() {
        // A leaves 3 min out and the rider is there now.
        setContent("A", reachStopMinutes = 0)

        val rule = composeRule.onNodeWithTag(ETA_STRIP_MARKER_TAG).getUnclippedBoundsInRoot()

        assertTrue("rule ending at ${rule.right} must precede route A", rule.right <= boundsOf("A").left)
    }

    @Test
    fun aRiderArrivingAfterEveryDepartureStillGetsARule() {
        // The moment falls past the last pill — every departure shown is out of reach. The rule closes
        // the strip rather than being dropped, which would leave the row looking unmarked.
        setContent("A", reachStopMinutes = 30)

        val rule = composeRule.onNodeWithTag(ETA_STRIP_MARKER_TAG).getUnclippedBoundsInRoot()

        assertTrue("rule at ${rule.left} must follow route A", boundsOf("A").right <= rule.left)
    }

    @Test
    fun anOverflowingStripOpensWithTheRuleAtItsLeftEdge() {
        // Six departures in a strip too narrow to show them all; the rider gets there after the first
        // two. The strip opens on the rule (#2228) — the catchable departures lead, and the two already
        // ruled out sit behind the "earlier" chevron rather than taking the first two slots.
        setContent("A", "B", "C", "D", "E", "F", reachStopMinutes = 14, width = 200.dp)

        val rule = composeRule.onNodeWithTag(ETA_STRIP_MARKER_TAG).getUnclippedBoundsInRoot()
        val firstCatchable = boundsOf("C")

        composeRule.onNodeWithText("A").assertIsNotDisplayed()
        composeRule.onNodeWithText("B").assertIsNotDisplayed()
        assertTrue("rule ending at ${rule.right} must precede route C at ${firstCatchable.left}", rule.right <= firstCatchable.left)
        assertTrue("rule at ${rule.left} must open the strip, not sit past its first slot", rule.left < 40.dp)
    }

    @Test
    fun theRuleReadsAsOneSentenceToAScreenReader() {
        setContent("A", "B", reachStopMinutes = 6)

        composeRule.onNodeWithContentDescription(description).assertExists()
        // ...and the pill it passed says so itself, where a screen reader actually meets it — the rule
        // is read after that pill, too late to explain it.
        composeRule.onNode(hasStateDescription(passedDescription)).assertExists()
    }
}
