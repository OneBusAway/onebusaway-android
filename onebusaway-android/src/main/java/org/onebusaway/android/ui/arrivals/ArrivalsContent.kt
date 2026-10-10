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

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import org.onebusaway.android.R
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.arrivals.components.ArrivalDisplayModeSwitch
import org.onebusaway.android.ui.arrivals.components.ArrivalRowAnchors
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.compose.components.AlertSurface
import org.onebusaway.android.ui.icons.AppIcons
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
    viewModel: ArrivalsViewModel,
    mapless: Boolean = false
): ArrivalRowCallbacks = remember(handler, viewModel, mapless) {
    ArrivalRowCallbacks(
        onRouteFavorite = handler::onRouteFavorite,
        onShowVehiclesOnMap = handler::onShowVehiclesOnMap,
        onShowRouteOnMap = handler::onShowRouteOnMap,
        onEtaClick = if (mapless) handler::onShowTripStatus else handler::onFocusVehicleOnMap,
        onShowTripStatus = handler::onShowTripStatus,
        onSetReminder = handler::onSetReminder,
        onToggleTracking = handler::onToggleTracking,
        onShowRouteSchedule = handler::onShowRouteSchedule,
        onReportArrivalProblem = handler::onReportArrivalProblem,
        onShowAlert = handler::onShowAlert,
        onRowClick = if (mapless) handler::onShowTripStatus else handler::onShowVehiclesOnMap
    )
}

/**
 * The arrivals board's per-stop route filter (#2366): the row menu's "show only this route" / "hide
 * this route" / "show all routes" (hide is also a right swipe on the row), and the hidden-routes line's
 * "show all routes". Hide goes through [ArrivalActionHandler.onHideRoute] for its undo snackbar; the
 * rest are pure ViewModel operations.
 */
class RouteFilterCallbacks(
    val onShowOnly: (routeId: String) -> Unit,
    val onHide: (routeId: String) -> Unit,
    val onShowAll: () -> Unit
)

/** The route-filter callbacks over [viewModel]'s filter actions, with hide via [handler] (#2366). */
@Composable
internal fun rememberRouteFilterCallbacks(
    viewModel: ArrivalsViewModel,
    handler: ArrivalActionHandler
): RouteFilterCallbacks = remember(viewModel, handler) {
    RouteFilterCallbacks(
        onShowOnly = viewModel::showOnlyRoute,
        onHide = handler::onHideRoute,
        onShowAll = viewModel::showAllRoutes
    )
}

/**
 * Navigation/dialog actions for the arrivals screen, implemented by the host activity (it has the
 * Context the targets need). The alert hide/show actions and most of the route filter are pure
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

    /** Starts or stops the live countdown notification for this arrival's whole route row (#2166). */
    fun onToggleTracking(arrival: ArrivalInfo)
    fun onShowRouteSchedule(scheduleUrl: String)
    fun onReportArrivalProblem(actions: ArrivalActions)
    fun onShowAlert(alertId: String)
    fun onHideAlert(alert: AlertItem)

    /** Hides [routeId] at this stop (#2366) — the row menu's "Hide this route" or a right swipe — and
     *  offers an undo snackbar, which needs the host's snackbar. */
    fun onHideRoute(routeId: String)
    fun onReportStopProblem()
}

@Composable
internal fun ArrivalsList(
    content: ArrivalsUiState.Content,
    rowCallbacks: ArrivalRowCallbacks,
    onShowAlert: (String) -> Unit,
    onHideAlert: (AlertItem) -> Unit,
    onShowHiddenAlerts: () -> Unit,
    /** Widens the time window and reloads (the list's "load more trips" footer button). */
    onLoadMore: () -> Unit,
    /** Whether a load-more request is in flight, for the footer button's spinner. Collected only by
     *  the footer item below, so a toggle recomposes just that item, not the whole list. */
    loadingMore: StateFlow<Boolean>,
    modifier: Modifier = Modifier,
    /** Stop-focus map colors keyed by route-direction. Empty outside the home drawer. */
    mapRouteColors: Map<RouteDirectionKey, Int> = emptyMap(),
    // The selected trip's band tint (#1990), or null when no vehicle is selected.
    selectedTripBandColor: Int? = null,
    /** Exact route-direction row selected over the home map's stop focus; null outside that state. */
    selectedRowKey: String? = null,
    /** Origin route used to resolve an unambiguous row when map and arrivals headsign labels differ. */
    selectedRouteId: String? = null,
    /** Route names in the selected vehicle block, beginning with the route on this row. */
    selectedRouteNames: List<String> = emptyList(),
    /** The trip drilled into within the selected row (the stop→route→trip focus, #2205); null when the
     *  selection stops at the route. */
    selectedTripId: String? = null,
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
    /** Spotlight anchors for the first route row — its ETA pill, its route badge and its favourite
     *  star, each addressable on its own so a tutorial can point at one without the others (e.g. the
     *  home sheet's onboarding spotlight). The defaults are no-ops for hosts that don't spotlight. */
    anchors: ArrivalRowAnchors = ArrivalRowAnchors(),
    displayMode: ArrivalDisplayMode = ArrivalDisplayMode.ROUTE,
    onDisplayModeChange: ((ArrivalDisplayMode) -> Unit)? = null,
    modeSwitchModifier: Modifier = Modifier,
    /** The stop's route-filter actions (#2366): the row menu's filter items and the hidden-routes line's
     *  "show all routes". The board hides [ArrivalsUiState.Content.hiddenRouteIds] either way — a host
     *  without these can't change the filter, but never ignores it. */
    routeFilter: RouteFilterCallbacks? = null
) {
    val effectiveSelectedRowKey = remember(content.routeGroups, selectedRowKey, selectedRouteId) {
        resolveSelectedRouteGroupKey(content.routeGroups, selectedRowKey, selectedRouteId)
    }
    val hiddenRouteIds = content.hiddenRouteIds
    val displayedGroups = remember(content.arrivals, content.routeGroups, effectiveSelectedRowKey, displayMode, hiddenRouteIds) {
        val groups = if (displayMode == ArrivalDisplayMode.TIME) {
            chronologicalArrivals(content.arrivals).map { RouteRowGroup(listOf(it)) }
        } else {
            promoteSelectedRouteGroup(content.routeGroups, effectiveSelectedRowKey)
        }
        visibleRouteGroups(groups, hiddenRouteIds, effectiveSelectedRowKey)
    }
    var previousMode by rememberSaveable { mutableStateOf(displayMode) }
    LaunchedEffect(displayMode) {
        if (previousMode != displayMode) listState.scrollToItem(0)
        previousMode = displayMode
    }
    var hadSelection by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveSelectedRowKey) {
        // Stable item keys preserve the old viewport across reordering. Explicitly return to the head
        // of the route rows so the promoted row is visible in the peek, and the original head is
        // visible again when route mode clears.
        val wasSelected = hadSelection
        hadSelection = effectiveSelectedRowKey != null
        if (displayMode == ArrivalDisplayMode.ROUTE && (effectiveSelectedRowKey != null || wasSelected)) {
            val alertsBeforeRoutes = content.hasAlerts && showAlerts
            val directionBeforeRoutes = showDirection && content.header.direction != null
            val firstRouteIndex = (if (alertsBeforeRoutes) 1 else 0) +
                (if (directionBeforeRoutes) 1 else 0) +
                (if (onDisplayModeChange != null) 1 else 0) +
                (if (hiddenRouteIds.isNotEmpty()) 1 else 0)
            listState.scrollToItem(firstRouteIndex)
        }
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth(), contentPadding = contentPadding) {
        if (onDisplayModeChange != null) {
            item(key = "display-mode") { ArrivalDisplayModeSwitch(displayMode, onDisplayModeChange, modeSwitchModifier) }
        }
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
                    onShowAlert = onShowAlert,
                    onHideAlert = onHideAlert,
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
        if (hiddenRouteIds.isNotEmpty()) {
            // Above the rows rather than in the footer, so a filtered board says so even in the peek.
            item(key = "hidden-routes") {
                HiddenItemsFootnote(
                    iconRes = R.drawable.ic_filter_list,
                    text = pluralStringResource(R.plurals.stop_info_hidden_routes, hiddenRouteIds.size, hiddenRouteIds.size),
                    action = routeFilter?.let {
                        FootnoteAction(stringResource(R.string.bus_options_menu_show_all_routes), it.onShowAll)
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
        if (displayedGroups.isEmpty()) {
            item(key = "empty") { EmptyArrivals(content.minutesAfter) }
        } else {
            itemsIndexed(displayedGroups, key = { _, group -> if (displayMode == ArrivalDisplayMode.TIME) group.representative.arrivalRowKey() else group.key }) { index, group ->
                // Route selection highlights its group, or each matching departure in Time mode.
                val isSelectedRow = group.key == effectiveSelectedRowKey
                // Remembered so an unchanged row keeps the same instance and can still skip recomposition.
                val filterActions = routeFilter?.let {
                    remember(it, group.routeId, content.stopRouteIds, hiddenRouteIds) {
                        it.forRoute(group.routeId, content.stopRouteIds, hiddenRouteIds)
                    }
                }
                // A right swipe is the row menu's "Hide this route" (#2366), offered exactly where that
                // item is — never on the last route showing. Always wrapped, only toggled, so a row that
                // gains or loses the swipe keeps its state. Swipes that start on the ETA pills scroll
                // the strip instead; the badge and headsign are the grip.
                SwipeToHide(
                    onHide = filterActions?.onHide,
                    // Glide up/down as the alert section above is toggled in/out.
                    modifier = Modifier.animateItem()
                ) {
                    RouteArrivalRow(
                        group = group,
                        chronological = displayMode == ArrivalDisplayMode.TIME,
                        actionsFor = { content.actions[it.tripId] },
                        isFavorite = group.routeId in content.favoriteRouteIds,
                        callbacks = rowCallbacks,
                        mapRouteColor = mapRouteColors[RouteDirectionKey(group.routeId, group.directionId)],
                        selectedTripBandColor = selectedTripBandColor,
                        selected = isSelectedRow,
                        selectedRouteNames = if (isSelectedRow) selectedRouteNames else emptyList(),
                        selectedTripId = selectedTripId.takeIf { isSelectedRow },
                        // The onboarding ETA spotlight anchors on the first route row's pill only.
                        anchors = if (index == 0) anchors else ArrivalRowAnchors(),
                        tracked = group.representative.trackedRouteKey() in content.trackedRows,
                        routeFilter = filterActions
                    )
                }
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
            contentPadding = COMPACT_LINK_PADDING
        ) {
            Text(stringResource(R.string.stop_info_load_more))
        }
    }
}

/**
 * A muted, secondary footnote (not a peer to the list's own buttons) saying how many of something the
 * rider has hidden — [text], e.g. "2 hidden alerts" or "2 routes hidden" (#2366) — and offering to bring
 * them back ([onClick]). The board's two hidden-things lines share it, so they look alike.
 *
 * With an [action], only its label is the tap target, a text button right after the count: the count
 * is a statement, and making it tappable too suggests it opens some way to manage what's hidden, when
 * the tap only ever does what the label says. Without one, [onClick] makes the whole line the target.
 */
@Composable
private fun HiddenItemsFootnote(
    @DrawableRes iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    action: FootnoteAction? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Shrinks rather than pushing the action off the line, but doesn't fill it: the action sits
            // beside the count, not at the far end.
            modifier = Modifier.weight(1f, fill = action == null)
        )
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            // Compact like the list's "load more" button, so it reads as a link on a footnote.
            TextButton(onClick = action.onClick, contentPadding = COMPACT_LINK_PADDING) {
                Text(action.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A [HiddenItemsFootnote]'s own tap target: [label], doing [onClick]. */
private class FootnoteAction(val label: String, val onClick: () -> Unit)

/** The board's compact text buttons ("load more", a footnote's action), which read as links. */
private val COMPACT_LINK_PADDING = PaddingValues(horizontal = 4.dp, vertical = 4.dp)

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
            // The eye-off icon conveys "hidden"; tapping reveals the user-hidden alerts again.
            HiddenItemsFootnote(
                iconRes = R.drawable.ic_visibility_off,
                text = pluralStringResource(R.plurals.alert_filter_text, hiddenAlertCount, hiddenAlertCount),
                onClick = onShowHiddenAlerts
            )
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
 * jumping). Left-swipe is disabled, and there's no swipe-behind affordance — a hidden alert or route
 * is recovered from the "show hidden" line on the board. A null [onHide] disables the swipe.
 */
@Composable
internal fun SwipeToHide(onHide: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Deliberately not rememberSwipeToDismissBoxState, which is rememberSaveable: a lazy list keeps a
    // row's saved state under its key, so a row brought back by an undo would return already swiped and
    // hide itself again — and every row key that ever scrolled by would leave an entry behind.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val rowVisible = remember { MutableTransitionState(true) }
    val currentOnHide by rememberUpdatedState(onHide)
    // Once the row settles in the dismissed position, collapse it; commit the hide only after the
    // collapse has fully finished.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }.first { it == SwipeToDismissBoxValue.StartToEnd }
        rowVisible.targetState = false
        snapshotFlow { rowVisible.isIdle }.first { it }
        currentOnHide?.invoke()
    }
    AnimatedVisibility(visibleState = rowVisible, modifier = modifier, exit = fadeOut() + shrinkVertically()) {
        // Which swipe directions are allowed is expressed by the enableDismissFrom… flags (the modern
        // "leave disallowed anchors out" approach), replacing the deprecated confirmValueChange veto.
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = onHide != null,
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
    AlertSurface(severity = alert.severity, onClick = onClick) {
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
