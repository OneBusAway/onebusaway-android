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

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
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
import org.onebusaway.android.ui.home.ArrivalsSheetState
import org.onebusaway.android.ui.home.HomeBackHandler
import org.onebusaway.android.ui.home.homeBackAction
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.TripMapReveal
import org.onebusaway.android.ui.nav.consumeTripMapReveal
import org.onebusaway.android.ui.nav.mapReturnAction
import org.onebusaway.android.ui.nav.showTripOnMap

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
            details(active).mapRequest()?.routeRequest()
        )
        // Scheduled positions are also drawn by the map; prediction availability is not motion.
        assertEquals(details(active).mapRequest(), details(active.copy(predicted = false)).mapRequest())
    }

    @Test
    fun tripsWithoutACurrentVehicleOpenTheUnscopedRoute() {
        for (status in listOf(null, active.copy(position = null), active.copy(activeTripId = "previous_trip"), active.copy(status = "CANCELED"))) {
            assertEquals(ShowRouteRequest("route"), details(status).mapRequest()?.routeRequest())
        }
        assertNull(details(active, routeId = "").mapRequest())
    }

    @Test
    fun mapButtonPreservesContextAndBackReturnsFromDirectionsOrMap() {
        lateinit var nav: NavHostController
        lateinit var backDispatcher: OnBackPressedDispatcher
        var mapUndoCount = 0
        var directionsBackCount = 0
        val content = mutableStateOf(content(details(active).mapRequest("origin_stop")))
        val mapLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.stop_info_option_showonmap)
        compose.setContent {
            nav = rememberNavController()
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            ObaTheme {
                NavHost(nav, startDestination = NavRoutes.tripDetails("trip", "origin_stop")) {
                    composable(
                        NavRoutes.TRIP_DETAILS,
                        arguments = listOf(
                            navArgument(NavRoutes.ARG_TRIP_ID) { type = NavType.StringType },
                            navArgument(NavRoutes.ARG_STOP_ID) {
                                type = NavType.StringType
                                nullable = true
                            },
                            navArgument(NavRoutes.ARG_SCROLL_MODE) {
                                type = NavType.StringType
                                nullable = true
                            }
                        )
                    ) {
                        TripDetailsScreen(
                            state = content.value,
                            onBack = { nav.popBackStack() },
                            onRefresh = {},
                            onStopClick = { _, _, _ -> },
                            onSetDestinationReminder = null,
                            onShowOnMap = nav::showTripOnMap
                        )
                    }
                    composable(NavRoutes.HOME) {
                        val directionsActive = remember { mutableStateOf(false) }
                        Column {
                            Text(if (directionsActive.value) "Directions screen" else "Map screen")
                            Button(onClick = { directionsActive.value = true }) { Text("Directions") }
                        }
                        // HomeScreen's one Back handler, with the source-page return at the top of it.
                        val onBackToSource = nav.mapReturnAction()
                        HomeBackHandler(
                            action = homeBackAction(
                                returnsToSource = onBackToSource != null,
                                directionsActive = directionsActive.value,
                                pickingEndpoint = false,
                                sheet = ArrivalsSheetState.Hidden,
                                canUndoMapAction = true
                            ),
                            onReturnToSource = { onBackToSource?.invoke() },
                            onCancelEndpointPick = {},
                            onNavigateBackInDirections = { directionsBackCount++ },
                            onCollapseSheet = {},
                            onUndoMapAction = { mapUndoCount++ }
                        )
                    }
                }
            }
        }
        val tripEntry = nav.currentBackStackEntry
        compose.onNodeWithContentDescription(mapLabel).assertIsDisplayed().performClick()
        compose.onNodeWithText("Map screen").assertIsDisplayed()
        compose.onNodeWithText("Directions").performClick()
        compose.onNodeWithText("Directions screen").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(tripEntry, nav.previousBackStackEntry)
            assertEquals(details(active).mapRequest("origin_stop"), nav.currentBackStackEntry!!.savedStateHandle.consumeTripMapReveal())
            backDispatcher.onBackPressed()
            assertEquals(0, mapUndoCount)
            assertEquals(0, directionsBackCount)
            assertSame(tripEntry, nav.currentBackStackEntry)
            // A refresh may replace live data with schedule-only data; the next tap must use it.
            content.value = content(details(null).mapRequest("origin_stop"))
        }
        compose.onNodeWithContentDescription(mapLabel).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(details(null).mapRequest("origin_stop"), nav.currentBackStackEntry!!.savedStateHandle.consumeTripMapReveal())
            backDispatcher.onBackPressed()
            assertEquals(0, mapUndoCount)
            assertEquals(0, directionsBackCount)
            assertSame(tripEntry, nav.currentBackStackEntry)
        }
    }

    /**
     * With no page beneath the map, the same handler dispatches the local rungs through the real
     * dispatcher: a pick is cancelled first, then directions unwinds, then map history. The ordering
     * itself is pinned on the JVM by `HomeBackActionTest`; this checks the composable delivers it.
     */
    @Test
    fun withoutASourceBackCancelsThePickThenUnwindsDirectionsThenMapHistory() {
        lateinit var backDispatcher: OnBackPressedDispatcher
        val directionsActive = mutableStateOf(true)
        val pickingEndpoint = mutableStateOf(true)
        var directionsBackCount = 0
        var mapUndoCount = 0
        compose.setContent {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            val nav = rememberNavController()
            NavHost(nav, startDestination = NavRoutes.HOME) {
                composable(NavRoutes.HOME) {
                    Text("Home screen")
                    HomeBackHandler(
                        action = homeBackAction(
                            returnsToSource = nav.mapReturnAction() != null,
                            directionsActive = directionsActive.value,
                            pickingEndpoint = pickingEndpoint.value,
                            sheet = ArrivalsSheetState.Hidden,
                            canUndoMapAction = true
                        ),
                        onReturnToSource = { fail("no page beneath the map to return to") },
                        onCancelEndpointPick = { pickingEndpoint.value = false },
                        onNavigateBackInDirections = { directionsBackCount++ },
                        onCollapseSheet = {},
                        onUndoMapAction = { mapUndoCount++ }
                    )
                }
            }
        }
        compose.onNodeWithText("Home screen").assertIsDisplayed()
        compose.runOnIdle {
            backDispatcher.onBackPressed()
            assertFalse(pickingEndpoint.value)
            assertEquals(0, directionsBackCount)
            assertEquals(0, mapUndoCount)
        }
        compose.runOnIdle {
            backDispatcher.onBackPressed()
            assertEquals(1, directionsBackCount)
            assertEquals(0, mapUndoCount)
            directionsActive.value = false
        }
        compose.runOnIdle {
            backDispatcher.onBackPressed()
            assertEquals(1, directionsBackCount)
            assertEquals(1, mapUndoCount)
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

    private fun content(request: TripMapReveal?) = TripDetailsUiState.Content(
        header = TripHeader("8", "Capitol Hill", null, "Metro Transit", null, "Scheduled", R.color.stop_info_scheduled_time, false),
        stops = emptyList(),
        scrollToIndex = -1,
        lineColorArgb = 0,
        mapRequest = request
    )
}
