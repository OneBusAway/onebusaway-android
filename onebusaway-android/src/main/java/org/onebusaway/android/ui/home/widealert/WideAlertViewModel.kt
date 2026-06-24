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
package org.onebusaway.android.ui.home.widealert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.onebusaway.android.region.RegionRepository

/**
 * The region-wide GTFS alert as a self-contained feature module (mirrors
 * [org.onebusaway.android.ui.home.weather.WeatherViewModel]): streams the current region's wide alerts,
 * pulled out of HomeViewModel/HomeUiState. [flatMapLatest] cancels the prior region's stream when the
 * region changes (replacing the old `alertsJob`). [dismiss] clears the current alert until the next emit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WideAlertViewModel @Inject constructor(
    wideAlertsRepo: WideAlertsRepository,
    regionRepo: RegionRepository,
) : ViewModel() {

    private val _wideAlert = MutableStateFlow<WideAlert?>(null)
    val wideAlert: StateFlow<WideAlert?> = _wideAlert.asStateFlow()

    init {
        viewModelScope.launch {
            regionRepo.region.map { it?.id }.distinctUntilChanged().flatMapLatest { regionId ->
                if (regionId == null) flowOf(null) else wideAlertsRepo.wideAlerts(regionId.toString())
            }.collect { _wideAlert.value = it }
        }
    }

    /** The user dismissed the alert dialog (Dismiss, or after following "More info"). */
    fun dismiss() {
        _wideAlert.value = null
    }
}
