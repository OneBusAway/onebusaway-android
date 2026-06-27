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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Collapses a route-filter selection to "show all" (empty) when every route is selected, matching
 * the legacy RoutesFilterDialog. Kept as a pure function so it's unit-testable.
 */
internal fun collapseRouteFilter(selected: Set<String>, totalRoutes: Int): Set<String> =
    if (selected.size >= totalRoutes) emptySet() else selected

/**
 * ViewModel for the arrivals screen. The 60-second polling loop lives in the screen (driven by the
 * host lifecycle); this exposes [refresh] for it to call plus the user actions. The current time
 * window ([minutesAfter]) grows with "load more"; the route filter is seeded from the provider on
 * the first load and then held in memory.
 *
 * Assisted-injected: [repository] comes from Dagger, while [stopId]/[ignorePersistedFilter] are
 * runtime args supplied by each host via [Factory] — the NavHost destination passes the nav-arg
 * stop id, the home sheet passes the focused stop's (dynamic) id, and the report picker passes its
 * stop with `ignorePersistedFilter = true`. Plain `@AssistedInject` (not `@HiltViewModel`) so the
 * [Factory] can be `@Inject`ed into each host and used inside `viewModelFactory {}` — Hilt forbids
 * injecting a `@HiltViewModel`'s assisted factory, and the home sheet's per-stop cleared
 * `ViewModelStoreOwner` isn't Hilt-aware, so `hiltViewModel()` can't serve it either.
 */
class ArrivalsViewModel @AssistedInject constructor(
    @Assisted private val stopId: String,
    /** When true, always show all routes (the report-flow picker), ignoring the saved filter. */
    @Assisted ignorePersistedFilter: Boolean,
    private val repository: ArrivalsRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(stopId: String, ignorePersistedFilter: Boolean): ArrivalsViewModel
    }

    private val _state = MutableStateFlow<ArrivalsUiState>(ArrivalsUiState.Loading)
    val state: StateFlow<ArrivalsUiState> = _state.asStateFlow()

    /**
     * Emits the raw response after each successful load. The map panel uses this to recenter the
     * map, move the FABs, and fire arrival-info tutorials (which need the response object) without
     * the ViewModel itself touching Android. Empty for the standalone screen, which ignores it.
     */
    private val _arrivalsLoaded = MutableSharedFlow<ArrivalsLoaded>(extraBufferCapacity = 1)
    val arrivalsLoaded: SharedFlow<ArrivalsLoaded> = _arrivalsLoaded.asSharedFlow()

    /** The pending route-favorite dialog request (the route the user tapped "favorite" on), or null. */
    private val _favoriteRequest = MutableStateFlow<ArrivalActions?>(null)
    val favoriteRequest: StateFlow<ArrivalActions?> = _favoriteRequest.asStateFlow()

    /** Whether the stop-details dialog (the overflow "show stop details" action) is showing. */
    private val _stopDetailsVisible = MutableStateFlow(false)
    val stopDetailsVisible: StateFlow<Boolean> = _stopDetailsVisible.asStateFlow()

    private var minutesAfter = DefaultArrivalsRepository.MINUTES_AFTER_DEFAULT

    private var routeFilter: Set<String> = emptySet()

    /**
     * Until the first load, let the repository seed the filter from the provider — unless we're
     * told to ignore it, in which case the empty (show-all) filter is used and never persisted.
     */
    private var filterLoaded = ignorePersistedFilter

    /** Wall-clock time of the last completed load, read by the screen's polling loop. */
    var lastResponseTimeMs: Long = 0L
        private set

    /**
     * Loads the arrivals once. Suspends until done so the screen's polling loop can measure the
     * 60s interval from completion. A failed refresh keeps any existing content (the repository
     * already returns the last good data as stale); it only surfaces [ArrivalsUiState.Error]
     * when there is nothing to show.
     */
    suspend fun refresh() {
        val result = repository.getArrivals(stopId, minutesAfter, routeFilter.takeIf { filterLoaded })
        lastResponseTimeMs = System.currentTimeMillis()
        result.fold(
            onSuccess = { data ->
                minutesAfter = data.minutesAfter
                routeFilter = data.effectiveRouteFilter
                filterLoaded = true
                _state.value = data.toContent()
                repository.lastLoaded()?.let { _arrivalsLoaded.tryEmit(it) }
            },
            onFailure = { error ->
                if (_state.value !is ArrivalsUiState.Content) {
                    _state.value = ArrivalsUiState.Error(error.message.orEmpty())
                }
            }
        )
    }

    /** Refreshes from a user action (the toolbar refresh button or Retry). */
    fun manualRefresh() {
        viewModelScope.launch { refresh() }
    }

    /** Widens the time window and reloads (the "load more arrivals" footer). */
    fun loadMore() {
        minutesAfter += DefaultArrivalsRepository.MINUTES_AFTER_INCREMENT
        viewModelScope.launch { refresh() }
    }

    /** Toggles the stop favorite, updating the header optimistically and persisting. */
    fun toggleFavorite() {
        val content = _state.value as? ArrivalsUiState.Content ?: return
        val newValue = !content.header.isFavorite
        _state.value = content.copy(header = content.header.copy(isFavorite = newValue))
        viewModelScope.launch { repository.setStopFavorite(content.header.stopId, newValue) }
    }

    /** Opens the route-favorite dialog for [actions] (the per-arrival "favorite route" tap). */
    fun requestRouteFavorite(actions: ArrivalActions) {
        _favoriteRequest.value = actions
    }

    /** Dismisses the route-favorite dialog without changing anything. */
    fun dismissRouteFavorite() {
        _favoriteRequest.value = null
    }

    /** Opens the stop-details dialog (the overflow "show stop details" action). */
    fun requestStopDetails() {
        _stopDetailsVisible.value = true
    }

    /** Dismisses the stop-details dialog. */
    fun dismissStopDetails() {
        _stopDetailsVisible.value = false
    }

    /**
     * Applies the route-favorite choice: stars/unstars the route/headsign (scoped to this stop, or
     * all stops when [allStops]), backfills the route details, then reloads. Closes the dialog.
     */
    fun favoriteRoute(actions: ArrivalActions, allStops: Boolean) {
        _favoriteRequest.value = null
        viewModelScope.launch {
            repository.favoriteRoute(
                routeId = actions.routeId,
                headsign = actions.headsign,
                stopId = if (allStops) null else actions.stopId,
                shortName = actions.routeShortName,
                longName = actions.routeLongName,
                favorite = !actions.isRouteFavorite
            )
            refresh()
        }
    }

    /** Switches the arrival-info display style (the legacy "sort by" view-mode toggle) and reloads. */
    fun setArrivalStyle(style: Int) {
        viewModelScope.launch {
            repository.setArrivalStyle(style)
            refresh()
        }
    }

    /** Replaces the route filter (empty == show all), persists it, and reloads. */
    fun setRouteFilter(filter: Set<String>) {
        routeFilter = filter
        filterLoaded = true
        viewModelScope.launch {
            repository.setRouteFilter(stopId, filter)
            refresh()
        }
    }

    /** The per-arrival "show only this route" toggle: select it, or clear if already narrowed. */
    fun showOnlyRoute(routeId: String) {
        val target = setOf(routeId)
        // Legacy toggle: clear when already showing just this route, or when a broader filter is set
        setRouteFilter(if (routeFilter == target || routeFilter.size > 1) emptySet() else target)
    }

    /** Clears the route filter (the header "show all" affordance). */
    fun showAllRoutes() {
        setRouteFilter(emptySet())
    }

    /** Hides every currently shown alert (the toolbar "hide alerts" action). */
    fun hideAllAlerts() {
        val ids = (_state.value as? ArrivalsUiState.Content)?.alerts?.map { it.id } ?: return
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.hideAlerts(ids)
            refresh()
        }
    }

    /** Un-hides every alert (the "show hidden alerts" affordance). */
    fun showHiddenAlerts() {
        viewModelScope.launch {
            repository.showAllAlerts()
            refresh()
        }
    }

    /** The service-alert dialog's content for an alert id (read from the last good response). */
    fun alertDetails(id: String): AlertDetails? = repository.alertDetails(id)

    private fun ArrivalsData.toContent() = ArrivalsUiState.Content(
        header = header,
        arrivals = arrivals,
        minutesAfter = minutesAfter,
        style = style,
        isStale = isStale,
        actions = actions,
        alerts = alerts,
        hiddenAlertCount = hiddenAlertCount,
        routeFilterOptions = routeFilterOptions,
        filteredRouteCount = filteredRouteCount,
        stopCode = stopCode,
        stopLat = stopLat,
        stopLon = stopLon,
        stopUserName = stopUserName
    )
}
