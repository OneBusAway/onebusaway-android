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
package org.onebusaway.android.ui.routeinfo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.MenuHeader
import org.onebusaway.android.ui.compose.components.StopRowContent
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Stateful entry point: collects the ViewModel's state and wires UI events to the host. */
@Composable
fun RouteInfoRoute(
    viewModel: RouteInfoViewModel,
    onBack: () -> Unit,
    onShowRouteOnMap: () -> Unit,
    onStopClick: (RouteStopItem) -> Unit,
    onStopShowOnMap: (RouteStopItem) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RouteInfoScreen(
        state = state,
        onBack = onBack,
        onShowRouteOnMap = onShowRouteOnMap,
        onStopClick = onStopClick,
        onStopShowOnMap = onStopShowOnMap
    )
}

/** Stateless screen content, fully driven by [RouteInfoUiState] — previewable and testable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteInfoScreen(
    state: RouteInfoUiState,
    onBack: () -> Unit,
    onShowRouteOnMap: () -> Unit,
    onStopClick: (RouteStopItem) -> Unit,
    onStopShowOnMap: (RouteStopItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShowRouteOnMap) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_location_map),
                            contentDescription = stringResource(R.string.stop_info_option_showonmap),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                RouteInfoUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))

                is RouteInfoUiState.Success -> RouteInfoContent(
                    route = state.route,
                    onStopClick = onStopClick,
                    onStopShowOnMap = onStopShowOnMap
                )

                is RouteInfoUiState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            }
        }
    }
}

@Composable
private fun RouteInfoContent(
    route: RouteInfo,
    onStopClick: (RouteStopItem) -> Unit,
    onStopShowOnMap: (RouteStopItem) -> Unit
) {
    // Direction names are unique within a route, so they key the expand/collapse state
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(Modifier.fillMaxSize()) {
        if (route.longName != null || route.agencyName != null) {
            item(key = "header") { RouteHeader(route) }
        }
        route.directions.forEach { direction ->
            val isExpanded = expanded[direction.name] == true
            item(key = "group:${direction.name}") {
                DirectionHeader(
                    name = direction.name,
                    expanded = isExpanded,
                    onClick = { expanded[direction.name] = !isExpanded }
                )
            }
            if (isExpanded) {
                items(direction.stops, key = { "${direction.name}:${it.id}" }) { stop ->
                    StopRow(stop, onStopClick, onStopShowOnMap)
                }
            }
        }
    }
}

@Composable
private fun RouteHeader(route: RouteInfo) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineBadge(route.shortName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (route.longName != null) {
                Text(route.longName, style = MaterialTheme.typography.titleMedium)
            }
            if (route.agencyName != null) {
                Text(
                    text = route.agencyName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteHeaderPreview() {
    ObaTheme {
        RouteHeader(
            RouteInfo(
                id = "1_8",
                shortName = "8",
                longName = "Seattle Center - Capitol Hill - Rainier Beach",
                agencyName = "Metro Transit",
                url = null,
                directions = emptyList()
            )
        )
    }
}

@Composable
private fun DirectionHeader(name: String, expanded: Boolean, onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DirectionHeaderPreview() {
    ObaTheme {
        Column {
            DirectionHeader(name = "Southbound", expanded = true, onClick = {})
            DirectionHeader(name = "Northbound", expanded = false, onClick = {})
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopRow(
    stop: RouteStopItem,
    onStopClick: (RouteStopItem) -> Unit,
    onStopShowOnMap: (RouteStopItem) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        // Route info stops are never favorites here, and the start padding indents them under
        // their direction group
        StopRowContent(
            name = stop.name,
            direction = stop.direction,
            isFavorite = false,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onStopClick(stop) },
                    onLongClick = { menuExpanded = true }
                )
                .padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            MenuHeader(stop.name)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.route_info_context_get_stop_info)) },
                onClick = {
                    menuExpanded = false
                    onStopClick(stop)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.route_info_context_showonmap)) },
                onClick = {
                    menuExpanded = false
                    onStopShowOnMap(stop)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StopRowPreview() {
    ObaTheme {
        Column {
            StopRow(
                stop = RouteStopItem("1", "Broadway & E Denny Way", "S", 47.6, -122.3),
                onStopClick = {},
                onStopShowOnMap = {}
            )
            StopRow(
                stop = RouteStopItem("2", "Stop with no direction", "", 47.6, -122.3),
                onStopClick = {},
                onStopShowOnMap = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteInfoScreenSuccessPreview() {
    ObaTheme {
        RouteInfoScreen(
            state = RouteInfoUiState.Success(
                RouteInfo(
                    id = "1_8",
                    shortName = "8",
                    longName = "Seattle Center - Capitol Hill - Rainier Beach",
                    agencyName = "Metro Transit",
                    url = "https://example.org",
                    directions = listOf(
                        RouteDirection(
                            "Southbound",
                            listOf(
                                RouteStopItem("1", "Denny Way & Fairview Ave N", "S", 47.6, -122.3),
                                RouteStopItem("2", "Broadway & E Denny Way", "S", 47.6, -122.3)
                            )
                        ),
                        RouteDirection("Northbound", emptyList())
                    )
                )
            ),
            onBack = {}, onShowRouteOnMap = {}, onStopClick = {}, onStopShowOnMap = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteInfoScreenLoadingPreview() {
    ObaTheme {
        RouteInfoScreen(
            state = RouteInfoUiState.Loading,
            onBack = {}, onShowRouteOnMap = {}, onStopClick = {}, onStopShowOnMap = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteInfoScreenErrorPreview() {
    ObaTheme {
        RouteInfoScreen(
            state = RouteInfoUiState.Error("Please check your Internet connection and try again."),
            onBack = {}, onShowRouteOnMap = {}, onStopClick = {}, onStopShowOnMap = {}
        )
    }
}
