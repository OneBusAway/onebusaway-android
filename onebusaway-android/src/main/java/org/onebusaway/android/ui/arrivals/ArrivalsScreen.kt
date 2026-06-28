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
package org.onebusaway.android.ui.arrivals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.onebusaway.android.R
import org.onebusaway.android.ui.nightlight.NightLightLauncher
import org.onebusaway.android.ui.arrivals.components.ArrivalCardStyleB
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.arrivals.components.ArrivalRowStyleA
import org.onebusaway.android.ui.arrivals.components.MenuRow
import org.onebusaway.android.ui.arrivals.components.groupForStyleB
import org.onebusaway.android.ui.arrivals.dialogs.RouteFavoriteHost
import org.onebusaway.android.ui.arrivals.dialogs.StopDetailsHost
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.ui.compose.components.LoadingContent

/** Refresh interval matching the legacy ArrivalsListFragment (fixed 60s, not the server value). */
private const val REFRESH_PERIOD_MS = 60_000L

/**
 * The lifecycle-scoped 60s polling loop, shared by the standalone screen and the map panel.
 * Runs only while RESUMED (cancelled on pause, like the legacy Handler) and refreshes immediately
 * on resume if the window already elapsed.
 */
@Composable
internal fun ArrivalsPolling(viewModel: ArrivalsViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val sinceLast = System.currentTimeMillis() - viewModel.lastResponseTimeMs
            delay((REFRESH_PERIOD_MS - sinceLast).coerceIn(0L, REFRESH_PERIOD_MS))
            while (isActive) {
                viewModel.refresh()
                delay(REFRESH_PERIOD_MS)
            }
        }
    }
}

/** Builds the per-arrival menu callbacks, bridging ViewModel actions and the host [handler]. */
@Composable
internal fun rememberArrivalRowCallbacks(
    handler: ArrivalActionHandler,
    viewModel: ArrivalsViewModel
): ArrivalRowCallbacks = remember(handler, viewModel) {
    ArrivalRowCallbacks(
        onRouteFavorite = handler::onRouteFavorite,
        onShowVehiclesOnMap = handler::onShowVehiclesOnMap,
        onShowTripStatus = handler::onShowTripStatus,
        onSetReminder = handler::onSetReminder,
        onShowOnlyRoute = viewModel::showOnlyRoute,
        onShowRouteSchedule = handler::onShowRouteSchedule,
        onReportArrivalProblem = handler::onReportArrivalProblem
    )
}

/**
 * Navigation/dialog actions for the arrivals screen, implemented by the host activity (it has the
 * Context the targets need). The route-filter and alert hide/show actions are pure
 * ViewModel operations and so are passed as plain lambdas, not through this handler.
 */
interface ArrivalActionHandler {
    fun onRouteFavorite(actions: ArrivalActions)
    fun onShowVehiclesOnMap(arrival: ArrivalInfo)
    fun onShowTripStatus(arrival: ArrivalInfo)
    fun onSetReminder(arrival: ArrivalInfo)
    fun onShowRouteSchedule(scheduleUrl: String)
    fun onReportArrivalProblem(actions: ArrivalActions)
    fun onShowAlert(alertId: String)
    fun onShowStopDetails()
    fun onReportStopProblem()
}

/**
 * Stateful entry point. The polling loop lives here so it follows the activity lifecycle:
 * polling runs only while RESUMED (cancelled on pause, like the legacy Handler), and refreshes
 * immediately on resume if the 60s window already elapsed.
 *
 * @param initialTitle stop name from the launching intent, shown until the first load lands
 */
@Composable
fun ArrivalsRoute(
    viewModel: ArrivalsViewModel,
    initialTitle: String,
    handler: ArrivalActionHandler,
    onBack: () -> Unit,
    // Provided by the NavHost destination so its alert-hide undo Snackbar has a Compose host (the
    // standalone activity anchors its own Snackbar to a View instead, leaving this null).
    snackbarHostState: SnackbarHostState? = null,
    // How the overflow "night light" item navigates — the in-NavHost destination supplies a
    // NavController lambda; null falls back to the standalone launcher facade.
    onNightLight: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArrivalsPolling(viewModel)
    RouteFavoriteHost(viewModel)
    StopDetailsHost(viewModel)
    val rowCallbacks = rememberArrivalRowCallbacks(handler, viewModel)
    ArrivalsScreen(
        state = state,
        initialTitle = initialTitle,
        onBack = onBack,
        onRefresh = viewModel::manualRefresh,
        onToggleFavorite = viewModel::toggleFavorite,
        onLoadMore = viewModel::loadMore,
        rowCallbacks = rowCallbacks,
        handler = handler,
        onSetRouteFilter = viewModel::setRouteFilter,
        onSetArrivalStyle = viewModel::setArrivalStyle,
        onShowAllRoutes = viewModel::showAllRoutes,
        onHideAllAlerts = viewModel::hideAllAlerts,
        onShowHiddenAlerts = viewModel::showHiddenAlerts,
        snackbarHostState = snackbarHostState,
        onNightLight = onNightLight
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalsScreen(
    state: ArrivalsUiState,
    initialTitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLoadMore: () -> Unit,
    rowCallbacks: ArrivalRowCallbacks,
    handler: ArrivalActionHandler,
    onSetRouteFilter: (Set<String>) -> Unit,
    onSetArrivalStyle: (Int) -> Unit,
    onShowAllRoutes: () -> Unit,
    onHideAllAlerts: () -> Unit,
    onShowHiddenAlerts: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    onNightLight: (() -> Unit)? = null
) {
    val content = state as? ArrivalsUiState.Content
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    Scaffold(
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = { Text(content?.header?.name?.takeIf { it.isNotEmpty() } ?: initialTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                painter = painterResource(
                                    if (content.header.isFavorite) {
                                        R.drawable.ic_toggle_star
                                    } else {
                                        R.drawable.ic_toggle_star_outline
                                    }
                                ),
                                contentDescription = stringResource(R.string.stop_info_favorite),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_navigation_refresh),
                            contentDescription = stringResource(R.string.region_option_refresh),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (content != null) {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_content_sort),
                                contentDescription = stringResource(R.string.menu_option_sort_by),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OverflowMenu(
                            onFilter = { showFilterDialog = true },
                            onStopDetails = handler::onShowStopDetails,
                            onReportStopProblem = handler::onReportStopProblem,
                            onHideAlerts = onHideAllAlerts,
                            onNightLight = onNightLight
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
                ArrivalsUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))

                is ArrivalsUiState.Content -> ArrivalsList(
                    content = state,
                    rowCallbacks = rowCallbacks,
                    handler = handler,
                    onLoadMore = onLoadMore,
                    onShowAllRoutes = onShowAllRoutes,
                    onShowHiddenAlerts = onShowHiddenAlerts
                )

                is ArrivalsUiState.Error -> Text(
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
    if (showFilterDialog && content != null) {
        RouteFilterDialog(
            options = content.routeFilterOptions,
            onDismiss = { showFilterDialog = false },
            onSave = {
                onSetRouteFilter(it)
                showFilterDialog = false
            }
        )
    }
    if (showSortDialog && content != null) {
        SortByDialog(
            selected = content.style,
            onDismiss = { showSortDialog = false },
            onSelect = {
                onSetArrivalStyle(it)
                showSortDialog = false
            }
        )
    }
}

/**
 * The legacy "Sort by" single-choice dialog: it doesn't reorder but switches the display style —
 * "Estimated arrival" (the flat Style A list) vs "Route" (the route-grouped Style B cards).
 * Selecting an option applies it immediately (legacy behavior).
 */
@Composable
private fun SortByDialog(
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = stringArrayResource(R.array.sort_arrivals)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_option_sort_by)) },
        text = { RadioOptionList(options = options, selectedIndex = selected, onSelect = onSelect) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/** A single-choice radio list (one row per option), shared by the sort and route-favorite dialogs. */
@Composable
internal fun RadioOptionList(
    options: Array<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        options.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) })
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun OverflowMenu(
    onFilter: () -> Unit,
    onStopDetails: () -> Unit,
    onReportStopProblem: () -> Unit,
    onHideAlerts: () -> Unit,
    // In-NavHost hosts supply a NavController lambda; null falls back to the standalone launcher facade.
    onNightLight: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_navigation_more_vert),
                contentDescription = stringResource(R.string.stop_info_item_options_title),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuRow(R.string.stop_info_option_filter) { expanded = false; onFilter() }
            MenuRow(R.string.stop_info_option_show_details) { expanded = false; onStopDetails() }
            MenuRow(R.string.stop_info_option_report_problem) { expanded = false; onReportStopProblem() }
            MenuRow(R.string.stop_info_option_hide_alerts) { expanded = false; onHideAlerts() }
            MenuRow(R.string.stop_info_option_night_light) {
                expanded = false
                onNightLight?.invoke() ?: NightLightLauncher.start(context)
            }
        }
    }
}

@Composable
internal fun ArrivalsList(
    content: ArrivalsUiState.Content,
    rowCallbacks: ArrivalRowCallbacks,
    handler: ArrivalActionHandler,
    onLoadMore: () -> Unit,
    onShowAllRoutes: () -> Unit,
    onShowHiddenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    /** Hosts that show the stop's direction elsewhere (e.g. in their own header) set this false to
     *  avoid duplicating it as a list item. */
    showDirection: Boolean = true,
    /** Inset for the scrollable content (e.g. a top inset so the list clears the collapsed peek fold). */
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Original indexes into [content.arrivals] to omit from the Style-A list (the home sheet hoists
     *  these to the top as morphing peek rows via [leadingContent], so listing them again would
     *  duplicate them). */
    excludedArrivalIndexes: Set<Int> = emptySet(),
    /** Extra items emitted at the very top of the list (above alerts), e.g. the home sheet's morphing
     *  peek rows so the whole drawer is one scrollable list under a pinned header. */
    leadingContent: (LazyListScope.() -> Unit)? = null,
    /** When false, only [leadingContent] is emitted — the rest of the list stays out of composition so
     *  it can't peek through the collapsed sheet fold. The home sheet drives this from the drawer's open
     *  fraction (full list once the drawer leaves its resting peek). */
    showFullList: Boolean = true
) {
    val useCards = content.style == BuildFlavorUtils.ARRIVAL_INFO_STYLE_B
    // Sorting/grouping is non-trivial; keep it off the recomposition path
    val groups = remember(content.arrivals, useCards) {
        if (useCards) groupForStyleB(content.arrivals) else emptyList()
    }
    // The Style-A rows minus the pinned peek rows, paired with their original index so keys stay
    // stable (tripId alone isn't unique — see below).
    val visibleArrivals = remember(content.arrivals, excludedArrivalIndexes) {
        content.arrivals.withIndex().filterNot { it.index in excludedArrivalIndexes }
    }
    val filterActive = content.filteredRouteCount > 0
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        leadingContent?.invoke(this)
        // Until the drawer leaves its peek, emit only the leading peek rows; the rest stays out of
        // composition so it can't peek through the collapsed sheet fold, then reveals with the drag.
        if (showFullList) {
            items(content.alerts, key = { "alert:${it.id}" }) { alert ->
                AlertRow(alert) { handler.onShowAlert(alert.id) }
            }
            if (content.hiddenAlertCount > 0) {
                item(key = "hidden_alerts") {
                    TextButton(
                        onClick = onShowHiddenAlerts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.alert_filter_text,
                                content.hiddenAlertCount,
                                content.hiddenAlertCount
                            )
                        )
                    }
                }
            }
            if (content.filteredRouteCount in 1 until content.header.routeCount) {
                item(key = "filter_indicator") {
                    FilterIndicator(content.filteredRouteCount, content.header.routeCount, onShowAllRoutes)
                }
            }
            if (showDirection) {
                content.header.direction?.let { direction ->
                    item(key = "direction") { DirectionLine(direction) }
                }
            }
            if (content.arrivals.isEmpty()) {
                item(key = "empty") { EmptyArrivals(content.minutesAfter) }
            } else if (useCards) {
                items(groups, key = { it.first().info.run { "$routeId:$headsign" } }) { group ->
                    ArrivalCardStyleB(
                        group = group,
                        actions = content.actions[group.first().info.tripId],
                        filterActive = filterActive,
                        callbacks = rowCallbacks
                    )
                }
            } else {
                // tripId alone isn't unique (the same trip can appear twice, e.g. frequency-based
                // service), so disambiguate the key by original index to satisfy LazyColumn's
                // unique-key rule.
                items(
                    visibleArrivals,
                    key = { (index, arrival) -> "${arrival.info.tripId}#$index" }
                ) { (_, arrival) ->
                    ArrivalRowStyleA(
                        arrival = arrival,
                        actions = content.actions[arrival.info.tripId],
                        filterActive = filterActive,
                        callbacks = rowCallbacks
                    )
                }
            }
            item(key = "load_more") {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.stop_info_load_more_arrivals))
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertItem, onClick: () -> Unit) {
    val (container, onContainer) = when (alert.severity) {
        AlertSeverity.ERROR ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AlertSeverity.WARNING ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        AlertSeverity.INFO ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.baseline_warning_24),
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Text(text = alert.summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FilterIndicator(shown: Int, total: Int, onShowAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.stop_info_filter_header, shown, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onShowAll) {
            Text(stringResource(R.string.bus_options_menu_show_all_routes))
        }
    }
}

@Composable
internal fun RouteFilterDialog(
    options: List<RouteFilterOption>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val checked = remember(options) {
        mutableStateListOf<Boolean>().apply { addAll(options.map { it.checked }) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stop_info_filter_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked[index] = !checked[index] }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                        Text(option.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selected = options
                    .filterIndexed { index, _ -> checked[index] }
                    .map { it.routeId }
                    .toSet()
                onSave(collapseRouteFilter(selected, options.size))
            }) {
                Text(stringResource(R.string.stop_info_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.stop_info_cancel)) }
        }
    )
}

@Composable
private fun DirectionLine(direction: String) {
    val directionText = stringResource(DisplayFormat.getStopDirectionText(direction))
    if (directionText.isNotEmpty()) {
        Text(
            text = directionText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EmptyArrivals(minutesAfter: Int) {
    val context = LocalContext.current
    Text(
        text = DisplayFormat.getNoArrivalsMessage(context, minutesAfter, false, false),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    )
}
