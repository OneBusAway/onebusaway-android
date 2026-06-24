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
package org.onebusaway.android.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.MenuHeader
import org.onebusaway.android.ui.compose.components.RouteRowContent

/** The route search tab: search box + results, with a long-press menu per row. */
@Composable
fun RouteSearchContent(
    viewModel: SearchViewModel<RouteSearchResult>,
    onRouteClick: (RouteSearchResult) -> Unit,
    onShowOnMap: (RouteSearchResult) -> Unit,
    onShowSchedule: (RouteSearchResult) -> Unit,
    onCreateShortcut: (RouteSearchResult) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        onQueryChange = viewModel::onQueryChange,
        searchHint = stringResource(R.string.search_route_hint),
        idleHint = stringResource(R.string.find_hint_nofavoriteroutes),
        state = state,
        itemKey = { it.id }
    ) { route ->
        RouteSearchRow(
            route = route,
            onRouteClick = onRouteClick,
            onShowOnMap = onShowOnMap,
            onShowSchedule = onShowSchedule,
            onCreateShortcut = onCreateShortcut
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteSearchRow(
    route: RouteSearchResult,
    onRouteClick: (RouteSearchResult) -> Unit,
    onShowOnMap: (RouteSearchResult) -> Unit,
    onShowSchedule: (RouteSearchResult) -> Unit,
    onCreateShortcut: (RouteSearchResult) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        RouteRowContent(
            shortName = route.shortName,
            longName = route.longName,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onRouteClick(route) },
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            MenuHeader(stringResource(R.string.route_name, route.shortName))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.my_context_get_route_info)) },
                onClick = {
                    menuExpanded = false
                    onRouteClick(route)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.my_context_showonmap)) },
                onClick = {
                    menuExpanded = false
                    onShowOnMap(route)
                }
            )
            if (route.url != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.my_context_show_schedule)) },
                    onClick = {
                        menuExpanded = false
                        onShowSchedule(route)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.my_context_create_shortcut)) },
                onClick = {
                    menuExpanded = false
                    onCreateShortcut(route)
                }
            )
        }
    }
}
