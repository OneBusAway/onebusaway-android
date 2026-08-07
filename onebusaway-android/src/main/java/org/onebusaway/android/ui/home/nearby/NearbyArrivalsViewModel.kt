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
package org.onebusaway.android.ui.home.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import org.onebusaway.android.api.data.NearbyArrivals
import org.onebusaway.android.api.data.NearbyArrivalsDataSource
import org.onebusaway.android.api.data.NearbyArrivalsResult
import org.onebusaway.android.api.data.NearbyArrivalsSupport
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.region.RegionRepository

/**
 * The refresh cadence, matching every other live-ETA surface in the app
 * ([org.onebusaway.android.ui.arrivals.ArrivalsPolling], the starred-stops list, trip details).
 * Because the whole viewport is one request, this costs the same as a single focused stop does.
 */
internal const val NEARBY_REFRESH_PERIOD_MS = 60_000L

/**
 * The arrivals window this asks for. Deliberately the starred-stop list's 35 minutes rather than the
 * focused-stop default of 65: the window is the only lever on this response's size — the server's
 * `maxCount` truncates the *arrivals* list ordered by distance, so capping it drops the farthest bays
 * outright instead of trimming each one — and at a dense downtown transit centre 35 minutes is ~29 KB
 * gzipped against ~40 KB at 65, for departures further out than anyone standing at the stop is
 * waiting for. A product judgement, not a derived number (#2107).
 */
internal const val NEARBY_MINUTES_AFTER = 35

/** What the transit-centre drawer has to show for the current viewport. */
sealed interface NearbyArrivalsUiState {

    /** Not asking: outside the transit-centre zoom band, or the drawer isn't the sheet's subject. */
    data object Idle : NearbyArrivalsUiState

    /** A first query for this viewport is in flight and nothing has been shown yet. */
    data object Loading : NearbyArrivalsUiState

    /**
     * A resolved response. [arrivals] may hold none at all (a box with no stops, or none due inside
     * the window), which is why the sheet gates on rows rather than on this state.
     */
    data class Loaded(val arrivals: NearbyArrivals) : NearbyArrivalsUiState

    /** This region's server does not serve the endpoint; the drawer never engages here. */
    data object Unsupported : NearbyArrivalsUiState
}

/**
 * Reads every stop's arrivals in the current viewport, for the transit-centre drawer (#2107).
 *
 * Self-wiring feature module obtained through `hiltViewModel()` on the HOME nav entry, mirroring
 * [org.onebusaway.android.ui.home.map.MapChromeViewModel]. It is fed the settled viewport and the zoom
 * band by the host (which holds the map view model) and asks nothing of the map itself.
 *
 * Deliberately Android-free — no `Context`, no `ArrivalInfo` — so it is a plain JVM unit test, the same
 * discipline that keeps `HomeViewModel`'s tests off the device. Turning the response into display rows
 * needs a `Context` and happens in composition.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NearbyArrivalsViewModel @Inject constructor(
    private val dataSource: NearbyArrivalsDataSource,
    private val regionRepo: RegionRepository,
    private val support: NearbyArrivalsSupport
) : ViewModel() {

    private val viewport = MutableStateFlow<CameraSnapshot?>(null)
    private val band = MutableStateFlow(StopBand.FULL)

    // Whether the drawer is this sheet's subject. Focusing a stop hands the sheet to that stop's own
    // arrivals session, so this query stops rather than polling a list nobody can see.
    private val active = MutableStateFlow(false)

    // The last response, held across a viewport change so a pan updates the list in place instead of
    // emptying it (see [poll]). Written and read only from the single collector `state` builds below,
    // which flatMapLatest serializes, so a plain field needs no synchronization.
    private var lastLoaded: NearbyArrivalsUiState.Loaded? = null

    /**
     * The viewport's arrivals, re-queried on every settled camera inside the band and polled at
     * [NEARBY_REFRESH_PERIOD_MS] while collected.
     *
     * `WhileSubscribed` is what stops the poll when the screen goes away: the host collects with
     * `collectAsStateWithLifecycle`, so a backgrounded app unsubscribes and the loop is cancelled —
     * the same effect `ArrivalsPolling` gets from `repeatOnLifecycle`, without a composable.
     */
    val state: StateFlow<NearbyArrivalsUiState> =
        combine(viewport, band, active) { viewport, band, active ->
            // Only the transit-centre band asks. Read as an ordering, not equality, so a band added
            // above ROUTES keeps the drawer rather than silently switching it off (the same rule
            // stopRouteLabel follows).
            if (active && band >= StopBand.ROUTES) viewport else null
        }
            .distinctUntilChanged()
            .flatMapLatest { viewport ->
                // flatMapLatest cancels an in-flight query when a newer viewport arrives, matching the
                // stop loader's discipline; the data source is suspend + runCatchingCancellable, so the
                // cancellation propagates rather than being swallowed (#1908).
                if (viewport == null) {
                    // The gate closed (zoomed out, or another surface took the sheet). Drop the held
                    // rows so re-entering the band somewhere else doesn't flash the last centre's bays.
                    lastLoaded = null
                    flowOf(NearbyArrivalsUiState.Idle)
                } else {
                    poll(viewport)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS),
                NearbyArrivalsUiState.Idle
            )

    /** The settled viewport (see `MapHost.settledCamera`). */
    fun onViewportSettled(snapshot: CameraSnapshot) {
        viewport.value = snapshot
    }

    /** The map's current stop zoom band, read off the render snapshot rather than recomputed. */
    fun onStopBand(value: StopBand) {
        band.value = value
    }

    /** Whether the drawer is the sheet's current subject. */
    fun setActive(value: Boolean) {
        active.value = value
    }

    private fun poll(viewport: CameraSnapshot) = flow {
        // Keep showing what's already on screen while the new viewport loads. Re-emitting Loading here
        // would empty the drawer's rows, and the sheet gates its visibility on having rows — so every
        // pan would retract the drawer and slide it back up a request later. The held rows are at most
        // one viewport stale, and a pan inside a transit centre mostly re-reads the same bays.
        emit(lastLoaded ?: NearbyArrivalsUiState.Loading)
        while (coroutineContext.isActive) {
            val regionId = regionRepo.region.value?.id
            if (support.isKnownUnsupported(regionId)) {
                emit(NearbyArrivalsUiState.Unsupported)
                return@flow
            }
            when (val result = dataSource.arrivals(viewport, NEARBY_MINUTES_AFTER)) {
                is NearbyArrivalsResult.Loaded -> {
                    NearbyArrivalsUiState.Loaded(result.arrivals).also {
                        lastLoaded = it
                        emit(it)
                    }
                }
                NearbyArrivalsResult.Unsupported -> {
                    support.recordAbsent(regionId)
                    emit(NearbyArrivalsUiState.Unsupported)
                    return@flow
                }
                // Transient: hold the last good rows and say nothing. The rider is looking at ETAs that
                // are at most a minute stale, which beats blanking the drawer — and a timeout must never
                // read as "this region can't do this".
                is NearbyArrivalsResult.Failed -> lastLoaded?.let { emit(it) }
            }
            delay(NEARBY_REFRESH_PERIOD_MS)
        }
    }

    private companion object {
        // Ride out a configuration change without cancelling the poll and re-querying on the way back.
        const val SUBSCRIPTION_GRACE_MS = 5_000L
    }
}
