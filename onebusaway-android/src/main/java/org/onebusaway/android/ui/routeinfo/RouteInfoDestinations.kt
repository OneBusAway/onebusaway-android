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
package org.onebusaway.android.ui.routeinfo

import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.StopReveal
import org.onebusaway.android.ui.nav.openSearchStop
import org.onebusaway.android.ui.nav.revealRouteOnMap
import org.onebusaway.android.ui.nav.showRouteMapFromArrivals
import org.onebusaway.android.ui.searchresults.SearchResultMode
import org.onebusaway.android.ui.searchresults.searchResultMode
import org.onebusaway.android.util.GeoPoint

/**
 * The route-info destination: a route's stops grouped by direction ([NavRoutes.ROUTE_INFO]). Reached
 * in-app from the home reminders overlay's "show route" and the My-routes list; `RouteInfoActivity`
 * still hosts the same [RouteInfoRoute] for the standalone/external launch paths (collapsed to an
 * activity-alias). The VM reads routeId from `SavedStateHandle` (the nav-arg).
 *
 * Stop taps follow the search workflow preference; explicit map actions retain the list beneath them.
 */
fun NavGraphBuilder.routeInfoGraph(navController: NavHostController) {
    composable(
        NavRoutes.ROUTE_INFO,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ROUTE_ID) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val routeId =
            backStackEntry.arguments?.getString(NavRoutes.ARG_ROUTE_ID).orEmpty()
        val prefs = PreferencesEntryPoint.get(LocalContext.current)
        ObaTheme {
            RouteInfoRoute(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onShowRouteOnMap = {
                    if (prefs.searchResultMode() == SearchResultMode.LISTS) {
                        navController.showRouteMapFromArrivals(ShowRouteRequest(routeId))
                    } else {
                        navController.revealRouteOnMap(routeId)
                    }
                },
                onStopClick = { stop ->
                    navController.openSearchStop(
                        StopReveal(stop.id, stop.name, GeoPoint(stop.latitude, stop.longitude)),
                        prefs.searchResultMode()
                    )
                }
            )
        }
    }
}
