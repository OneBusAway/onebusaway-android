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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** Wall-clock time of the last completed load, read by the screen's polling loop. */
    var lastResponseTimeMs: Long = 0L
        private set

    /**
     * Loads the trip details once. A failed refresh keeps any existing content (the repository
     * returns the last good data); it only surfaces [TripDetailsUiState.Error] when there is nothing
     * to show.
     */
    suspend fun refresh() {
        val result = repository.getTripDetails(tripId, stopId, scrollMode, destinationId)
        lastResponseTimeMs = System.currentTimeMillis()
        result.fold(
            onSuccess = { data -> _state.value = data.toContent() },
            onFailure = { error ->
                if (_state.value !is TripDetailsUiState.Content) {
                    _state.value = TripDetailsUiState.Error(error.message.orEmpty())
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

    /** The trip's route id, for the host's "show on map" action (null until the first load). */
    fun routeId(): String? = (_state.value as? TripDetailsUiState.Content)?.routeId

    /** The resolved transit-line color, for tinting the trip-map uncertainty band (0 until loaded). */
    fun lineColorArgb(): Int = (_state.value as? TripDetailsUiState.Content)?.lineColorArgb ?: 0

    /** The last good response, for the host to resolve stops when setting a destination reminder. */
    fun lastResponse() = repository.lastResponse()

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
        routeId = routeId,
        lineColorArgb = lineColorArgb
    )
}
