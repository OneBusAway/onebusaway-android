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
package org.onebusaway.android.ui.searchresults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.ListUiState
import org.onebusaway.android.ui.compose.components.ListScreenScaffold
import org.onebusaway.android.ui.compose.components.MenuHeader
import org.onebusaway.android.ui.compose.components.RouteRowContent
import org.onebusaway.android.ui.compose.components.StopRowContent
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * Stateful entry point: collects the ViewModel's query (title) and results, and forwards each
 * result's chosen action to the host.
 */
@Composable
fun SearchResultsRoute(
    viewModel: SearchResultsViewModel,
    onBack: () -> Unit,
    onRouteListStops: (SearchResultItem.Route) -> Unit,
    onRouteShowOnMap: (SearchResultItem.Route) -> Unit,
    onStopArrivals: (SearchResultItem.Stop) -> Unit,
    onStopShowOnMap: (SearchResultItem.Stop) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchResultsScreen(
        title = stringResource(R.string.app_name),
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onRouteListStops = onRouteListStops,
        onRouteShowOnMap = onRouteShowOnMap,
        onStopArrivals = onStopArrivals,
        onStopShowOnMap = onStopShowOnMap
    )
}

/** Stateless screen content, fully driven by [ListUiState] — previewable and testable. */
@Composable
fun SearchResultsScreen(
    title: String,
    state: ListUiState<SearchResultItem>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onRouteListStops: (SearchResultItem.Route) -> Unit,
    onRouteShowOnMap: (SearchResultItem.Route) -> Unit,
    onStopArrivals: (SearchResultItem.Stop) -> Unit,
    onStopShowOnMap: (SearchResultItem.Stop) -> Unit
) {
    ListScreenScaffold(
        title = title,
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        // Route and stop ids share no namespace, so prefix to keep keys unique
        itemKey = { item ->
            when (item) {
                is SearchResultItem.Route -> "r:${item.id}"
                is SearchResultItem.Stop -> "s:${item.id}"
            }
        },
        emptyContent = {
            Text(
                text = stringResource(R.string.find_hint_noresults),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        }
    ) { item ->
        when (item) {
            is SearchResultItem.Route -> RouteResultRow(item, onRouteListStops, onRouteShowOnMap)
            is SearchResultItem.Stop -> StopResultRow(item, onStopArrivals, onStopShowOnMap)
        }
    }
}

@Composable
private fun RouteResultRow(
    route: SearchResultItem.Route,
    onListStops: (SearchResultItem.Route) -> Unit,
    onShowOnMap: (SearchResultItem.Route) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val options = stringArrayResource(R.array.search_route_options)
    Column {
        Box {
            RouteRowContent(
                shortName = route.shortName,
                longName = route.longName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                MenuHeader(route.longName ?: route.shortName)
                DropdownMenuItem(
                    text = { Text(options[0]) },
                    onClick = { menuExpanded = false; onListStops(route) }
                )
                DropdownMenuItem(
                    text = { Text(options[1]) },
                    onClick = { menuExpanded = false; onShowOnMap(route) }
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun StopResultRow(
    stop: SearchResultItem.Stop,
    onArrivals: (SearchResultItem.Stop) -> Unit,
    onShowOnMap: (SearchResultItem.Stop) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val options = stringArrayResource(R.array.search_stop_options)
    Column {
        Box {
            StopRowContent(
                name = stop.name,
                direction = stop.direction,
                isFavorite = stop.isFavorite,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                MenuHeader(stop.name)
                DropdownMenuItem(
                    text = { Text(options[0]) },
                    onClick = { menuExpanded = false; onArrivals(stop) }
                )
                DropdownMenuItem(
                    text = { Text(options[1]) },
                    onClick = { menuExpanded = false; onShowOnMap(stop) }
                )
            }
        }
        HorizontalDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultsScreenSuccessPreview() {
    ObaTheme {
        SearchResultsScreen(
            title = "8",
            state = ListUiState.Success(
                listOf(
                    SearchResultItem.Route("1_8", "8", "Seattle Center - Rainier Beach", null),
                    SearchResultItem.Stop("1_100", "Broadway & E Denny Way", "S", true, 47.6, -122.3),
                    SearchResultItem.Stop("1_101", "Stop with no direction", "", false, 47.6, -122.3)
                )
            ),
            onRetry = {}, onBack = {},
            onRouteListStops = {}, onRouteShowOnMap = {}, onStopArrivals = {}, onStopShowOnMap = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultsScreenEmptyPreview() {
    ObaTheme {
        SearchResultsScreen(
            title = "zzzz",
            state = ListUiState.Success(emptyList()),
            onRetry = {}, onBack = {},
            onRouteListStops = {}, onRouteShowOnMap = {}, onStopArrivals = {}, onStopShowOnMap = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultsScreenLoadingPreview() {
    ObaTheme {
        SearchResultsScreen(
            title = "8",
            state = ListUiState.Loading,
            onRetry = {}, onBack = {},
            onRouteListStops = {}, onRouteShowOnMap = {}, onStopArrivals = {}, onStopShowOnMap = {}
        )
    }
}
