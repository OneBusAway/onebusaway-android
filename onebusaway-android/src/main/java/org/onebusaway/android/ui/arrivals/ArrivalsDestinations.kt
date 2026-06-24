/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.routeinfo.RouteInfoRoute
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher

/**
 * The arrivals navigation cluster: a route's stops by direction ([NavRoutes.ROUTE_INFO]) and a stop's
 * real-time arrivals ([NavRoutes.ARRIVALS]). Both recover the host via [findActivity]; the arrivals VM
 * is built from the host's assisted factory keyed on the nav-arg stop id (process-death safe).
 */
fun NavGraphBuilder.arrivalsGraph(navController: NavHostController) {
    // RouteInfo destination: a route's stops grouped by direction. Reached
    // in-app from the home reminders overlay's "show route"; RouteInfoActivity still hosts
    // the same RouteInfoRoute for the standalone/external launch paths (collapsed to an
    // activity-alias). The VM reads routeId from SavedStateHandle (the nav-arg).
    composable(
        NavRoutes.ROUTE_INFO,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ROUTE_ID) { type = NavType.StringType }
        ),
    ) { backStackEntry ->
        val activity = LocalContext.current.findActivity() as HomeActivity
        val routeId =
            backStackEntry.arguments?.getString(NavRoutes.ARG_ROUTE_ID).orEmpty()
        ObaTheme {
            RouteInfoRoute(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onShowRouteOnMap = { activity.showRouteOnMap(routeId) },
                onStopClick = { stop ->
                    navController.navigate(NavRoutes.arrivals(stop.id, stop.name))
                },
                onStopShowOnMap = { stop ->
                    activity.focusStopOnMap(stop.id, stop.latitude, stop.longitude)
                },
            )
        }
    }
    // Arrivals destination: real-time arrivals for a stop. Reached in-app
    // from RouteInfo's stop tap and the home overlays' stop taps; ArrivalsListActivity
    // still hosts the same ArrivalsRoute for the standalone/FCM/external paths (collapsed
    // to an activity-alias). The VM is built from the assisted factory with the
    // nav-arg stop id (process-death safe — it's re-read from the back-stack arg).
    composable(
        NavRoutes.ARRIVALS,
        arguments = listOf(
            navArgument(NavRoutes.ARG_STOP_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_STOP_NAME) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val activity = LocalContext.current.findActivity() as HomeActivity
        val stopId =
            backStackEntry.arguments?.getString(NavRoutes.ARG_STOP_ID).orEmpty()
        val stopName =
            backStackEntry.arguments?.getString(NavRoutes.ARG_STOP_NAME).orEmpty()
        val arrivalsVm: ArrivalsViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    activity.arrivalsViewModelFactory.create(stopId, ignorePersistedFilter = false)
                }
            }
        )
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val handler = remember(arrivalsVm) {
            createArrivalActionHandler(
                activity = activity,
                viewModel = arrivalsVm,
                currentContent = { arrivalsVm.state.value as? ArrivalsUiState.Content },
                onShowRouteOnMap = { routeId ->
                    activity.showRouteOnMap(routeId)
                },
                showUndoSnackbar = { messageRes, actionRes, onAction ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = activity.getString(messageRes),
                            actionLabel = actionRes?.let { activity.getString(it) },
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
                    }
                },
                onShowTrip = { tripId, sid ->
                    navController.navigate(
                        NavRoutes.tripDetails(tripId, sid, TripDetailsLauncher.SCROLL_MODE_STOP)
                    )
                },
            )
        }
        ObaTheme {
            ArrivalsRoute(
                viewModel = arrivalsVm,
                initialTitle = stopName,
                handler = handler,
                onBack = { navController.popBackStack() },
                snackbarHostState = snackbarHostState,
            )
        }
    }
}
