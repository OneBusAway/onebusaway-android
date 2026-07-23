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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import org.onebusaway.android.R
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.arrivals.components.MenuRow
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.dialogs.StopDetailsHost
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.nightlight.NightLightLauncher
import org.onebusaway.android.util.DisplayFormat

/** Refresh interval matching the legacy ArrivalsListFragment (fixed 60s, not the server value). */
private const val REFRESH_PERIOD_MS = 60_000L

/** How many service alerts the alert list shows before the "show more" link, and the page size each
 *  tap reveals — keeps a busy alert feed from crowding out the arrivals. */
private const val ALERT_PAGE_SIZE = 3

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
            val sinceLast = (WallTime.now() - viewModel.lastResponseTime).inWholeMilliseconds
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
        onShowRouteOnMap = handler::onShowRouteOnMap,
        onEtaClick = handler::onFocusVehicleOnMap,
        onShowTripStatus = handler::onShowTripStatus,
        onSetReminder = handler::onSetReminder,
        onShowRouteSchedule = handler::onShowRouteSchedule,
        onReportArrivalProblem = handler::onReportArrivalProblem,
        onShowAlert = handler::onShowAlert
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

    /** The badge long-press "Show route on map": frame the whole route as if searched from the search
     *  bar — no stop/direction scoping, unlike the stop-scoped [onShowVehiclesOnMap] row-body tap. */
    fun onShowRouteOnMap(arrival: ArrivalInfo)

    /** The ETA-pill tap: frame the arrival's live vehicle with its stop, or toast if none is tracked. */
    fun onFocusVehicleOnMap(arrival: ArrivalInfo)
    fun onShowTripStatus(arrival: ArrivalInfo)
    fun onSetReminder(arrival: ArrivalInfo)
    fun onShowRouteSchedule(scheduleUrl: String)
    fun onReportArrivalProblem(actions: ArrivalActions)
    fun onShowAlert(alertId: String)
    fun onHideAlert(alert: AlertItem)
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
    StopDetailsHost(viewModel)
    val rowCallbacks = rememberArrivalRowCallbacks(handler, viewModel)
    ArrivalsScreen(
        state = state,
        initialTitle = initialTitle,
        onBack = onBack,
        onRefresh = viewModel::manualRefresh,
        onToggleFavorite = viewModel::toggleFavorite,
        rowCallbacks = rowCallbacks,
        handler = handler,
        onHideAllAlerts = viewModel::hideAllAlerts,
        onShowHiddenAlerts = viewModel::showHiddenAlerts,
        onLoadMore = viewModel::loadMore,
        // Collected inside the list's footer item, not here — a load-more toggle should only
        // recompose that one item, not this whole screen (and everything ArrivalsList contains).
        loadingMore = viewModel.loadingMore,
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
    rowCallbacks: ArrivalRowCallbacks,
    handler: ArrivalActionHandler,
    onHideAllAlerts: () -> Unit,
    onShowHiddenAlerts: () -> Unit,
    onLoadMore: () -> Unit,
    // A StateFlow, not a collected Boolean: the list's footer item collects it itself, so a
    // load-more toggle only recomposes that one item instead of this whole screen.
    loadingMore: StateFlow<Boolean>,
    snackbarHostState: SnackbarHostState? = null,
    onNightLight: (() -> Unit)? = null
) {
    val content = state as? ArrivalsUiState.Content
    Scaffold(
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = { Text(content?.header?.name?.takeIf { it.isNotEmpty() } ?: initialTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
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
                                        R.drawable.star
                                    } else {
                                        R.drawable.star_outline
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
                        OverflowMenu(
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

                // The full screen isn't height-constrained like the drawer's peek, so it keeps
                // alerts expanded (showAlerts defaults to true) rather than offering a collapse toggle.
                is ArrivalsUiState.Content -> ArrivalsList(
                    content = state,
                    rowCallbacks = rowCallbacks,
                    handler = handler,
                    onShowHiddenAlerts = onShowHiddenAlerts,
                    onLoadMore = onLoadMore,
                    loadingMore = loadingMore
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
}

@Composable
internal fun OverflowMenu(
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
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.stop_info_item_options_title),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuRow(R.string.stop_info_option_show_details) {
                expanded = false
                onStopDetails()
            }
            MenuRow(R.string.stop_info_option_report_problem) {
                expanded = false
                onReportStopProblem()
            }
            MenuRow(R.string.stop_info_option_hide_alerts) {
                expanded = false
                onHideAlerts()
            }
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
    onShowHiddenAlerts: () -> Unit,
    /** Widens the time window and reloads (the list's "load more trips" footer button). */
    onLoadMore: () -> Unit,
    /** Whether a load-more request is in flight, for the footer button's spinner. Collected only by
     *  the footer item below, so a toggle recomposes just that item, not the whole list. */
    loadingMore: StateFlow<Boolean>,
    modifier: Modifier = Modifier,
    /** Stop-focus map colors keyed by route-direction. Empty outside the home drawer. */
    mapRouteColors: Map<RouteDirectionKey, Int> = emptyMap(),
    /** Exact route-direction row selected over the home map's stop focus; null outside that state. */
    selectedRowKey: String? = null,
    /** Origin route used to resolve an unambiguous row when map and arrivals headsign labels differ. */
    selectedRouteId: String? = null,
    /** Route names in the selected vehicle block, beginning with the route on this row. */
    selectedRouteNames: List<String> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    /** Hosts that show the stop's direction elsewhere (e.g. in their own header) set this false to
     *  avoid duplicating it as a list item. */
    showDirection: Boolean = true,
    /** Inset for the scrollable content (e.g. a bottom inset so the list clears the nav-bar chrome). */
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Whether to render the stop's service alerts (the alert list and the "show hidden" footnote) at
     *  the head of the list. Hosts that own a collapse toggle pass its state so alerts stay hidden
     *  until expanded; the default keeps them visible. Changes animate (see the "alerts" item). */
    showAlerts: Boolean = true,
    /** An opaque anchor modifier applied to the first route row's ETA pill (e.g. the home sheet's
     *  onboarding spotlight); default is a no-op for hosts that don't spotlight. */
    etaAnchor: Modifier = Modifier
) {
    val effectiveSelectedRowKey = remember(content.routeGroups, selectedRowKey, selectedRouteId) {
        resolveSelectedRouteGroupKey(content.routeGroups, selectedRowKey, selectedRouteId)
    }
    val routeGroups = remember(content.routeGroups, effectiveSelectedRowKey) {
        promoteSelectedRouteGroup(content.routeGroups, effectiveSelectedRowKey)
    }
    var hadSelection by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveSelectedRowKey) {
        // Stable item keys preserve the old viewport across reordering. Explicitly return to the head
        // of the route rows so the promoted row is visible in the peek, and the original head is
        // visible again when route mode clears.
        val wasSelected = hadSelection
        hadSelection = effectiveSelectedRowKey != null
        if (effectiveSelectedRowKey != null || wasSelected) {
            val alertsBeforeRoutes = content.hasAlerts && showAlerts
            val directionBeforeRoutes = showDirection && content.header.direction != null
            val firstRouteIndex = (if (alertsBeforeRoutes) 1 else 0) +
                (if (directionBeforeRoutes) 1 else 0)
            listState.scrollToItem(firstRouteIndex)
        }
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (content.hasAlerts && showAlerts) {
            // The whole alert section is one item, present only while [showAlerts] is set. Toggling the
            // header's alert icon adds/removes this item; Modifier.animateItem() fades it in/out and lets
            // the route rows below glide into place. (An always-present item gated by AnimatedVisibility
            // stranded the section at zero height in the collapsed peek — the lazy layout didn't remeasure
            // it on toggle, so the alerts only appeared after a drag forced a relayout.)
            item(key = "alerts") {
                ServiceAlertsContent(
                    alerts = content.alerts,
                    hiddenAlertCount = content.hiddenAlertCount,
                    onShowAlert = handler::onShowAlert,
                    onHideAlert = handler::onHideAlert,
                    onShowHiddenAlerts = onShowHiddenAlerts,
                    modifier = Modifier.animateItem()
                )
            }
        }
        if (showDirection) {
            content.header.direction?.let { direction ->
                item(key = "direction") { DirectionLine(direction) }
            }
        }
        if (content.routeGroups.isEmpty()) {
            item(key = "empty") { EmptyArrivals(content.minutesAfter) }
        } else {
            itemsIndexed(routeGroups, key = { _, group -> group.key }) { index, group ->
                RouteArrivalRow(
                    group = group,
                    actionsFor = { content.actions[it.tripId] },
                    isFavorite = group.routeId in content.favoriteRouteIds,
                    callbacks = rowCallbacks,
                    mapRouteColor = mapRouteColors[RouteDirectionKey(group.routeId, group.directionId)],
                    selected = group.key == effectiveSelectedRowKey,
                    selectedRouteNames =
                    if (group.key == effectiveSelectedRowKey) selectedRouteNames else emptyList(),
                    // The onboarding ETA spotlight anchors on the first route row's pill only.
                    etaAnchor = if (index == 0) etaAnchor else Modifier,
                    // Glide up/down as the alert section above is toggled in/out.
                    modifier = Modifier.animateItem()
                )
            }
        }
        item(key = "load_more") {
            val loading by loadingMore.collectAsStateWithLifecycle()
            LoadMoreFooter(windowEnd = content.windowEnd, loading = loading, onClick = onLoadMore)
        }
    }
}

/**
 * The list's "load more trips" footer: a muted "Showing arrivals until HH:MM" note giving the rider
 * the window's current far edge, followed by the clickable "load more" affordance that widens the
 * stop's arrivals time window and reloads. Shown below the arrivals (or the empty-list message) so
 * it's always reachable. (Replaces the old per-strip pull-to-reload gesture, which surprised riders by
 * pulling in other routes' trips too.)
 */
@Composable
private fun LoadMoreFooter(windowEnd: ServerTime, loading: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // Formatting hits DateUtils; key it to windowEnd so the spinner toggling on each tap doesn't reformat.
    val untilTime = remember(windowEnd, context) { DisplayFormat.formatTime(context, windowEnd.epochMs) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        // Center the pair as a unit, with a gap just wider than a space between them.
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.stop_info_showing_trips_until, untilTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        // The clickable "load more" retains the old button's TextButton styling (primary-colored text);
        // trimmed content padding keeps the gap to the note tight rather than the default 12dp inset.
        TextButton(
            onClick = onClick,
            enabled = !loading,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(stringResource(R.string.stop_info_load_more))
        }
    }
}

/**
 * A muted, secondary footnote (not a peer to the alert list's "more" button): the eye-off icon
 * conveys "hidden", tapping reveals the [count] user-hidden alerts again.
 */
@Composable
private fun HiddenAlertsRow(count: Int, onShowHiddenAlerts: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onShowHiddenAlerts)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_visibility_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = pluralStringResource(R.plurals.alert_filter_text, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The stop's service alerts, capped at [ALERT_PAGE_SIZE] rows with a paged "show more" link that
 * reveals the next page each tap — mirrors the arrivals list's "load more" so a busy alert feed
 * can't crowd out the arrivals. Each row is right-swipe-to-hide. Paging state is local and persists
 * across the 60s refresh; it resets only when the list leaves composition.
 */
@Composable
internal fun ServiceAlertsContent(
    alerts: List<AlertItem>,
    hiddenAlertCount: Int,
    onShowAlert: (String) -> Unit,
    onHideAlert: (AlertItem) -> Unit,
    onShowHiddenAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (alerts.isNotEmpty()) {
            AlertList(alerts, onShowAlert, onHideAlert)
        }
        if (hiddenAlertCount > 0) {
            HiddenAlertsRow(hiddenAlertCount, onShowHiddenAlerts)
        }
    }
}

@Composable
private fun AlertList(
    alerts: List<AlertItem>,
    onShowAlert: (String) -> Unit,
    onHideAlert: (AlertItem) -> Unit
) {
    var visibleCount by rememberSaveable { mutableIntStateOf(ALERT_PAGE_SIZE) }
    val visible = alerts.take(visibleCount)
    Column {
        for (alert in visible) {
            // Key the swipe state to the content identity so it tracks the row (not the slot, and not
            // a transient situation id) across refreshes.
            key(alert.contentId) {
                SwipeToHide(onHide = { onHideAlert(alert) }) {
                    AlertRow(alert) { onShowAlert(alert.situationId) }
                }
            }
        }
        if (visible.size < alerts.size) {
            val remaining = alerts.size - visible.size
            TextButton(
                onClick = { visibleCount += ALERT_PAGE_SIZE },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Text(pluralStringResource(R.plurals.alert_show_more, remaining, remaining))
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = AppIcons.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Wraps [content] so a right-swipe (start-to-end) slides it fully off, then collapses the empty
 * space, invoking [onHide] once the collapse finishes (so the list below glides up instead of
 * jumping). Left-swipe is disabled, and there's no swipe-behind affordance — a hidden alert is
 * recovered from the "show hidden alerts" link below the list.
 */
@Composable
private fun SwipeToHide(onHide: () -> Unit, content: @Composable () -> Unit) {
    // Which swipe directions are allowed is expressed by the box's enableDismissFromStartToEnd /
    // enableDismissFromEndToStart flags below (the modern "leave disallowed anchors out" approach),
    // replacing the deprecated confirmValueChange veto callback.
    val dismissState = rememberSwipeToDismissBoxState()
    val rowVisible = remember { MutableTransitionState(true) }
    // Once the row settles in the dismissed position, start the collapse.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            rowVisible.targetState = false
        }
    }
    // Commit the hide only after the collapse animation has fully finished.
    LaunchedEffect(rowVisible.isIdle) {
        if (rowVisible.isIdle && !rowVisible.currentState) onHide()
    }
    AnimatedVisibility(visibleState = rowVisible, exit = fadeOut() + shrinkVertically()) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = false,
            // No swipe-behind content: the row slides off into empty space.
            backgroundContent = {}
        ) {
            content()
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
