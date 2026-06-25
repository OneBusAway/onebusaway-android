/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.dataview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * ViewModel for the trip trajectory debug screen. It keeps the trip's volatile vehicle status fresh
 * by collecting the repository's trip-details stream for the screen's lifetime, and rebuilds the
 * distance-vs-time [state] from the latest store snapshot whenever the screen ticks [refresh] (~1×
 * per second, driven by the screen so it's tied to the visible lifecycle, like TripDetails).
 */
@HiltViewModel
class TripTrajectoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TripObservationRepository,
) : ViewModel() {

    private val tripId: String = savedState.get<String>(NavRoutes.ARG_TRIP_ID)
        ?: throw IllegalStateException("tripId is required")

    private val _state = MutableStateFlow(TripTrajectoryUiState.empty(tripId))
    val state: StateFlow<TripTrajectoryUiState> = _state.asStateFlow()

    init {
        // Hydrate + keep the store fresh for the trip while the screen is open; refresh() reads it.
        viewModelScope.launch { repository.tripDetailsStream(tripId).collect { /* recorded by the repo */ } }
    }

    /** Rebuilds the graph from the latest store snapshot at [nowMs]. The screen ticks this ~1×/sec. */
    fun refresh(nowMs: Long = System.currentTimeMillis()) {
        val snapshot = repository.lookupTripState(tripId) ?: run {
            _state.value = TripTrajectoryUiState.empty(tripId)
            return
        }
        _state.value = TripTrajectoryUiState(
            tripId = tripId,
            vehicleId = snapshot.anchor?.vehicleId,
            sampleCount = snapshot.history.size,
            tripEnded = snapshot.vehicleActiveTripId != null && snapshot.vehicleActiveTripId != tripId,
            trajectory = buildTrajectory(snapshot, nowMs),
        )
    }
}
