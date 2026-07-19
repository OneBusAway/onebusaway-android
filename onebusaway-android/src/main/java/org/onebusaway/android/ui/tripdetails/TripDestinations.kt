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
package org.onebusaway.android.ui.tripdetails

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.R
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.PushRegistrationEntryPoint
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.dataview.TripTrajectoryRoute
import org.onebusaway.android.ui.dataview.TripTrajectoryViewModel
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.tripinfo.TripInfoEvent
import org.onebusaway.android.ui.tripinfo.TripInfoRoute
import org.onebusaway.android.ui.tripinfo.TripInfoViewModel
import org.onebusaway.android.ui.tripinfo.confirmDeleteReminder

/**
 * The trip navigation cluster: a trip's stops + live vehicle ([NavRoutes.TRIP_DETAILS]) and the
 * reminder editor ([NavRoutes.TRIP_INFO]). Both recover the host via [findActivity] for the
 * destination-reminder prefs, the notification-permission request, and the delete-reminder confirm.
 */
fun NavGraphBuilder.tripGraph(navController: NavHostController) {
    // TripDetails destination: a trip's stops + live vehicle position.
    // Reached in-app from the arrivals destination's "show trip"; TripDetailsActivity still
    // hosts the same TripDetailsRoute for standalone/map/NavigationService launches
    // (collapsed to an activity-alias). The destination-reminder flow is the shared
    // rememberDestinationReminderAction controller; the VM reads its args from the nav-args.
    composable(
        NavRoutes.TRIP_DETAILS,
        arguments = listOf(
            navArgument(NavRoutes.ARG_TRIP_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_STOP_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(NavRoutes.ARG_SCROLL_MODE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val context = LocalContext.current
        val tripId =
            backStackEntry.arguments?.getString(NavRoutes.ARG_TRIP_ID).orEmpty()
        val tripStopId = backStackEntry.arguments?.getString(NavRoutes.ARG_STOP_ID)
        val tripVm: TripDetailsViewModel = hiltViewModel()
        ObaTheme {
            TripDetailsRoute(
                viewModel = tripVm,
                onBack = { navController.popBackStack() },
                onStopClick = { sid, name, _ ->
                    navController.navigate(NavRoutes.arrivals(sid, name))
                },
                onSetDestinationReminder = rememberDestinationReminderAction(
                    viewModel = tripVm,
                    prefsRepository = PreferencesEntryPoint.get(context),
                    tripId = tripId,
                    stopId = tripStopId,
                ),
                onShowTrajectory = { navController.navigate(NavRoutes.tripTrajectory(tripId)) },
            )
        }
    }
    // TripTrajectory destination (debug): the speed-estimation distance-vs-time graph for a trip.
    // Modernizes the former VehicleLocationDataActivity; a NavHost destination reached by route
    // (NavRoutes.tripTrajectory). The entry affordance from TripDetailsScreen is a follow-up.
    composable(
        NavRoutes.TRIP_TRAJECTORY,
        arguments = listOf(
            navArgument(NavRoutes.ARG_TRIP_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_STOP_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) {
        val trajectoryVm: TripTrajectoryViewModel = hiltViewModel()
        ObaTheme {
            TripTrajectoryRoute(viewModel = trajectoryVm, onBack = { navController.popBackStack() })
        }
    }
    // TripInfo destination (reminder editor). Reached in-app from the home reminders
    // overlay's edit-tap and the arrivals "set reminder" action (both via the
    // TripInfoActivity facade → HomeActivity → translator). Non-exported; no alias.
    composable(
        NavRoutes.TRIP_INFO,
        arguments = listOf(
            navArgument(NavRoutes.ARG_TRIP_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_STOP_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_ROUTE_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_ROUTE_NAME) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_STOP_NAME) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_HEADSIGN) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_DEPART_TIME) {
                type = NavType.LongType; defaultValue = 0L
            },
            navArgument(NavRoutes.ARG_STOP_SEQUENCE) {
                type = NavType.IntType; defaultValue = 0
            },
            navArgument(NavRoutes.ARG_SERVICE_DATE) {
                type = NavType.LongType; defaultValue = 0L
            },
            navArgument(NavRoutes.ARG_VEHICLE_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument(NavRoutes.ARG_SERVER_NOW) {
                type = NavType.LongType; defaultValue = 0L
            },
        ),
    ) { backStackEntry ->
        val activity = LocalContext.current.findActivity()
        val infoTripId =
            backStackEntry.arguments?.getString(NavRoutes.ARG_TRIP_ID).orEmpty()
        val infoStopId =
            backStackEntry.arguments?.getString(NavRoutes.ARG_STOP_ID).orEmpty()
        val infoVm: TripInfoViewModel = hiltViewModel()
        // The system notification-permission dialog only pauses (never stops) the activity, so the
        // ProcessLifecycleOwner ON_START resync in PushRegistrationManager never fires for it. Resync
        // straight from the result callback so an in-flow opt-in registers this device immediately
        // instead of waiting for the next app launch.
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { PushRegistrationEntryPoint.get(activity).resync() }
        LaunchedEffect(infoVm) {
            infoVm.events.collect { event ->
                when (event) {
                    TripInfoEvent.Saved -> {
                        Toast.makeText(
                            activity, R.string.trip_info_saved, Toast.LENGTH_SHORT
                        ).show()
                        navController.popBackStack()
                    }
                    TripInfoEvent.SaveFailed -> Toast.makeText(
                        activity, R.string.failed_to_set_reminder, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        ObaTheme {
            TripInfoRoute(
                viewModel = infoVm,
                onBack = { navController.popBackStack() },
                onSave = {
                    // POST_NOTIFICATIONS is a runtime permission only on API 33+; older versions grant it implicitly.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    infoVm.save()
                },
                onDelete = {
                    confirmDeleteReminder(activity) {
                        DatabaseEntryPoint.get(activity).reminderRepository()
                            .deleteReminderInBackground(infoTripId, infoStopId)
                        navController.popBackStack()
                    }
                },
                onShowRoute = {
                    infoVm.routeId()?.let { navController.navigate(NavRoutes.routeInfo(it)) }
                },
                onShowStop = {
                    navController.navigate(NavRoutes.arrivals(infoStopId, infoVm.stopName()))
                },
            )
        }
    }
}
