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
package org.onebusaway.android.ui.home.directions

import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.tripresults.RouteLegRef
import org.onebusaway.android.ui.tripresults.RouteStopRef
import org.onebusaway.android.util.GeoPoint

/**
 * [DirectionStopEtaStrip] owns the decision of what an un-queryable stop renders — its hosts just hand
 * it a stop. A stop missing the OBA id or the location needed to fetch arrivals draws **nothing**:
 * "no upcoming arrivals" is reserved for a stop that was actually looked up, since saying it here would
 * report a stop we couldn't identify as one with no service.
 */
class DirectionStopEtaStripTest {

    // See createUnconfinedComposeRule for why Unconfined composition is used here (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val noArrivalsText = context.getString(R.string.directions_stop_no_arrivals)

    private val routeLeg = RouteLegRef(
        routeId = "1_100",
        headsign = "Rainier Beach",
        board = null,
        alight = null
    )

    /** Fails the test if touched: an unidentifiable stop must bail out before any arrivals session. */
    private val failingFactory = object : ArrivalsViewModel.Factory {
        override fun create(stopId: String): ArrivalsViewModel = throw AssertionError("must not open an arrivals session for an unidentifiable stop")
    }

    private fun setContent(stop: RouteStopRef) = composeRule.setContent {
        DirectionStopEtaStrip(
            routeLeg = routeLeg,
            stop = stop,
            arrivalsViewModelFactory = failingFactory,
            onShowTrip = { _, _ -> },
            onEditReminder = {},
            onFocusVehicle = {}
        )
    }

    @Test
    fun stopWithoutAnObaId_rendersNothing() {
        setContent(RouteStopRef(null, "500", "Pine St & 3rd Ave", GeoPoint(47.6150, -122.3350)))

        composeRule.onNodeWithText(noArrivalsText).assertDoesNotExist()
    }

    @Test
    fun stopWithoutALocation_rendersNothing() {
        // The weaker "id is null" test alone would miss this one and still show the misleading text.
        setContent(RouteStopRef("1_500", "500", "Pine St & 3rd Ave", null))

        composeRule.onNodeWithText(noArrivalsText).assertDoesNotExist()
    }
}
