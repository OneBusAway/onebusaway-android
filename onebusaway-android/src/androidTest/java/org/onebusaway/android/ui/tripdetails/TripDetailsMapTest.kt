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
package org.onebusaway.android.ui.tripdetails

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.Position
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.contract.TripReference
import org.onebusaway.android.api.contract.TripStatus
import org.onebusaway.android.api.data.TripDetails
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.consumeRouteReveal
import org.onebusaway.android.ui.nav.showRouteMapFromArrivals

class TripDetailsMapTest {
    @get:Rule val compose = createUnconfinedComposeRule()

    private val active = TripStatus(
        activeTripId = "trip",
        predicted = true,
        position = Position(47.6, -122.3)
    )

    private fun details(status: TripStatus?, routeId: String = "route") = TripDetails(
        EntryWithReferences(
            TripDetailsEntry(tripId = "trip", status = status),
            References(trips = listOf(TripReference(id = "trip", routeId = routeId, directionId = "1")))
        ),
        currentTime = 0L
    )

    @Test
    fun runningTripFocusesItsVehicleWithoutTheOriginatingStop() {
        assertEquals(
            ShowRouteRequest("route", focusTripId = "trip", initialDirectionId = 1),
            details(active).mapRequest()
        )
        // Scheduled positions are also drawn by the map; prediction availability is not motion.
        assertEquals(details(active).mapRequest(), details(active.copy(predicted = false)).mapRequest())
    }

    @Test
    fun tripsWithoutACurrentVehicleOpenTheUnscopedRoute() {
        for (status in listOf(null, active.copy(position = null), active.copy(activeTripId = "previous_trip"), active.copy(status = "CANCELED"))) {
            assertEquals(ShowRouteRequest("route"), details(status).mapRequest())
        }
        assertNull(details(active, routeId = "").mapRequest())
    }

    @Test
    fun mapButtonPassesTheCurrentRequestAndBackReturnsToTripStatus() {
        lateinit var nav: NavHostController
        val content = mutableStateOf(content(details(active).mapRequest()))
        val mapLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.stop_info_option_showonmap)
        compose.setContent {
            nav = rememberNavController()
            ObaTheme {
                NavHost(nav, startDestination = "trip_status") {
                    composable("trip_status") {
                        TripDetailsScreen(
                            state = content.value,
                            onBack = { nav.popBackStack() },
                            onRefresh = {},
                            onStopClick = { _, _, _ -> },
                            onSetDestinationReminder = null,
                            onShowOnMap = nav::showRouteMapFromArrivals
                        )
                    }
                    composable(NavRoutes.HOME) { Text("Map screen") }
                }
            }
        }
        val tripEntry = nav.currentBackStackEntry
        compose.onNodeWithContentDescription(mapLabel).assertIsDisplayed().performClick()
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(tripEntry, nav.previousBackStackEntry)
            assertEquals(details(active).mapRequest(), nav.currentBackStackEntry!!.savedStateHandle.consumeRouteReveal())
            nav.popBackStack()
            // A refresh may replace live data with schedule-only data; the next tap must use it.
            content.value = content(details(null).mapRequest())
        }
        compose.onNodeWithContentDescription(mapLabel).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(ShowRouteRequest("route"), nav.currentBackStackEntry!!.savedStateHandle.consumeRouteReveal())
        }
    }

    @Test
    fun mapButtonIsAbsentUntilARouteIsAvailable() {
        val state = mutableStateOf<TripDetailsUiState>(TripDetailsUiState.Loading)
        val mapLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.stop_info_option_showonmap)
        compose.setContent {
            ObaTheme {
                TripDetailsScreen(state.value, onBack = {}, onRefresh = {}, onStopClick = { _, _, _ -> }, onSetDestinationReminder = null, onShowOnMap = {})
            }
        }
        compose.onNodeWithContentDescription(mapLabel).assertDoesNotExist()
        compose.runOnIdle { state.value = TripDetailsUiState.Error("Unavailable") }
        compose.onNodeWithContentDescription(mapLabel).assertDoesNotExist()
        compose.runOnIdle { state.value = content(null) }
        compose.onNodeWithContentDescription(mapLabel).assertDoesNotExist()
    }

    private fun content(request: ShowRouteRequest?) = TripDetailsUiState.Content(
        header = TripHeader("8", "Capitol Hill", null, "Metro Transit", null, "Scheduled", R.color.stop_info_scheduled_time, false),
        stops = emptyList(),
        scrollToIndex = -1,
        lineColorArgb = 0,
        mapRequest = request
    )
}
