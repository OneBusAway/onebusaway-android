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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * Coverage for the option card's street-distance metric lines (#2122): a card measures **every** street
 * mode the trip travels on, so a bikeshare trip says how far it rides as well as how far it walks, and a
 * bike-only trip says how far it bikes instead of reporting "0 ft" of walking as its whole street cost —
 * and each of those lines can win its own "least" category, so the emphasis follows whatever the plan is
 * actually made of rather than always landing on the walking line.
 *
 * The distances are asserted as the text a rider reads (`ConversionUtils`-formatted, in whatever units
 * the device is set to) rather than as raw meters, since the formatting is the point of a distance line.
 */
class OptionCardStreetMetricsTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aBikeshareTripMeasuresBothItsRideAndItsWalk() {
        show(option(StreetMode.WALK to WALK_METERS, StreetMode.BIKESHARE to BIKE_METERS))

        composeRule.onNodeWithText(distance(WALK_METERS)).assertIsDisplayed()
        composeRule.onNodeWithText(distance(BIKE_METERS)).assertIsDisplayed()
    }

    @Test
    fun aBikeOnlyTripMeasuresItsBiking_andSaysNothingAboutWalking() {
        show(option(StreetMode.BIKE to BIKE_METERS))

        composeRule.onNodeWithText(distance(BIKE_METERS)).assertIsDisplayed()
        // A single option wins no category, so nothing forces the zero-walk line a LEAST_WALKING
        // winner keeps.
        composeRule.onNodeWithText(distance(0.0)).assertDoesNotExist()
    }

    /**
     * The win the walking line has always had, now on the mode a bikeshare plan is really compared on.
     * The two options are identical but for how far they ride, so nothing else can be won.
     */
    @Test
    fun theOptionThatRidesLeastAnnouncesItsBikeshareWin() {
        show(
            option(StreetMode.WALK to WALK_METERS, StreetMode.BIKESHARE to BIKE_METERS),
            option(StreetMode.WALK to WALK_METERS, StreetMode.BIKESHARE to BIKE_METERS * 2)
        )

        composeRule
            .onNode(hasStateDescription(context.getString(R.string.trip_plan_winner_least_bikesharing)))
            .assertExists()
    }

    // ---- fixtures -----------------------------------------------------------------------------------

    /** The distance line's text, assembled from its parts exactly as the card draws them. */
    private fun distance(meters: Double) = ConversionUtils.getFormattedDistanceParts(meters, context)
        .joinToString(separator = "") { it.text }

    private fun option(vararg distances: Pair<StreetMode, Double>) = ItineraryOption(
        symbols = listOf(ModeSymbol.Street(distances.first().first)),
        durationMinutes = 18L,
        startTime = ServerTime(0L),
        endTime = ServerTime(18 * 60_000L),
        streetDistanceMeters = distances.toMap()
    )

    private fun show(vararg options: ItineraryOption) {
        composeRule.setContent {
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
        composeRule.waitForIdle()
    }
}

/** Two distances far enough apart to format differently whichever units the device uses. */
private const val WALK_METERS = 400.0
private const val BIKE_METERS = 2300.0
