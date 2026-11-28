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
package org.onebusaway.android.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.api.net.ObaRateLimit
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * ViewModel for the trip details screen. The 60-second polling loop lives in the screen (driven by
 * the activity lifecycle); this exposes [refresh] for it to call. The destination-reminder stop is
 * held here so the host can set it (after starting the reminder) or clear it (when the trip ends).
 */
@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TripDetailsRepository,
    private val rateLimit: ObaRateLimit,
    private val clock: ElapsedClock
) : ViewModel() {

    // Launch args arrive via SavedStateHandle — from the NavHost destination's nav-args, or from the
    // standalone TripDetailsActivity's Builder, both keyed by the same clean NavRoutes arg names.
    private val tripId: String = savedState.get<String>(NavRoutes.ARG_TRIP_ID)
        ?: throw IllegalStateException("TripId should not be null")
    private val stopId: String? = savedState.get<String>(NavRoutes.ARG_STOP_ID)
    private val scrollMode: String? = savedState.get<String>(NavRoutes.ARG_SCROLL_MODE)

    private val _state = MutableStateFlow<TripDetailsUiState>(TripDetailsUiState.Loading)
    val state: StateFlow<TripDetailsUiState> = _state.asStateFlow()

    // True only while a user-triggered refresh is in flight, so the toolbar can show a spinner. The
    // silent 60s background poll deliberately doesn't flip this.
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var destinationId: String? = savedState.get<String>(NavRoutes.ARG_DEST_ID)

    /**
     * When the last load completed, or null before the first. On the monotonic clock rather than the
     * wall clock: the polling loop measures a real interval from here ([earlyRetryAfter] is a few
     * seconds counted off the rate-limit gate's own monotonic clock), and a device clock change must
     * not stretch or cut it.
     */
    private var lastResponseAt: ElapsedTime? = null

    /** How long ago the last load completed, for the screen's polling loop; null before the first. */
    fun sinceLastResponse(): Duration? = lastResponseAt?.let { clock.now() - it }

    /**
     * How soon after the last response to poll again when a first load met a rate limit, instead of
     * the full period: until the server may be asked again (#2410), so a trip opened during a 429
     * cooldown loads when the cooldown ends rather than a minute later (#2416). Only after the first
     * rate-limited load in a row, and only while the gate is still closed and so says how long to
     * wait: one early retry per streak, so a limit that persists doesn't turn the page into a request
     * every cooldown (as on the arrivals board). Null otherwise.
     */
    var earlyRetryAfter: Duration? = null
        private set

    /** Whether the load before the latest one was refused by a rate limit, for [earlyRetryAfter]. */
    private var lastRefreshRateLimited = false

    /**
     * Loads the trip details once. A failed refresh keeps any existing content (the repository
     * returns the last good data); it only surfaces [TripDetailsUiState.Error] when there is nothing
     * to show. A first load refused by a rate limit stays [TripDetailsUiState.Loading] and sets
     * [earlyRetryAfter] instead, since the server is busy rather than the trip unavailable; if the
     * early retry is refused too, the error shows and the page goes back to the full period.
     */
    suspend fun refresh() {
        val result = repository.getTripDetails(tripId, stopId, scrollMode, destinationId)
        lastResponseAt = clock.now()
        val rateLimited = (result.exceptionOrNull() as? TripLoadException)?.rateLimited == true
        val nothingToShow = _state.value !is TripDetailsUiState.Content
        // A 429 slow enough to arrive after the gate reopened leaves no wait to read off it. That is a
        // server still refusing, not leave to ask again at once, so it gets the full period too.
        val retryAfter = if (rateLimited && !lastRefreshRateLimited && nothingToShow) rateLimit.rateLimitedFor() else null
        earlyRetryAfter = retryAfter
        lastRefreshRateLimited = rateLimited
        result.fold(
            onSuccess = { data -> _state.value = data.toContent() },
            onFailure = { error ->
                when {
                    // Replaces an earlier failure's error: the page is waiting, not broken.
                    retryAfter != null -> _state.value = TripDetailsUiState.Loading
                    nothingToShow -> _state.value = TripDetailsUiState.Error(error.message.orEmpty())
                }
            }
        )
    }

    /** Refreshes from a user action (the toolbar refresh button or Retry), showing the spinner. */
    fun manualRefresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Resolves (and persists) the before/destination stops for [position], for the reminder flow. */
    fun destinationStops(position: Int) = repository.destinationStops(position)

    /** The server time of the last loaded response, or null if none has loaded. */
    fun lastLoadedTime(): Long? = repository.lastLoadedTime()

    /**
     * Sets (or clears with null) the destination-reminder stop and reloads so the flag updates. The
     * host calls this after starting/cancelling a reminder.
     */
    fun setDestinationId(id: String?) {
        destinationId = id
        manualRefresh()
    }

    private fun TripDetailsData.toContent() = TripDetailsUiState.Content(
        header = header,
        stops = stops,
        scrollToIndex = scrollToIndex,
        lineColorArgb = lineColorArgb,
        mapRequest = mapRequest
    )
}
