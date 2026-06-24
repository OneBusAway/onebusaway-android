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
package org.onebusaway.android.ui.tripinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * State holder for the trip-reminder editor: loads the merged reminder data once, tracks the form
 * edits (name + reminder lead time), and runs the save through [TripInfoRepository], reporting the
 * outcome on [events].
 */
@HiltViewModel
class TripInfoViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TripInfoRepository,
) : ViewModel() {

    // Launch args arrive via SavedStateHandle — from the NavHost TRIP_INFO destination's nav-args, or
    // (for the standalone My-Reminders / arrivals "set reminder" launch paths) from the TripInfoActivity
    // facade's HomeActivity intent, which HomeActivity's translator turns into the same nav-args. Keyed
    // by the clean NavRoutes arg names. tripUri is recomputed from tripId/stopId by TripInfoArgs.
    private val args = TripInfoArgs(
        tripId = savedState.get<String>(NavRoutes.ARG_TRIP_ID).orEmpty(),
        stopId = savedState.get<String>(NavRoutes.ARG_STOP_ID).orEmpty(),
        routeId = savedState.get<String>(NavRoutes.ARG_ROUTE_ID),
        routeName = savedState.get<String>(NavRoutes.ARG_ROUTE_NAME),
        stopName = savedState.get<String>(NavRoutes.ARG_STOP_NAME),
        headsign = savedState.get<String>(NavRoutes.ARG_HEADSIGN),
        departTime = savedState.get<Long>(NavRoutes.ARG_DEPART_TIME) ?: 0,
        stopSequence = savedState.get<Int>(NavRoutes.ARG_STOP_SEQUENCE) ?: 0,
        serviceDate = savedState.get<Long>(NavRoutes.ARG_SERVICE_DATE) ?: 0,
        vehicleId = savedState.get<String>(NavRoutes.ARG_VEHICLE_ID),
    )

    private val _state = MutableStateFlow<TripInfoUiState>(TripInfoUiState.Loading)
    val state: StateFlow<TripInfoUiState> = _state.asStateFlow()

    private val _events = Channel<TripInfoEvent>(Channel.BUFFERED)
    val events: Flow<TripInfoEvent> = _events.receiveAsFlow()

    private var data: TripInfoData? = null

    init {
        viewModelScope.launch {
            val loaded = repository.load(args)
            data = loaded
            _state.value = TripInfoUiState.Content(
                stopName = loaded.stopNameText,
                routeName = loaded.routeText,
                headsign = loaded.headsignText,
                departureText = loaded.departureText,
                reminderOptions = loaded.reminderOptions,
                reminderSelection = REMINDER_MINUTES.indexOf(loaded.reminderMinutes)
                    .coerceIn(0, loaded.reminderOptions.size - 1),
                tripName = loaded.tripName,
                isNewTrip = loaded.isNewTrip
            )
        }
    }

    /** The route of this trip, once loaded — for the "Show route" action. */
    fun routeId(): String? = data?.routeId

    /** The stop's display name, once loaded — passed along to the arrivals screen. */
    fun stopName(): String? = data?.stopNameText

    fun setTripName(name: String) = updateContent { it.copy(tripName = name) }

    fun setReminderSelection(index: Int) = updateContent { it.copy(reminderSelection = index) }

    fun save() {
        val content = _state.value as? TripInfoUiState.Content ?: return
        val loaded = data ?: return
        if (content.isSaving) return
        updateContent { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = repository.save(
                args, loaded, REMINDER_MINUTES[content.reminderSelection], content.tripName
            )
            updateContent { it.copy(isSaving = false) }
            _events.send(if (saved) TripInfoEvent.Saved else TripInfoEvent.SaveFailed)
        }
    }

    private inline fun updateContent(transform: (TripInfoUiState.Content) -> TripInfoUiState.Content) {
        _state.update { (it as? TripInfoUiState.Content)?.let(transform) ?: it }
    }
}
