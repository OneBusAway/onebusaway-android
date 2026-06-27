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
import org.onebusaway.android.io.elements.ObaSituation
import org.onebusaway.android.io.request.ObaArrivalInfoResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    // --- Reactive state sources. The UI [state] is derived from these, so a user action updates the
    // screen by mutating a source rather than imperatively recomputing and assigning the state. -----

    /** The latest fetched snapshot (null until the first load). */
    private val loaded = MutableStateFlow<ArrivalsData?>(null)

    /** Which alert ids are hidden — seeded from the DB on each load and mutated optimistically by the
     *  hide/show actions (which persist to the DB as a side effect). The shown/hidden alert split is
     *  derived from this, so hiding or un-hiding an alert needs no re-fetch. It must be a separate
     *  source (not folded into [loaded]) because session hides have to survive across loads; the
     *  optimistic favorite, which doesn't, is just edited into [loaded] in place. */
    private val hiddenIds = MutableStateFlow<Set<String>>(emptySet())

    /** Set only when a load fails with nothing to show; cleared by any successful load. */
    private val fatalError = MutableStateFlow<String?>(null)

    /** The UI state: the fetched [loaded] snapshot projected through the [hiddenIds] override. */
    val state: StateFlow<ArrivalsUiState> =
        combine(loaded, hiddenIds, fatalError) { data, hidden, error ->
            when {
                data != null -> data.toContent(hidden)
                error != null -> ArrivalsUiState.Error(error)
                else -> ArrivalsUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ArrivalsUiState.Loading)

    /**
     * Emits the raw response after each successful load. The map panel uses this to recenter the
     * map, move the FABs, and fire arrival-info tutorials (which need the response object) without
     * the ViewModel itself touching Android. Empty for the standalone screen, which ignores it.
     */
    private val _responses = MutableSharedFlow<ObaArrivalInfoResponse>(extraBufferCapacity = 1)
    val responses: SharedFlow<ObaArrivalInfoResponse> = _responses.asSharedFlow()

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
                // Hidden-alert state lives in two places: the service_alerts DB (the persisted truth,
                // incl. the "hide all alerts" preference auto-hide) and [hiddenIds] (the reactive
                // source). We reconcile on each load: union in the DB's hides, keep any session hides
                // whose write-through hasn't landed yet, and prune ids that are gone. (A deeper design
                // would observe the provider URI as a flow and drop this in-memory mirror entirely.)
                // Update before publishing [loaded] so the derived state never flashes the new alerts
                // against a stale hidden set.
                val activeIds = data.activeAlerts.mapTo(mutableSetOf()) { it.id }
                hiddenIds.update { (it + data.dbHiddenIds) intersect activeIds }
                fatalError.value = null
                loaded.value = data
                repository.lastResponse()?.let { _responses.tryEmit(it) }
            },
            onFailure = { error ->
                if (loaded.value == null) fatalError.value = error.message.orEmpty()
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

    /** Toggles the stop favorite, updating the header optimistically and persisting. The optimistic
     *  flag is edited straight into [loaded] (the next load overwrites it with the persisted value),
     *  so unlike [hiddenIds] it needs no separate override source. */
    fun toggleFavorite() {
        val data = loaded.value ?: return
        val newValue = !data.header.isFavorite
        loaded.value = data.copy(header = data.header.copy(isFavorite = newValue))
        viewModelScope.launch { repository.setStopFavorite(data.header.stopId, newValue) }
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

    /** Hides a single alert (the row's swipe-to-hide gesture). */
    fun hideAlert(id: String) {
        hiddenIds.update { it + id }
        persistHidden(listOf(id))
    }

    /** Hides every currently shown alert (the toolbar "hide alerts" action). */
    fun hideAllAlerts() {
        val ids = loaded.value?.activeAlerts?.map { it.id }.orEmpty()
        if (ids.isEmpty()) return
        hiddenIds.update { it + ids }
        persistHidden(ids)
    }

    /** Un-hides every alert (the "show hidden alerts" affordance). */
    fun showHiddenAlerts() {
        hiddenIds.value = emptySet()
        viewModelScope.launch { repository.showAllAlerts() }
    }

    /** Persists the hidden flag for [ids] off the UI path — the screen already reacted to [hiddenIds]. */
    private fun persistHidden(ids: List<String>) {
        viewModelScope.launch { repository.hideAlerts(ids) }
    }

    /** The full situation for an alert id, for the alert dialog (read from the last good response). */
    fun situation(id: String): ObaSituation? = repository.situation(id)

    /** The last good response, for the report-flow picker to resolve agency/block from refs. */
    fun lastResponse(): ObaArrivalInfoResponse? = repository.lastResponse()

    /** Projects a fetched snapshot through the [hidden] set, which splits [activeAlerts] into the
     *  shown list and the hidden count. */
    private fun ArrivalsData.toContent(hidden: Set<String>): ArrivalsUiState.Content {
        val shown = activeAlerts.filterNot { it.id in hidden }
        return ArrivalsUiState.Content(
            header = header,
            arrivals = arrivals,
            minutesAfter = minutesAfter,
            style = style,
            isStale = isStale,
            actions = actions,
            alerts = shown,
            hiddenAlertCount = activeAlerts.size - shown.size,
            routeFilterOptions = routeFilterOptions,
            filteredRouteCount = filteredRouteCount,
            stopCode = stopCode,
            stopLat = stopLat,
            stopLon = stopLon,
            stopUserName = stopUserName
        )
    }
}
