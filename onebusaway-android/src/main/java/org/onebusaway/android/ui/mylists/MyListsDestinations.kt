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
package org.onebusaway.android.ui.mylists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.navigateFromHome
import org.onebusaway.android.ui.nav.revealRouteOnMap
import org.onebusaway.android.ui.nav.revealStopOnMap
import org.onebusaway.android.util.PreferenceUtils

/**
 * The "My*" navigation cluster: the three tabbed list destinations (starred stops / routes / recent)
 * and the standalone saved-trip-reminders list. The tabbed destinations resolve the preferences
 * repository via [PreferencesEntryPoint]; the reminders list hosts its own entry-scoped VM and a sort action.
 */
fun NavGraphBuilder.myListsGraph(navController: NavHostController) {
    // The three "My*" tabbed list destinations. Reached from static app
    // shortcuts + old pinned tab:// shortcuts (the translator maps the tag to the route) and,
    // for Recent, the toolbar overflow. Tab wiring lives in MyListScreens.kt; the per-tab VMs
    // are scoped to the back-stack entry. The legacy CREATE_SHORTCUT picker mode is dropped.
    val tabArg = listOf(
        navArgument(NavRoutes.ARG_TAB) {
            type = NavType.StringType; nullable = true; defaultValue = null
        },
    )
    composable(NavRoutes.MY_STOPS, arguments = tabArg) { entry ->
        ObaTheme {
            MyStopsDestination(
                initialTag = entry.arguments?.getString(NavRoutes.ARG_TAB),
                prefsRepository = PreferencesEntryPoint.get(LocalContext.current),
                onBack = { navController.popBackStack() },
                onShowStopOnMap = { id, lat, lon -> navController.revealStopOnMap(id, lat, lon) },
                onOpenStop = { id, name -> navController.navigateFromHome(NavRoutes.arrivals(id, name)) },
            )
        }
    }
    composable(NavRoutes.MY_ROUTES, arguments = tabArg) { entry ->
        ObaTheme {
            MyRoutesDestination(
                initialTag = entry.arguments?.getString(NavRoutes.ARG_TAB),
                prefsRepository = PreferencesEntryPoint.get(LocalContext.current),
                onBack = { navController.popBackStack() },
                onShowRouteOnMap = { navController.revealRouteOnMap(it) },
                onOpenRoute = { navController.navigateFromHome(NavRoutes.routeInfo(it)) },
            )
        }
    }
    composable(NavRoutes.MY_RECENT, arguments = tabArg) { entry ->
        ObaTheme {
            MyRecentDestination(
                initialTag = entry.arguments?.getString(NavRoutes.ARG_TAB),
                prefsRepository = PreferencesEntryPoint.get(LocalContext.current),
                onBack = { navController.popBackStack() },
                onShowStopOnMap = { id, lat, lon -> navController.revealStopOnMap(id, lat, lon) },
                onShowRouteOnMap = { navController.revealRouteOnMap(it) },
                onOpenStop = { id, name -> navController.navigateFromHome(NavRoutes.arrivals(id, name)) },
            )
        }
    }
    // "My Reminders" destination: the standalone saved-trip-reminders list.
    // A single ReminderListDestination in a Scaffold (not MyTabsScreen) with a sort action.
    // Entry-scoped VM. The home drawer embeds the same destination separately.
    composable(NavRoutes.MY_REMINDERS) {
        val activity = LocalContext.current.findActivity()
        val reminders = rememberListVm("reminders") {
            RemindersRepository(activity.applicationContext)
        }
        ObaTheme {
            Scaffold(
                topBar = {
                    ObaTopAppBar(
                        title = stringResource(R.string.navdrawer_item_my_reminders),
                        onBack = { navController.popBackStack() }
                    ) {
                        IconButton(onClick = {
                            activity.chooseSortOrder(
                                PreferenceUtils.getReminderSortOrderFromPreferences(),
                                R.array.sort_reminders
                            ) { reminders.setSort(it) }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_content_sort),
                                contentDescription = stringResource(R.string.menu_option_sort_by)
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val onEditReminder: (String, String) -> Unit = { tripId, stopId ->
                        navController.navigateFromHome(NavRoutes.tripInfo(tripId, stopId))
                    }
                    ReminderListDestination(
                        reminders,
                        emptyText = R.string.trip_list_notrips,
                        onClick = { editReminder(it, onEditReminder) },
                        actions = {
                            activity.reminderActions(
                                it,
                                onEdit = onEditReminder,
                                onShowRoute = { navController.navigate(NavRoutes.routeInfo(it)) },
                                onShowStop = { navController.navigate(NavRoutes.arrivals(it)) },
                            )
                        }
                    )
                }
            }
        }
    }
}
