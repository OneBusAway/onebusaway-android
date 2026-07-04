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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.mylists.RouteListDestination
import org.onebusaway.android.ui.mylists.StarredRoutesRepository
import org.onebusaway.android.ui.mylists.StarredStopsRepository
import org.onebusaway.android.ui.mylists.StopListDestination
import org.onebusaway.android.ui.mylists.chooseSortOrder
import org.onebusaway.android.ui.mylists.confirmClear
import org.onebusaway.android.ui.mylists.openRoute
import org.onebusaway.android.ui.mylists.rememberListVm
import org.onebusaway.android.ui.mylists.routeActions
import org.onebusaway.android.ui.mylists.stopActions
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.revealRouteOnMap
import org.onebusaway.android.ui.nav.revealStopOnMap
import org.onebusaway.android.util.PreferenceUtils

/**
 * The home drawer's two starred-list destinations: the former in-place "tabs" promoted to real NavHost
 * destinations (back arrow + sort/clear), reusing the shared My* list bodies/VMs/repos. They're distinct
 * from the multi-tab `MY_STOPS`/`MY_ROUTES`; the reminders row reuses [NavRoutes.MY_REMINDERS] directly
 * (a single-list reminder destination already).
 */
fun NavGraphBuilder.homeListsGraph(navController: NavHostController) {
    composable(NavRoutes.HOME_STARRED_STOPS) {
        val host = LocalContext.current.findActivity()
        val vm = rememberListVm("home.starredStops") { StarredStopsRepository(host.applicationContext) }
        StarredListScaffold(
            title = R.string.navdrawer_item_starred_stops,
            clearLabel = R.string.my_option_clear_starred_stops,
            onBack = { navController.popBackStack() },
            onSort = {
                host.chooseSortOrder(
                    PreferenceUtils.getStopSortOrderFromPreferences(host), R.array.sort_stops
                ) { vm.setSort(it) }
            },
            onClear = {
                host.confirmClear(
                    R.string.my_option_clear_starred_stops_title,
                    R.string.my_option_clear_starred_stops_confirm,
                ) { vm.clearAll() }
            },
        ) {
            StopListDestination(
                vm,
                emptyText = R.string.my_no_starred_stops,
                onClick = { navController.navigate(NavRoutes.arrivals(it.id, it.name)) },
                actions = {
                    host.stopActions(
                        it, R.string.my_context_remove_star,
                        onShowOnMap = { id, lat, lon -> navController.revealStopOnMap(id, lat, lon) },
                    ) { vm.remove(it.id) }
                },
            )
        }
    }
    composable(NavRoutes.HOME_STARRED_ROUTES) {
        val host = LocalContext.current.findActivity()
        val vm = rememberListVm("home.starredRoutes") { StarredRoutesRepository(host.applicationContext) }
        StarredListScaffold(
            title = R.string.navdrawer_item_starred_routes,
            clearLabel = R.string.my_option_clear_starred_routes,
            onBack = { navController.popBackStack() },
            onSort = {
                host.chooseSortOrder(
                    PreferenceUtils.getStopSortOrderFromPreferences(host), R.array.sort_stops
                ) { vm.setSort(it) }
            },
            onClear = {
                host.confirmClear(
                    R.string.my_option_clear_starred_routes_title,
                    R.string.my_option_clear_starred_routes_confirm,
                ) { vm.clearAll() }
            },
        ) {
            RouteListDestination(
                vm,
                emptyText = R.string.my_no_starred_routes,
                onClick = { route -> openRoute(route) { routeId -> navController.revealRouteOnMap(routeId) } },
                actions = {
                    host.routeActions(
                        it, R.string.my_context_remove_star,
                        onShowOnMap = { routeId -> navController.revealRouteOnMap(routeId) },
                    ) { vm.remove(it.id) }
                },
            )
        }
    }
}

/**
 * The shared chrome for a home starred-list destination: a [Scaffold] with a back arrow + sort + clear
 * top bar over the [body] list. The two starred lists differ only in their strings, VM/repo, and list
 * body — all supplied by the caller.
 */
@Composable
private fun StarredListScaffold(
    @StringRes title: Int,
    @StringRes clearLabel: Int,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onClear: () -> Unit,
    body: @Composable () -> Unit,
) {
    ObaTheme {
        Scaffold(
            topBar = {
                ObaTopAppBar(title = stringResource(title), onBack = onBack) {
                    IconButton(onClick = onSort) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_content_sort),
                            contentDescription = stringResource(R.string.menu_option_sort_by),
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_delete_forever_48),
                            contentDescription = stringResource(clearLabel),
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { body() }
        }
    }
}
