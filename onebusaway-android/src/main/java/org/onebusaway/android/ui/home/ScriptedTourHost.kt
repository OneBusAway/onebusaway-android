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
package org.onebusaway.android.ui.home

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import org.onebusaway.android.demo.DemoModeController
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.render.MapViewport
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.models.WheelchairBoarding
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripEndpointSlot
import org.onebusaway.android.ui.tripplan.TripPlanViewModel
import org.onebusaway.android.ui.tripresults.TripResultsUiState
import org.onebusaway.android.ui.tripresults.TripResultsViewModel
import org.onebusaway.android.ui.tutorial.ScriptedTutorialActions
import org.onebusaway.android.util.GeoPoint

/**
 * What the scripted tour changed that would otherwise outlive it (#2164), captured when it starts.
 *
 * Only two things qualify: the camera, because the tour flies it to a city the rider isn't in, and the
 * rental layer, because that toggle is a persisted preference. Every other action the tour takes is
 * in-memory focus state, which its teardown clears outright.
 */
data class ScriptedTourUndo(
    val viewport: MapViewport?,
    /** The rental layer's three preferences: the master switch and each mode toggle. */
    val rentalsVisible: Boolean,
    val bikesVisible: Boolean,
    val scootersVisible: Boolean
)

/**
 * Binds the scripted tour's stage directions to the real app (#2164): each lambda performs, through the
 * ordinary view models, exactly the action the rider would have performed themselves.
 *
 * Going through the same entry points a tap does — `revealStop` for a stop, `requestShowFocusedStopRouteOnMap`
 * for a route badge, `selectFocusedRouteTrip` for an ETA pill — is what keeps the tour honest: it
 * demonstrates the app rather than a staged imitation of it, and a change to how focus works can't leave
 * the tutorial describing behaviour the app no longer has.
 *
 * **Nothing here writes to the rider's data.** The steps about starring and pinning spotlight those
 * controls and explain them; they do not press them.
 */
@Composable
internal fun rememberScriptedTutorialActions(
    demoMode: DemoModeController,
    homeViewModel: HomeViewModel,
    mapViewModel: MapViewModel,
    tripPlanViewModel: TripPlanViewModel,
    tripResultsViewModel: TripResultsViewModel,
    drawerState: DrawerState
): ScriptedTutorialActions {
    return remember(demoMode, homeViewModel, mapViewModel, tripPlanViewModel, tripResultsViewModel, drawerState) {
        ScriptedTutorialActions(
            focusDemoStop = {
                val stop = demoMode.fixture.anchorStop ?: return@ScriptedTutorialActions
                homeViewModel.revealStop(
                    FocusedStop(
                        id = stop.id,
                        name = stop.name,
                        code = stop.code,
                        point = GeoPoint(stop.lat, stop.lon),
                        wheelchairBoarding = WheelchairBoarding.UNKNOWN
                    ),
                    animate = true
                )
                // Revealing a stop clears the map focus first, and that clear travels through a
                // directive which drops retained framing on its way (see [showRentals]) — so the zoom
                // has to follow it rather than race it.
                delay(FOCUS_SETTLE_MILLIS)
                // Come in from the opening overview. This is also what restores the zoom when the rider
                // steps *back* out of the route view, which frames the whole line.
                mapViewModel.aimAt(DemoModeController.CAMERA_TARGET, DemoModeController.DETAIL_ZOOM)
            },
            showDemoRoute = {
                val route = demoMode.featuredRoute ?: return@ScriptedTutorialActions
                homeViewModel.requestShowFocusedStopRouteOnMap(
                    routeId = route.routeId,
                    directionId = route.directionId,
                    shortName = route.shortName
                )
            },
            selectDemoTrip = {
                // Null when no bus of the featured route is out right now; the step then narrates the
                // route view it already has rather than selecting a vehicle that doesn't exist.
                demoMode.featuredTripId()?.let(homeViewModel::selectFocusedRouteTrip)
            },
            setDrawerOpen = { open -> if (open) drawerState.open() else drawerState.close() },
            // Just the focus unwind, which is what backing out of a route actually does — the camera
            // stays where the rider left it rather than springing somewhere new.
            resetMap = homeViewModel::clearMapFocus,
            showRentals = {
                // Reached backwards from the trip-planning step, the app is still in directions, which
                // has to be unwound before a map layer means anything. Forward this costs nothing:
                // there is no focus to clear, so it returns without even emitting a directive.
                homeViewModel.clearMapFocus()
                // Clearing a focus travels through a map *directive* that the host consumes a frame or
                // two later, and that drops any retained framing on its way — so the aim below has to
                // follow it rather than race it. Settling first is why these actions suspend. Worst
                // case if the wait is short, the camera simply isn't re-aimed.
                delay(FOCUS_SETTLE_MILLIS)
                // Bring the neighbourhood back into view. Unwinding the focus left the camera wherever
                // the last route framing flew it (for the demo 49, the whole city), where the rentals
                // would be a few overlapping pixels.
                mapViewModel.aimAt(DemoModeController.CAMERA_TARGET, DemoModeController.CAMERA_ZOOM)
                // The master switch alone can leave nothing drawn: it deliberately preserves whichever
                // modes the rider had, and both default off. The demo set has bikes, docks *and*
                // scooters, so the tour asks for both modes. All three are preferences, and all three
                // are put back on teardown.
                mapViewModel.setRentalLayerVisible(RentalLayer.BIKES, true)
                mapViewModel.setRentalLayerVisible(RentalLayer.SCOOTERS, true)
                mapViewModel.setRentalsVisible(true)
            },
            planDemoTrip = {
                homeViewModel.enterDirections()
                val stop = demoMode.fixture.anchorStop
                tripPlanViewModel.setEndpoint(
                    TripEndpointSlot.FROM,
                    TripEndpoint.Geocoded(
                        displayName = stop?.name.orEmpty()
                            .ifBlank { DemoModeController.TRIP_PLAN_DESTINATION_NAME },
                        lat = stop?.lat ?: DemoModeController.CAMERA_TARGET.latitude,
                        lon = stop?.lon ?: DemoModeController.CAMERA_TARGET.longitude,
                        isTransit = true
                    )
                )
                tripPlanViewModel.setEndpoint(
                    TripEndpointSlot.TO,
                    TripEndpoint.Geocoded(
                        displayName = DemoModeController.TRIP_PLAN_DESTINATION_NAME,
                        lat = DemoModeController.TRIP_PLAN_DESTINATION.latitude,
                        lon = DemoModeController.TRIP_PLAN_DESTINATION.longitude,
                        isTransit = true
                    )
                )
            },
            showOtherItinerary = {
                // Step to the *next* option, wrapping. The point of the step is that the map redraws, so
                // it has to land somewhere other than where it already is; which option in particular
                // doesn't matter, and stepping keeps it right however many the planner returned.
                val results = tripResultsViewModel.state.value as? TripResultsUiState.Success
                if (results != null && results.options.size > 1) {
                    tripResultsViewModel.selectOption((results.selectedIndex + 1) % results.options.size)
                }
            }
        )
    }
}

/**
 * How long to let a focus change reach the map before ordering the camera about.
 *
 * A couple of frames' grace rather than a synchronisation point: the map host consumes focus directives
 * on the main thread a frame or two after they're emitted, and there is no signal published for "that
 * one has landed". Generous enough that it always has, and harmless when it hasn't — the step just
 * shows an un-reaimed camera.
 */
private const val FOCUS_SETTLE_MILLIS = 250L
