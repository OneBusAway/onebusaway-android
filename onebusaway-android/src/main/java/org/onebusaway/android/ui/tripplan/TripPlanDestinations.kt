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
package org.onebusaway.android.ui.tripplan

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * The trip-planning navigation cluster: the trip-planning form + results sheet ([NavRoutes.TRIP_PLAN])
 * and its "pick a point on the map" sub-screen ([NavRoutes.TRIP_PLAN_PICK_LOCATION]). Both are already
 * self-wiring (they take only the [navController]).
 */
fun NavGraphBuilder.tripPlanGraph(navController: NavHostController) {
    // Trip plan destination: the trip-planning form + results sheet. Reached
    // in-app from the home drawer's "Plan a trip"; re-entered from a RealtimeService trip-
    // update notification (RealtimeService tags the open intent with the TRIP_PLAN route).
    // The destination ports the former TripPlanActivity's Android glue. Non-exported.
    composable(NavRoutes.TRIP_PLAN) {
        ObaTheme {
            TripPlanDestination(
                navController = navController,
                onBack = { navController.popBackStack() },
            )
        }
    }
    // Trip plan "pick a point on the map" sub-screen (former
    // TripPlanLocationPickerActivity). Reached only from the trip-plan destination's
    // from/to "pick on map"; hands the chosen point back via this entry's previous
    // back-stack SavedStateHandle. The initial center arrives as decimal-string lat/lon.
    composable(
        NavRoutes.TRIP_PLAN_PICK_LOCATION,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PICK_LAT) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_PICK_LON) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { entry ->
        ObaTheme {
            TripPlanLocationPickerDestination(
                navController = navController,
                lat = entry.arguments?.getString(NavRoutes.ARG_PICK_LAT)?.toDoubleOrNull(),
                lon = entry.arguments?.getString(NavRoutes.ARG_PICK_LON)?.toDoubleOrNull(),
            )
        }
    }
}
