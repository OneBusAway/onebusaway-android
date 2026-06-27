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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.search.RouteSearchContent
import org.onebusaway.android.ui.search.RouteSearchResult
import org.onebusaway.android.ui.search.SearchViewModel
import org.onebusaway.android.ui.search.StopSearchContent
import org.onebusaway.android.ui.common.Shortcuts
import org.onebusaway.android.ui.search.StopSearchResult
import org.onebusaway.android.util.ExternalIntents

/**
 * The shared list/search "destinations": body composables hosted by both the Compose [MyTabsScreen]
 * (the `My*` tab activities) and the Compose home overlays ([org.onebusaway.android.ui.home]). The
 * three list destinations are stateless — callers supply the per-row `onClick` and `actions` (each
 * caller wires them to the `AppCompatActivity` nav/action helpers with its own strings). The route
 * search destination still resolves the host via [findActivity] for its schedule/shortcut actions.
 */

@Composable
fun StopListDestination(
    viewModel: MyListViewModel<StopListItem>,
    @StringRes emptyText: Int,
    onClick: (StopListItem) -> Unit,
    actions: (StopListItem) -> List<RowAction>,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyListContent(state, emptyText = stringResource(emptyText), itemKey = { it.id }) { stop ->
        StopRow(stop, onClick = { onClick(stop) }, actions = actions(stop))
    }
}

@Composable
fun RouteListDestination(
    viewModel: MyListViewModel<RouteListItem>,
    @StringRes emptyText: Int,
    onClick: (RouteListItem) -> Unit,
    actions: (RouteListItem) -> List<RowAction>,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyListContent(state, emptyText = stringResource(emptyText), itemKey = { it.id }) { route ->
        RouteRow(route, onClick = { onClick(route) }, actions = actions(route))
    }
}

@Composable
fun ReminderListDestination(
    viewModel: MyListViewModel<ReminderItem>,
    @StringRes emptyText: Int,
    onClick: (ReminderItem) -> Unit,
    actions: (ReminderItem) -> List<RowAction>,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyListContent(
        state,
        emptyText = stringResource(emptyText),
        itemKey = { "${it.tripId}:${it.stopId}" }
    ) { reminder ->
        ReminderRow(reminder, onClick = { onClick(reminder) }, actions = actions(reminder))
    }
}

@Composable
fun StopSearchDestination(
    viewModel: SearchViewModel<StopSearchResult>,
    onShowOnMap: (stopId: String, lat: Double, lon: Double) -> Unit,
    onOpenStop: (stopId: String, stopName: String?) -> Unit,
) {
    StopSearchContent(
        viewModel = viewModel,
        onStopClick = { openStopSearchResult(it, onOpenStop) },
        onShowOnMap = { onShowOnMap(it.id, it.latitude, it.longitude) }
    )
}

@Composable
fun RouteSearchDestination(
    viewModel: SearchViewModel<RouteSearchResult>,
    onShowOnMap: (routeId: String) -> Unit,
    onOpenRoute: (routeId: String) -> Unit,
) {
    val host = LocalContext.current.findActivity()
    RouteSearchContent(
        viewModel = viewModel,
        onRouteClick = { openRouteSearchResult(it, onOpenRoute) },
        onShowOnMap = { onShowOnMap(it.id) },
        onShowSchedule = { route -> route.url?.let { ExternalIntents.goToUrl(host, it) } },
        onCreateShortcut = { Shortcuts.createRouteShortcut(host, it.id, it.shortName) }
    )
}
