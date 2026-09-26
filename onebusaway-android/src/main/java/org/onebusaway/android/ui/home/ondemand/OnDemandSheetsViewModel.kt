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
package org.onebusaway.android.ui.home.ondemand

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.ondemand.LocalityResolver
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.PlannerQualification
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.qualifyingServices
import org.onebusaway.android.ondemand.sortedSoonestUsable
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

/** What the overlap picker shows (spec §3.5): the list, where it was probed, and the locality once known. */
data class PickerRequest(
    val matches: List<OnDemandMatch>,
    val probe: ProbePoint,
    /** True for the bar's long-press list (every match); false for the inside-only list. */
    val nearby: Boolean,
    val locality: String?
)

/** The planner fallback sheet's content (spec §3.8). */
sealed interface PlannerFallbackState {
    data class Loading(val origin: ProbePoint) : PlannerFallbackState
    data class Ready(val origin: ProbePoint, val originMatches: List<OnDemandMatch>, val qualification: PlannerQualification) : PlannerFallbackState
}

/**
 * The home screen's on-demand sheets: the overlap picker (and, from the planner, the fallback sheet).
 * Scoped to the HOME nav entry through `hiltViewModel()`, like `MapChromeViewModel`.
 */
@HiltViewModel
class OnDemandSheetsViewModel @Inject constructor(
    private val localityResolver: LocalityResolver,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _picker = MutableStateFlow<PickerRequest?>(null)
    val picker: StateFlow<PickerRequest?> = _picker.asStateFlow()

    /** Open the picker sorted by §2.6 at once; the locality follows when the geocoder answers in time. */
    fun openPicker(matches: List<OnDemandMatch>, probe: ProbePoint, nearby: Boolean) {
        val request = PickerRequest(matches.sortedSoonestUsable(), probe, nearby, locality = null)
        _picker.value = request
        viewModelScope.launch {
            val locality = localityResolver.locality(probe.point) ?: return@launch
            // Only the request still open for this probe takes the answer; a closed or replaced one ignores it.
            _picker.update { current -> if (current != null && current.probe == probe && current.locality == null) current.copy(locality = locality) else current }
        }
    }

    fun closePicker() {
        _picker.value = null
    }

    private val _planner = MutableStateFlow<PlannerFallbackState?>(null)
    val planner: StateFlow<PlannerFallbackState?> = _planner.asStateFlow()
    private var plannerJob: Job? = null

    /**
     * Probe both ends through [probeExact] (the exact-point cache) and qualify. An unanswerable end
     * reads as no matches, so the sheet says nothing covers both rather than failing.
     */
    fun openPlanner(origin: GeoPoint, destination: GeoPoint, probeExact: suspend (GeoPoint) -> List<OnDemandMatch>?) {
        val probe = ProbePoint(origin, ProbeSource.Point(null))
        plannerJob?.cancel()
        _planner.value = PlannerFallbackState.Loading(probe)
        plannerJob = viewModelScope.launch {
            val atOrigin = probeExact(origin) ?: emptyList()
            val atDestination = probeExact(destination) ?: emptyList()
            _planner.value = PlannerFallbackState.Ready(probe, atOrigin, qualifyingServices(atOrigin, atDestination))
        }
    }

    fun closePlanner() {
        plannerJob?.cancel()
        _planner.value = null
    }

    /** The device clock, minted here so the sheet's copy is evaluated at open time. */
    fun now(): Instant = Instant.ofEpochMilli(timeProvider.now())
}
