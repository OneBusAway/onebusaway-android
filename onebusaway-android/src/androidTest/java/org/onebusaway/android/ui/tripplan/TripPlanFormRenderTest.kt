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
package org.onebusaway.android.ui.tripplan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import kotlin.math.absoluteValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * On-device checks on the compact directions form (#2094). The point of that redesign was a specific
 * vertical budget — two 48dp endpoint rows over one 40dp action bar, 146dp in total, down from 216dp —
 * and a height claim is exactly the kind of thing that rots silently as the composable is edited, so
 * it's asserted here at real density rather than left in a commit message.
 *
 * The rest covers what moved rather than what shrank: the endpoint rows carry no permanent trailing
 * chrome, current-location and pick-on-map appear only once a place is being chosen, and a resolved
 * endpoint is tap-to-edit without being cleared by the tap.
 */
class TripPlanFormRenderTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    /** The directions card's content width on a 412dp phone: the screen less its 12dp margins. */
    private val cardWidth = 388.dp

    private val plannedState = TripPlanFormState(
        from = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3),
        to = TripEndpoint.Geocoded("Pike Place Market", lat = 47.61, lon = -122.34),
        departNow = true,
        dateLabel = "June 10",
        timeLabel = "3:45 PM"
    )

    @Test
    fun theRestingFormFitsItsVerticalBudget() {
        renderForm(plannedState)

        val height = composeRule.onNodeWithTag(FORM).getUnclippedBoundsInRoot().height

        // 4 + 48 + 1 + 48 + 1 + 40 + 4. Each of those seven bands is rounded to whole pixels
        // independently, so the total can drift by up to seven half-pixels — expressed in dp, since
        // that is what drifts. Nothing wider: this test exists to hold the budget, not to wave at it.
        val allowance = with(composeRule.density) { (7 * 0.5f).toDp() }
        assertTrue(
            "expected the resting form to measure 146dp (±$allowance), but it was $height",
            (height - 146.dp).value.absoluteValue <= allowance.value
        )
    }

    @Test
    fun modePickersExpandToLabelsAndReportTheSelection() {
        var vehicle: VehicleMode? = null
        var street: StreetMode? = null
        renderForm(
            state = { plannedState },
            onVehicleModeSelected = { vehicle = it },
            onStreetModeSelected = { street = it }
        )

        composeRule.onNodeWithTag(TripPlanTestTags.VEHICLE_MODE).performClick()
        composeRule.onNodeWithText("Rail only").performClick()
        assertEquals(VehicleMode.RAIL, vehicle)

        composeRule.onNodeWithTag(TripPlanTestTags.STREET_MODE).performClick()
        composeRule.onNodeWithText("My own bike").performClick()
        assertEquals(StreetMode.BICYCLE, street)
    }

    @Test
    fun aResolvedEndpointShowsItsPlaceWithNoTrailingChrome() {
        renderForm(plannedState)

        composeRule.onNodeWithText("Pike Place Market").assertIsDisplayed()
        // The four per-field icon buttons the redesign retired. Their content descriptions are the only
        // thing that ever identified them, so their absence is what there is to assert.
        val pickOnMapButtons = composeRule
            .onAllNodesWithContentDescription("Pick location on map")
            .fetchSemanticsNodes()
            .size
        assertTrue(
            "pick-on-map should not be permanent field chrome, found $pickOnMapButtons",
            pickOnMapButtons == 0
        )
    }

    @Test
    fun choosingAPlaceOffersLocationAndMapBeforeTheSuggestions() {
        renderForm(
            plannedState.copy(
                from = TripEndpoint.FreeText(""),
                fromSuggestions = listOf(
                    TripEndpoint.Geocoded("Pike St & 3rd Ave", 47.61, -122.33, isTransit = true),
                    TripEndpoint.Geocoded("Pike Brewing Company", 47.60, -122.34)
                )
            )
        )

        // Focusing is enough to raise the list — no typing, which keeps the IME (and its timing) out
        // of a test that is about ordering.
        composeRule.onNodeWithTag(FROM_FIELD).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FROM_MY_LOCATION).assertIsDisplayed()
        composeRule.onNodeWithTag(FROM_PICK_ON_MAP).assertIsDisplayed()
        composeRule.onNodeWithText("Pike Brewing Company").assertIsDisplayed()

        // Ordering is the point — the actions are pinned to the *head* of the list. Visibility alone
        // would pass with them sitting below the geocoder's results.
        val firstSuggestionTop = composeRule.onNodeWithText("Pike St & 3rd Ave")
            .getUnclippedBoundsInRoot().top
        listOf(FROM_MY_LOCATION, FROM_PICK_ON_MAP).forEach { tag ->
            val actionTop = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
            assertTrue(
                "$tag should precede the suggestions, but sat at $actionTop vs $firstSuggestionTop",
                actionTop < firstSuggestionTop
            )
        }
    }

    /**
     * Tapping a resolved endpoint opens it for editing but must not clear it: clearing would make the
     * form unsubmittable and drop the planned route off the map, so a stray tap would discard a plan.
     */
    @Test
    fun tappingAResolvedEndpointDoesNotClearIt() {
        var cleared = false
        renderForm(plannedState, onToQueryChange = { cleared = true })

        composeRule.onNodeWithTag(TO_FIELD).performClick()
        composeRule.waitForIdle()

        assertTrue("tapping to edit must not rewrite the endpoint", !cleared)
    }

    /**
     * Tapping a resolved endpoint has to *stay* in edit mode. The field attaches unfocused, so a naive
     * "leave edit mode when focus is lost" fires once on attach and collapses the row again — the
     * dropdown flashes open and shuts, and the endpoint can't be edited at all.
     */
    @Test
    fun tappingAResolvedEndpointStaysOpenForEditing() {
        renderForm(plannedState)

        composeRule.onNodeWithTag(TO_FIELD).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TO_MY_LOCATION).assertIsDisplayed()
        composeRule.onNodeWithTag(TO_PICK_ON_MAP).assertIsDisplayed()
    }

    /** The same flash-and-close, from the other direction: focusing an empty field must offer them too. */
    @Test
    fun focusingAnEmptyEndpointOffersTheActionsWithoutTyping() {
        renderForm(plannedState.copy(from = TripEndpoint.FreeText("")))

        composeRule.onNodeWithTag(FROM_FIELD).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FROM_MY_LOCATION).assertIsDisplayed()
        composeRule.onNodeWithTag(FROM_PICK_ON_MAP).assertIsDisplayed()
    }

    @Test
    fun theActionBarStatesWhenTheTripIsFor() {
        renderForm(plannedState)

        composeRule.onNodeWithTag(TripPlanTestTags.WHEN_MODE).assertIsDisplayed()
        composeRule.onNodeWithText("now").assertIsDisplayed()
    }

    /**
     * The action bar's trailing buttons stay put whatever the time segment reads. The segment is the
     * bar's one flexible slot, so it has to both hold the buttons against the edge when the label is
     * the single word "now" and cap itself when the label is a full date and time — sharing that
     * flexibility with a weighted spacer got the first half wrong, leaving the buttons floating short
     * of the edge, and nothing caught it because both labels rendered without overflowing.
     */
    @Test
    fun theActionBarHoldsItsButtonsInPlaceWhateverTheTimeReads() {
        var departNow by mutableStateOf(true)
        renderForm(
            state = {
                plannedState.copy(
                    departNow = departNow,
                    dateLabel = "Wednesday, 30 September",
                    timeLabel = "11:45 PM"
                )
            }
        )

        val withNow = composeRule.onNodeWithContentDescription(ADVANCED_SETTINGS)
            .getUnclippedBoundsInRoot().right

        departNow = false
        composeRule.waitForIdle()
        val withPinnedTime = composeRule.onNodeWithContentDescription(ADVANCED_SETTINGS)
            .getUnclippedBoundsInRoot().right

        assertEquals(
            "the trailing buttons moved when the time label grew",
            withNow,
            withPinnedTime
        )
    }

    /**
     * Choosing "Your location" repeatedly has to keep resolving the endpoint. It used to work only on
     * alternate taps: the cycles that started from an already-resolved endpoint left a focused text
     * field to be torn down, and the value change that came out of that teardown was forwarded as a
     * *query* — turning the resolved endpoint back into coordinate-less free text and dropping the
     * planned route off the map.
     *
     * Driven through a state holder that mirrors TripPlanViewModel's two writes, since the bug is in
     * the round trip between them rather than in either one.
     */
    @Test
    fun repeatedlyChoosingYourLocationKeepsTheOriginResolved() {
        var from: TripEndpoint by mutableStateOf(TripEndpoint.FreeText(""))
        renderForm(
            state = { plannedState.copy(from = from) },
            // Exactly what the ViewModel does with each of these, for the origin slot.
            onQueryChange = { slot, text ->
                if (slot == TripEndpointSlot.FROM) from = TripEndpoint.FreeText(text)
            },
            onSelect = { slot, place -> if (slot == TripEndpointSlot.FROM) from = place },
            onCurrentLocation = { slot ->
                if (slot == TripEndpointSlot.FROM) {
                    from = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3)
                }
            }
        )

        repeat(4) { attempt ->
            composeRule.onNodeWithTag(FROM_FIELD).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(FROM_MY_LOCATION).performClick()
            composeRule.waitForIdle()

            assertTrue(
                "after ${attempt + 1} selection(s) the origin should still resolve, but it was $from",
                from.hasCoordinates
            )
        }
    }

    /**
     * The rail's colour rule, one render per case (the test rule allows a single setContent).
     * Asserted as channel dominance rather than exact hex, so it holds in both themes and survives a
     * palette tweak.
     */
    @Test
    fun aChosenOriginAndDestinationReadGreenThenRed() {
        renderForm(
            plannedState.copy(
                from = TripEndpoint.Geocoded("Space Needle", lat = 47.62, lon = -122.34),
                to = TripEndpoint.Geocoded("Pike Place Market", lat = 47.61, lon = -122.34)
            )
        )

        assertDominant(railDot(row = 0), Channel.GREEN, "a chosen origin")
        assertDominant(railDot(row = 1), Channel.RED, "a chosen destination")
    }

    @Test
    fun anOriginAtTheDevicePositionReadsBlue() {
        renderForm(
            plannedState.copy(
                from = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3),
                to = TripEndpoint.Geocoded("Pike Place Market", lat = 47.61, lon = -122.34)
            )
        )

        assertDominant(railDot(row = 0), Channel.BLUE, "an origin at the device's position")
    }

    /** Blue is about *which* place it is, not which end of the trip it sits at. */
    @Test
    fun aDestinationAtTheDevicePositionReadsBlue() {
        renderForm(
            plannedState.copy(
                from = TripEndpoint.Geocoded("Space Needle", lat = 47.62, lon = -122.34),
                to = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3)
            )
        )

        assertDominant(railDot(row = 1), Channel.BLUE, "a destination at the device's position")
    }

    private enum class Channel { RED, GREEN, BLUE }

    /**
     * Dominance rather than an exact hex, so the rule survives a palette tweak and holds in both
     * themes — but strictly, with no tolerance to tune: the sample is the centre of a 12dp filled
     * circle, so it is the fill colour itself, not an antialiased edge.
     */
    private fun assertDominant(color: Color, channel: Channel, what: String) {
        val (dominant, others) = when (channel) {
            Channel.RED -> color.red to listOf(color.green, color.blue)
            Channel.GREEN -> color.green to listOf(color.red, color.blue)
            Channel.BLUE -> color.blue to listOf(color.red, color.green)
        }
        assertTrue(
            "$what should read $channel, but its dot was $color",
            others.all { dominant > it }
        )
    }

    /** Samples the centre of a row's rail dot. Mirrors the form's own 4dp pad / 48dp row / 1dp rule. */
    private fun railDot(row: Int): Color {
        val pixels = composeRule.onNodeWithTag(FORM).captureToImage().toPixelMap()
        val d = composeRule.density
        val x = with(d) { (RAIL_WIDTH_DP / 2).roundToPx() }
        val y = with(d) { (4.dp + 24.dp + 49.dp * row).roundToPx() }
        return pixels[x, y]
    }

    private fun renderForm(
        state: TripPlanFormState,
        onToQueryChange: (String) -> Unit = {}
    ) = renderForm(
        state = { state },
        onQueryChange = { slot, text -> if (slot == TripEndpointSlot.TO) onToQueryChange(text) }
    )

    /** [state] is a lambda so a test can back it with its own mutable state and observe writes. */
    private fun renderForm(
        state: () -> TripPlanFormState,
        onQueryChange: (TripEndpointSlot, String) -> Unit = { _, _ -> },
        onSelect: (TripEndpointSlot, TripEndpoint.Geocoded) -> Unit = { _, _ -> },
        onCurrentLocation: (TripEndpointSlot) -> Unit = {},
        onVehicleModeSelected: (VehicleMode) -> Unit = {},
        onStreetModeSelected: (StreetMode) -> Unit = {}
    ) {
        composeRule.setContent {
            ObaTheme {
                Box(Modifier.width(cardWidth).testTag(FORM)) {
                    TripPlanForm(
                        state = state(),
                        onQueryChange = onQueryChange,
                        onSelect = onSelect,
                        onCurrentLocation = onCurrentLocation,
                        onPickOnMap = {},
                        onSetArriving = {},
                        onDepartNow = {},
                        onPickDate = {},
                        onPickTime = {},
                        availableStreetModes = StreetMode.entries,
                        onVehicleModeSelected = onVehicleModeSelected,
                        onStreetModeSelected = onStreetModeSelected,
                        onReverse = {},
                        onAdvancedSettings = {}
                    )
                }
            }
        }
    }

    private companion object {
        const val FORM = "tripPlanFormRoot"

        /** The advanced-settings button's contentDescription — the bar's last trailing child. */
        const val ADVANCED_SETTINGS = "Additional Trip Preferences"
        val RAIL_WIDTH_DP = 44.dp
        val FROM_FIELD = TripPlanTestTags.FROM_PREFIX + TripPlanTestTags.FIELD_SUFFIX
        val TO_FIELD = TripPlanTestTags.TO_PREFIX + TripPlanTestTags.FIELD_SUFFIX
        val FROM_MY_LOCATION = TripPlanTestTags.FROM_PREFIX + TripPlanTestTags.MY_LOCATION_SUFFIX
        val FROM_PICK_ON_MAP = TripPlanTestTags.FROM_PREFIX + TripPlanTestTags.PICK_ON_MAP_SUFFIX
        val TO_MY_LOCATION = TripPlanTestTags.TO_PREFIX + TripPlanTestTags.MY_LOCATION_SUFFIX
        val TO_PICK_ON_MAP = TripPlanTestTags.TO_PREFIX + TripPlanTestTags.PICK_ON_MAP_SUFFIX
    }
}
