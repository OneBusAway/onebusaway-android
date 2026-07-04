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
package org.onebusaway.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.RegionState

/**
 * The forced-choice region picker (and its catastrophic-failure sibling) as a self-contained reactive
 * feature (mirrors the other `regionRepo`-observing home VMs, e.g.
 * [org.onebusaway.android.ui.home.widealert.WideAlertViewModel]): region resolution lives entirely in
 * [RegionRepository], which raises [RegionState.NeedsManualChoice] whenever no region can be auto-selected
 * and [RegionState.Failed] when region info couldn't be loaded at all. This exposes the former as the
 * [picker] list (resolved via [choose]) and the latter as [failed] (retried via [retry]) — so both show
 * regardless of which screen triggered the refresh (they're rendered at the activity root), rather than
 * being a HomeViewModel/HomeUiState concern.
 */
@HiltViewModel
class RegionPickerViewModel @Inject constructor(
    private val regionRepo: RegionRepository,
) : ViewModel() {

    // The regions to choose from while resolution needs a manual pick, else null. Mirrors the manual
    // collect-into-MutableStateFlow idiom used by the other home feature VMs (WeatherViewModel /
    // SurveyViewModel) rather than a SharingStarted.Eagerly stateIn, whose never-completing collector
    // leaks across JVM unit tests.
    private val _picker = MutableStateFlow<List<Region>?>(null)
    val picker: StateFlow<List<Region>?> = _picker.asStateFlow()

    // Whether region info couldn't be loaded at all (catastrophic failure) — drives the retryable
    // "couldn't load regions" affordance. A resolving/active/manual-choice transition clears it.
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        // One collector drives both projections so the picker and the failure affordance can never
        // disagree about the current state.
        viewModelScope.launch {
            regionRepo.state.collect { state ->
                _picker.value = (state as? RegionState.NeedsManualChoice)?.regions
                _failed.value = state is RegionState.Failed
            }
        }
    }

    /** The user picked [region]: the repository applies it, which drives [picker] back to null. */
    fun choose(region: Region) {
        viewModelScope.launch { regionRepo.choose(region) }
    }

    /** Re-attempt resolution after a [failed] load; a success drives [failed] back to false. */
    fun retry() {
        viewModelScope.launch { regionRepo.refresh() }
    }
}
