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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.onebusaway.android.time.WallTime

/**
 * The lifecycle of a pull-to-load-more request (the ETA strip's pull-past-end gesture). One request
 * is tracked at a time — the load is global to the stop — and the [Loading.token] identifies which
 * strip fired it, so only that strip animates its spinner/reveal.
 */
sealed interface LoadMoreState {
    data object Idle : LoadMoreState
    data class Loading(val token: Int) : LoadMoreState

    /**
     * [dataVersion] is the [ArrivalsUiState.Content.dataVersion] current when this request's load
     * completed; the firing strip settles once its *layout* reflects a version >= this. [success]
     * means a fresh network result landed (a stale fallback counts as failure — nothing new could
     * have arrived).
     */
    data class Finished(val token: Int, val success: Boolean, val dataVersion: Long) : LoadMoreState
}

/**
 * ViewModel for the arrivals screen. The 60-second polling loop lives in the screen (driven by the
 * host lifecycle); this exposes [refresh] for it to call plus the user actions. The current time
 * window ([minutesAfter]) grows with "load more".
 *
 * Assisted-injected: [repository] comes from Dagger, while [stopId] is a runtime arg supplied by each
 * host via [Factory] — the NavHost destination passes the nav-arg stop id, the home sheet passes the
 * focused stop's (dynamic) id, and the report picker passes its stop. Plain `@AssistedInject` (not
 * `@HiltViewModel`) so the [Factory] can be `@Inject`ed into each host and used inside
 * `viewModelFactory {}` — Hilt forbids injecting a `@HiltViewModel`'s assisted factory, and the home
 * sheet's per-stop cleared `ViewModelStoreOwner` isn't Hilt-aware, so `hiltViewModel()` can't serve it
 * either.
 */
class ArrivalsViewModel @AssistedInject constructor(
    @Assisted private val stopId: String,
    private val repository: ArrivalsRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(stopId: String): ArrivalsViewModel
    }

    // --- Reactive state sources. The UI [state] is derived from these, so a user action updates the
    // screen by mutating a source rather than imperatively recomputing and assigning the state. -----

    /**
     * The loaded snapshot paired with a monotonically increasing revision. The revision is stamped
     * into [ArrivalsUiState.Content.dataVersion] so the UI can tell, purely from state, whether a
     * given load's data has reached its composition/layout yet. Paired in one object (not two flows)
     * so the [state] combine can never emit new data with an old version.
     */
    private data class VersionedData(val data: ArrivalsData, val version: Long)

    /** The latest fetched snapshot (null until the first load). */
    private val loaded = MutableStateFlow<VersionedData?>(null)

    private fun publish(data: ArrivalsData) {
        loaded.value = VersionedData(data, (loaded.value?.version ?: 0L) + 1L)
    }

    /** Set only when a load fails with nothing to show; cleared by any successful load. */
    private val fatalError = MutableStateFlow<String?>(null)

    /**
     * The UI state: the fetched [loaded] snapshot projected through the repository's hide/show
     * decisions. Those decisions are the store itself, observed as a flow — there is no in-memory
     * hidden mirror to keep in sync, so a hide/un-hide from *any* surface (the swipe gesture, the
     * alert dialog and its Undo, "show hidden alerts") is reflected here with nothing to reconcile.
     * See #1593.
     */
    val state: StateFlow<ArrivalsUiState> =
        combine(
            loaded, repository.alertHideState(), repository.favoriteRouteIds(), fatalError
        ) { data, hideState, favoriteRouteIds, error ->
            when {
                data != null -> data.data.toContent(hideState, favoriteRouteIds, data.version)
                error != null -> ArrivalsUiState.Error(error)
                else -> ArrivalsUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ArrivalsUiState.Loading)

    /**
     * Emits the map-relevant snapshot after each successful load. The map panel uses this to recenter
     * the map, move the FABs, and fire arrival-info tutorials without the ViewModel itself touching
     * Android. Empty for the standalone screen, which ignores it.
     */
    private val _arrivalsLoaded = MutableSharedFlow<ArrivalsLoaded>(extraBufferCapacity = 1)
    val arrivalsLoaded: SharedFlow<ArrivalsLoaded> = _arrivalsLoaded.asSharedFlow()

    /** Whether the stop-details dialog (the overflow "show stop details" action) is showing. */
    private val _stopDetailsVisible = MutableStateFlow(false)
    val stopDetailsVisible: StateFlow<Boolean> = _stopDetailsVisible.asStateFlow()

    private var minutesAfter = DefaultArrivalsRepository.MINUTES_AFTER_DEFAULT

    /** Wall-clock time of the last completed load, read by the screen's polling loop. */
    var lastResponseTime: WallTime = WallTime(0L)
        private set

    /**
     * Loads the arrivals once. Suspends until done so the screen's polling loop can measure the
     * 60s interval from completion. A failed refresh keeps any existing content (the repository
     * already returns the last good data as stale); it only surfaces [ArrivalsUiState.Error]
     * when there is nothing to show.
     *
     * Returns true when a *fresh* network response landed — a stale fallback (the repository
     * returns the last good data flagged `isStale` on failure-with-content) or a hard failure
     * returns false. Consumed by [loadMore]; the poll loop ignores it.
     */
    suspend fun refresh(): Boolean {
        val result = repository.getArrivals(stopId, minutesAfter)
        lastResponseTime = WallTime.now()
        return result.fold(
            onSuccess = { data ->
                minutesAfter = data.minutesAfter
                fatalError.value = null
                publish(data)
                repository.lastLoaded()?.let { _arrivalsLoaded.tryEmit(it) }
                !data.isStale
            },
            onFailure = { error ->
                if (loaded.value == null) fatalError.value = error.message.orEmpty()
                false
            }
        )
    }

    /** Refreshes from a user action (the toolbar refresh button or Retry). */
    fun manualRefresh() {
        viewModelScope.launch { refresh() }
    }

    private val _loadMoreState = MutableStateFlow<LoadMoreState>(LoadMoreState.Idle)

    /** The pull-to-load-more lifecycle; the ETA strip that fired the matching token drives its
     *  spinner/reveal from this. */
    val loadMoreState: StateFlow<LoadMoreState> = _loadMoreState.asStateFlow()

    private var nextLoadMoreToken = 0

    /** The in-flight load-more refresh, cancelled when a newer load-more supersedes it. */
    private var loadMoreJob: Job? = null

    /**
     * Widens the time window and reloads (the ETA strip's pull-past-end gesture). Returns the
     * request token; [loadMoreState] reports the request's lifecycle under that token.
     */
    fun loadMore(): Int {
        val token = ++nextLoadMoreToken
        minutesAfter += DefaultArrivalsRepository.MINUTES_AFTER_INCREMENT
        _loadMoreState.value = LoadMoreState.Loading(token)
        // Cancel any older load-more still in flight so its refresh() can't publish() after this one —
        // an earlier (narrower-window) response resolving last would otherwise roll the arrivals back.
        // A cancelled refresh dies on its (cooperative) network call before it reaches publish. Polls
        // and other refreshes are separate intents and are left alone.
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            // Safety guard ONLY (not happy-path logic): getArrivals is already bounded by OkHttp's
            // connect/read timeouts; this caps a pathological trickling response so Finished always
            // fires and a strip's spinner can't be pinned forever.
            val fresh = withTimeoutOrNull(LOAD_MORE_SAFETY_TIMEOUT_MS) { refresh() } ?: false
            // Publish completion only if a later loadMore hasn't superseded this request (its own
            // Finished will follow; the superseded strip tears down on seeing Loading(other)).
            if ((_loadMoreState.value as? LoadMoreState.Loading)?.token == token) {
                _loadMoreState.value =
                    LoadMoreState.Finished(token, fresh, loaded.value?.version ?: 0L)
            }
        }
        return token
    }

    /** Toggles the stop favorite, updating the header optimistically and persisting. */
    fun toggleFavorite() {
        val content = state.value as? ArrivalsUiState.Content ?: return
        val newValue = !content.header.isFavorite
        loaded.value?.let { publish(it.data.copy(header = content.header.copy(isFavorite = newValue))) }
        viewModelScope.launch { repository.setStopFavorite(content.header.stopId, newValue) }
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
     * Toggles the route star wholesale (#1751): stars the route if it isn't already (else unstars) and
     * backfills the route details. No reload — the star + drawer promotion re-flag reactively from the
     * repository's [ArrivalsRepository.favoriteRouteIds] overlay.
     */
    fun toggleRouteFavorite(actions: ArrivalActions) {
        val starred = (state.value as? ArrivalsUiState.Content)
            ?.favoriteRouteIds?.contains(actions.routeId) == true
        viewModelScope.launch {
            repository.favoriteRoute(
                routeId = actions.routeId,
                shortName = actions.routeShortName,
                longName = actions.routeLongName,
                favorite = !starred
            )
        }
    }

    /** Hides every currently active alert (the toolbar "hide alerts" action). The reactive [state]
     *  picks up the write with no refresh. */
    fun hideAllAlerts() {
        val ids = activeSituationIds()
        if (ids.isEmpty()) return
        viewModelScope.launch { repository.hideAlerts(ids) }
    }

    /** Un-hides every alert (the "show hidden alerts" affordance): records the active alerts as
     *  explicitly shown, which reveals them even under the "hide all alerts" preference. */
    fun showHiddenAlerts() {
        val ids = activeSituationIds()
        if (ids.isEmpty()) return
        viewModelScope.launch { repository.showAlerts(ids) }
    }

    /** Hides a single alert row (the swipe gesture): records every situation id folded into the row
     *  so a hide follows the content even as the feed rotates its id. See #1593. */
    fun hideAlert(alert: AlertItem) {
        viewModelScope.launch { repository.hideAlerts(alert.situationIds.toList()) }
    }

    /** Every situation id across the currently loaded active alerts (all grouped ids, deduped). */
    private fun activeSituationIds(): List<String> =
        loaded.value?.data?.activeAlerts.orEmpty().flatMapTo(mutableSetOf()) { it.situationIds }.toList()

    /** The service-alert dialog's content for an alert id (read from the last good response). */
    fun alertDetails(id: String): AlertDetails? = repository.alertDetails(id)

    /** Stamps the alert read when its dialog opens. */
    fun markAlertRead(id: String) {
        viewModelScope.launch { repository.markAlertRead(id) }
    }

    /** Hides a single alert by id (the dialog's Hide). */
    fun hideAlert(id: String) {
        viewModelScope.launch { repository.setAlertHidden(id, true) }
    }

    /** Un-hides a single alert by id (the dialog's Undo action). */
    fun unhideAlert(id: String) {
        viewModelScope.launch { repository.setAlertHidden(id, false) }
    }

    /** Hides every recorded alert (the dialog's Hide All). */
    fun hideAllRecordedAlerts() {
        viewModelScope.launch { repository.hideAllRecordedAlerts() }
    }

    private fun ArrivalsData.toContent(
        hideState: AlertHideState,
        favoriteRouteIds: Set<String>,
        version: Long,
    ): ArrivalsUiState.Content {
        val shown = activeAlerts.filterNot { hideState.isHidden(it, hideAlertsByDefault) }
        val hiddenCount = activeAlerts.size - shown.size
        return ArrivalsUiState.Content(
            header = header,
            dataVersion = version,
            arrivals = arrivals,
            // Lift starred routes to the top (each partition stays in departure order). Done here, off
            // the live favorite set, so a star toggle re-orders the list with no re-fetch (#1707/#1751).
            routeGroups = orderRouteGroupsByFavorite(routeGroups, favoriteRouteIds),
            minutesAfter = minutesAfter,
            isStale = isStale,
            actions = actions,
            favoriteRouteIds = favoriteRouteIds,
            alerts = shown,
            hiddenAlertCount = hiddenCount,
            routeDisplayNames = routeDisplayNames,
            stopCode = stopCode,
            stopLat = stopLat,
            stopLon = stopLon,
            stopUserName = stopUserName
        )
    }

    private companion object {
        /** Backstop on a pathological trickling response so [LoadMoreState.Finished] always fires;
         *  OkHttp's connect/read timeouts bound the normal failure paths well before this. */
        const val LOAD_MORE_SAFETY_TIMEOUT_MS = 30_000L
    }
}
