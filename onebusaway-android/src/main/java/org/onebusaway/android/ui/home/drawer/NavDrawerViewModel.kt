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
package org.onebusaway.android.ui.home.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.onebusaway.android.region.RegionRepository

/**
 * The nav drawer's region/feature gating as a self-contained feature module (mirrors
 * [org.onebusaway.android.ui.home.weather.WeatherViewModel]): which gated rows the drawer shows. Pulls
 * the gating out of HomeViewModel/HomeUiState. The drawer obtains this via `hiltViewModel()` scoped to
 * the HOME nav entry — a deliberate lighter alternative to the activity-scoped+passed-down wiring.
 *
 * [NavItemsRepository.availability] is a synchronous read whose only reactive input is the region (the
 * reminder push-id and the custom-OTP-url are read non-reactively at call time), so the availability is
 * re-pulled on each region-id change — preserving the prior HomeViewModel behavior exactly (no new
 * reactivity on those non-region inputs).
 */
@HiltViewModel
class NavDrawerViewModel @Inject constructor(
    navItemsRepo: NavItemsRepository,
    regionRepo: RegionRepository,
) : ViewModel() {

    // Seeded synchronously, then re-pulled on each region-id change. Uses the manual
    // collect-into-MutableStateFlow idiom (as WeatherViewModel) rather than a SharingStarted.Eagerly
    // stateIn, whose never-completing collector leaks across JVM unit tests.
    private val _availability = MutableStateFlow(navItemsRepo.availability())
    val availability: StateFlow<NavItemAvailability> = _availability.asStateFlow()

    init {
        viewModelScope.launch {
            regionRepo.region
                .distinctUntilChangedBy { it?.id }
                .collect { _availability.value = navItemsRepo.availability() }
        }
    }
}
