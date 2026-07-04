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
package org.onebusaway.android.ui.mylists

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.onebusaway.android.R
import org.onebusaway.android.app.di.NetworkEntryPoint
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.search.DefaultRouteSearchRepository
import org.onebusaway.android.ui.search.DefaultStopSearchRepository
import org.onebusaway.android.ui.search.RouteSearchResult
import org.onebusaway.android.ui.search.SearchViewModel
import org.onebusaway.android.ui.search.StopSearchResult
import org.onebusaway.android.util.PreferenceUtils

/**
 * The three `My*` tabbed list NavHost destinations (former MyStops/MyRoutes/
 * MyRecentStopsAndRoutes activities), plus the per-tab builders they share. Each destination is a
 * thin [HomeActivity][org.onebusaway.android.ui.HomeActivity] `composable {}`; the recent-stops and
 * recent-routes tabs appear in two destinations each, so the tab wiring (list + row actions + clear/
 * sort) lives once here as [AppCompatActivity] extensions and is composed per screen. The legacy
 * launcher-shortcut picker is gone, so every list uses the in-app navigation path.
 */

internal fun AppCompatActivity.recentStopsTab(
    viewModel: MyListViewModel<StopListItem>,
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int,
    onShowOnMap: (stopId: String, lat: Double, lon: Double) -> Unit,
    onOpenStop: (stopId: String, stopName: String?) -> Unit,
): MyTab = MyTab(
    tag = MyTabs.RECENT_STOPS,
    titleRes = titleRes,
    iconRes = iconRes,
    clear = TabAction(R.string.my_option_clear_recent_stops) {
        confirmClear(
            R.string.my_option_clear_recent_stops_title,
            R.string.my_option_clear_recent_stops_confirm,
        ) { viewModel.clearAll() }
    },
) {
    StopListDestination(
        viewModel,
        emptyText = R.string.my_no_recent_stops,
        onClick = { openStop(it, onOpenStop) },
        actions = {
            stopActions(it, R.string.my_context_remove_recent, onShowOnMap) {
                viewModel.remove(it.id)
            }
        },
    )
}

internal fun AppCompatActivity.starredStopsTab(
    viewModel: MyListViewModel<StopListItem>,
    onShowOnMap: (stopId: String, lat: Double, lon: Double) -> Unit,
    onOpenStop: (stopId: String, stopName: String?) -> Unit,
): MyTab = MyTab(
    tag = MyTabs.STARRED_STOPS,
    titleRes = R.string.my_starred_title,
    iconRes = R.drawable.ic_tab_starred_unselected,
    onSort = {
        chooseSortOrder(
            PreferenceUtils.getStopSortOrderFromPreferences(this@starredStopsTab),
            R.array.sort_stops,
        ) { viewModel.setSort(it) }
    },
    clear = TabAction(R.string.my_option_clear_starred_stops) {
        confirmClear(
            R.string.my_option_clear_starred_stops_title,
            R.string.my_option_clear_starred_stops_confirm,
        ) { viewModel.clearAll() }
    },
) {
    StopListDestination(
        viewModel,
        emptyText = R.string.my_no_starred_stops,
        onClick = { openStop(it, onOpenStop) },
        actions = {
            stopActions(it, R.string.my_context_remove_star, onShowOnMap) {
                viewModel.remove(it.id)
            }
        },
    )
}

internal fun AppCompatActivity.recentRoutesTab(
    viewModel: MyListViewModel<RouteListItem>,
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int,
    onShowOnMap: (routeId: String) -> Unit,
): MyTab = MyTab(
    tag = MyTabs.RECENT_ROUTES,
    titleRes = titleRes,
    iconRes = iconRes,
    clear = TabAction(R.string.my_option_clear_recent_routes) {
        confirmClear(
            R.string.my_option_clear_recent_routes_title,
            R.string.my_option_clear_recent_routes_confirm,
        ) { viewModel.clearAll() }
    },
) {
    RouteListDestination(
        viewModel,
        emptyText = R.string.my_no_recent_routes,
        onClick = { openRoute(it, onShowOnMap) },
        actions = {
            routeActions(it, R.string.my_context_remove_recent, onShowOnMap) {
                viewModel.remove(it.id)
            }
        },
    )
}

internal fun stopSearchTab(
    viewModel: SearchViewModel<StopSearchResult>,
    onShowOnMap: (stopId: String, lat: Double, lon: Double) -> Unit,
    onOpenStop: (stopId: String, stopName: String?) -> Unit,
): MyTab = MyTab(
    tag = MyTabs.SEARCH,
    titleRes = R.string.my_search_title,
    iconRes = R.drawable.ic_tab_search_unselected,
) {
    StopSearchDestination(viewModel, onShowOnMap, onOpenStop)
}

internal fun routeSearchTab(
    viewModel: SearchViewModel<RouteSearchResult>,
    onShowOnMap: (routeId: String) -> Unit,
    onOpenRoute: (routeId: String) -> Unit,
): MyTab = MyTab(
    tag = MyTabs.SEARCH,
    titleRes = R.string.my_search_title,
    iconRes = R.drawable.ic_tab_search_unselected,
) {
    RouteSearchDestination(viewModel, onShowOnMap, onOpenRoute)
}

/** [MyTabsScreen] with the last-viewed-tab persistence seam wired to [prefsRepository]. */
@Composable
private fun PersistedTabsScreen(
    @StringRes titleRes: Int,
    lastTabKey: String,
    initialTag: String?,
    prefsRepository: PreferencesRepository,
    onBack: () -> Unit,
    tabs: List<MyTab>,
) {
    MyTabsScreen(
        titleRes = titleRes,
        initialTag = initialTag,
        persistedTag = prefsRepository.getString(lastTabKey, null),
        onPersistTag = { prefsRepository.setString(lastTabKey, it) },
        onBack = onBack,
        tabs = tabs,
    )
}

/** Recent / Starred / Search stop tabs. Reached from static app shortcuts + old pinned `tab://`. */
@Composable
fun MyStopsDestination(
    initialTag: String?,
    prefsRepository: PreferencesRepository,
    onBack: () -> Unit,
    onShowStopOnMap: (stopId: String, lat: Double, lon: Double) -> Unit,
    onOpenStop: (stopId: String, stopName: String?) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val app = activity.applicationContext
    val recent = rememberListVm("stops.recent") { RecentStopsRepository(app) }
    val starred = rememberListVm("stops.starred") { StarredStopsRepository(app) }
    val search = rememberSearchVm("stops.search") {
        DefaultStopSearchRepository(app, NetworkEntryPoint.getLocationSearch(app))::search
    }
    PersistedTabsScreen(
        titleRes = R.string.my_recent_stops,
        lastTabKey = "MyStopsActivity.LastTab",
        initialTag = initialTag,
        prefsRepository = prefsRepository,
        onBack = onBack,
        tabs = listOf(
            activity.recentStopsTab(
                recent, R.string.my_recent_title, R.drawable.ic_tab_recent_unselected,
                onShowStopOnMap, onOpenStop,
            ),
            activity.starredStopsTab(starred, onShowStopOnMap, onOpenStop),
            stopSearchTab(search, onShowStopOnMap, onOpenStop),
        ),
    )
}

/** Recent / Search route tabs. Reached from the static "Recent routes" shortcut + old pinned `tab://`. */
@Composable
fun MyRoutesDestination(
    initialTag: String?,
    prefsRepository: PreferencesRepository,
    onBack: () -> Unit,
    onShowRouteOnMap: (routeId: String) -> Unit,
    onOpenRoute: (routeId: String) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val app = activity.applicationContext
    val recent = rememberListVm("routes.recent") { RecentRoutesRepository(app) }
    val search = rememberSearchVm("routes.search") {
        DefaultRouteSearchRepository(app, NetworkEntryPoint.getLocationSearch(app))::search
    }
    PersistedTabsScreen(
        titleRes = R.string.my_recent_routes,
        lastTabKey = "MyRoutesActivity.LastTab",
        initialTag = initialTag,
        prefsRepository = prefsRepository,
        onBack = onBack,
        tabs = listOf(
            activity.recentRoutesTab(
                recent, R.string.my_recent_title, R.drawable.ic_tab_recent_unselected, onShowRouteOnMap,
            ),
            routeSearchTab(search, onShowRouteOnMap, onOpenRoute),
        ),
    )
}

/** Recent Stops / Recent Routes tabs — the toolbar "Recent" overflow item + the static shortcut. */
@Composable
fun MyRecentDestination(
    initialTag: String?,
    prefsRepository: PreferencesRepository,
    onBack: () -> Unit,
    onShowStopOnMap: (stopId: String, lat: Double, lon: Double) -> Unit,
    onShowRouteOnMap: (routeId: String) -> Unit,
    onOpenStop: (stopId: String, stopName: String?) -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val app = activity.applicationContext
    val recentStops = rememberListVm("recents.stops") { RecentStopsRepository(app) }
    val recentRoutes = rememberListVm("recents.routes") { RecentRoutesRepository(app) }
    PersistedTabsScreen(
        titleRes = R.string.my_recent_title,
        lastTabKey = "RecentRoutesStopsActivity.LastTab",
        initialTag = initialTag,
        prefsRepository = prefsRepository,
        onBack = onBack,
        tabs = listOf(
            activity.recentStopsTab(
                recentStops, R.string.my_recent_stops, R.drawable.ic_menu_stop,
                onShowStopOnMap, onOpenStop,
            ),
            activity.recentRoutesTab(
                recentRoutes, R.string.my_recent_routes, R.drawable.ic_bus, onShowRouteOnMap,
            ),
        ),
    )
}
