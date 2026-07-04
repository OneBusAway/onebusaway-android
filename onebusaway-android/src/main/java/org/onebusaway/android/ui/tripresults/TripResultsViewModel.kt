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
package org.onebusaway.android.ui.tripresults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opentripplanner.api.model.Itinerary

/**
 * Holds the trip-planning results: the option cards, which one is selected, and the selected
 * itinerary's directions. The OTP [Itinerary] objects are kept as opaque tokens (they aren't
 * JVM-pure) and handed back to the repository to re-summarize/re-generate directions on selection.
 * [selectedItinerary] drives a re-point of the declarative map when a different option is selected.
 */
@HiltViewModel
class TripResultsViewModel @Inject constructor(
    private val repository: TripResultsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<TripResultsUiState>(TripResultsUiState.Loading)
    val state: StateFlow<TripResultsUiState> = _state.asStateFlow()

    /** Emits the selected index (and its itinerary) so the screen can re-point the map. */
    private val _selectedItinerary = MutableSharedFlow<Pair<Int, Itinerary>>(extraBufferCapacity = 1)
    val selectedItinerary: SharedFlow<Pair<Int, Itinerary>> = _selectedItinerary.asSharedFlow()

    private var itineraries: List<Itinerary> = emptyList()
    private var selectedIndex: Int = 0

    /** Seeds the results from a completed plan. [initialIndex] restores the prior option selection. */
    fun setItineraries(itineraries: List<Itinerary>, initialIndex: Int) {
        this.itineraries = itineraries
        this.selectedIndex = initialIndex.coerceIn(0, (itineraries.size - 1).coerceAtLeast(0))
        load()
    }

    /** Selects a different option card, reloading its directions and re-pointing the map. */
    fun selectOption(index: Int) {
        if (index == selectedIndex || index !in itineraries.indices) return
        selectedIndex = index
        itineraries.getOrNull(index)?.let { _selectedItinerary.tryEmit(index to it) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            repository.summarize(itineraries).fold(
                onSuccess = { options ->
                    val directions = itineraries.getOrNull(selectedIndex)
                        ?.let { repository.directionsFor(it).getOrDefault(emptyList()) }
                        .orEmpty()
                    _state.value = TripResultsUiState.Success(
                        options = options,
                        selectedIndex = selectedIndex,
                        directions = directions
                    )
                },
                onFailure = { error ->
                    _state.value = TripResultsUiState.Error(error.message.orEmpty())
                }
            )
        }
    }
}
