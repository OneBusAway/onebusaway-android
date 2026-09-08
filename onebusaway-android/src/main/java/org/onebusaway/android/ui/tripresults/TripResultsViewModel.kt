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
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.time.ServerTime

/**
 * Holds the trip-planning results: the option cards, which one is selected, and the selected
 * itinerary's directions. The [TripItinerary] objects are kept as opaque tokens and handed back to
 * the repository to re-summarize/re-generate directions on selection. [selectedItinerary] drives a
 * re-point of the declarative map when a different option is selected.
 */
@HiltViewModel
class TripResultsViewModel @Inject constructor(
    private val repository: TripResultsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<TripResultsUiState>(TripResultsUiState.Loading)
    val state: StateFlow<TripResultsUiState> = _state.asStateFlow()

    /** Emits the chosen itinerary so the screen can re-point the map, including on a re-tap. */
    private val _selectedItinerary = MutableSharedFlow<TripItinerary>(extraBufferCapacity = 1)
    val selectedItinerary: SharedFlow<TripItinerary> = _selectedItinerary.asSharedFlow()

    // The plan being shown, or null before the first seed. Held by identity — see [seedPlan].
    private var plan: List<TripItinerary>? = null
    private var selectedIndex: Int = 0
    private var plannedStart: ServerTime? = null

    /**
     * Seeds a new plan, or applies an explicit resume's [resumeIndex] even to the same cached plan.
     * A null index means there is no resume to consume: a new plan opens on option zero, while a
     * remounted sheet keeps the rider's selection. The return value tells the sheet to draw the chosen
     * option and consume the resume.
     *
     * Plans are compared by identity because a fresh plan can contain equal itineraries.
     */
    fun seedPlan(
        itineraries: List<TripItinerary>,
        resumeIndex: Int?,
        plannedStart: ServerTime? = null
    ): Boolean {
        if (itineraries === plan && resumeIndex == null) return false
        plan = itineraries
        selectedIndex = (resumeIndex ?: 0).coerceIn(0, (itineraries.size - 1).coerceAtLeast(0))
        this.plannedStart = plannedStart
        load()
        return true
    }

    /** The itinerary of the option currently selected, or null before any plan is seeded. */
    fun currentItinerary(): TripItinerary? = plan?.getOrNull(selectedIndex)

    /**
     * Tapping an option card re-points the map at its itinerary (framing the whole trip) — even when
     * that option is already selected, so a re-tap re-frames after the user has zoomed into a leg. Only
     * a *change* of option reloads its directions.
     */
    fun selectOption(index: Int) {
        val itineraries = plan ?: return
        if (index !in itineraries.indices) return
        val changed = index != selectedIndex
        selectedIndex = index
        _selectedItinerary.tryEmit(itineraries[index])
        if (changed) load()
    }

    private fun load() {
        viewModelScope.launch {
            repository.summarize(plan.orEmpty()).fold(
                onSuccess = { options ->
                    val selected = currentItinerary()
                    val directions = selected
                        ?.let { repository.directionsFor(it, plannedStart).getOrDefault(emptyList()) }
                        .orEmpty()
                    _state.value = TripResultsUiState.Success(
                        options = options,
                        selectedIndex = selectedIndex,
                        // Each leg carries its own alerts (#2143), attached as the log is built, so an
                        // alert can't lag or outlive the leg it is drawn under.
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
