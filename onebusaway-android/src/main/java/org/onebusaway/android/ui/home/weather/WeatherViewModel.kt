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
package org.onebusaway.android.ui.home.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/**
 * The weather chip's state: the forecast (null until fetched), the hide-weather preference, and the
 * preferred temperature-units preference (null = never set → the formatter uses the locale default).
 */
data class WeatherUiState(
    val data: WeatherData? = null,
    val hidden: Boolean = false,
    val preferredTempUnits: String? = null,
)

/**
 * Owns the weather chip as a self-contained feature module (mirrors [org.onebusaway.android.ui.home.donation.DonationViewModel]/SurveyViewModel):
 * the region-keyed forecast fetch (once per region) + the hide-weather preference. Pulls weather out of
 * HomeViewModel/HomeUiState (the fetch in `onRegionValid`, the `weather` field, the
 * `HomeEnvironment.weatherHidden` gate). The NEARBY-tab gate stays in HomeScreen, like the other chrome;
 * the chip's tap (toast the summary) is handled in [WeatherFeature].
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepo: WeatherRepository,
    regionRepo: RegionRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    // Guard so the forecast is fetched once per region (not on every region emission).
    private var fetchedRegionId: Long? = null

    init {
        // Self-subscribe to the current region: fetch the forecast once per region id, clear when none.
        // Keyed on the region id, so weather follows the *region* (not the map viewport — panning out of
        // range no longer clears it). Replaces the host's MapFeature setRegion push.
        viewModelScope.launch {
            regionRepo.region.map { it?.id }.distinctUntilChanged().collect { setRegion(it) }
        }
        // Observe the hide-weather preference reactively (replaces the on-resume refreshHiddenPref poll).
        // The pref stores "weather enabled" (default true), so hidden = !enabled.
        viewModelScope.launch {
            preferencesRepository
                .observeBoolean(R.string.preference_key_display_weather_view, true)
                .collect { enabled -> _state.update { it.copy(hidden = !enabled) } }
        }
        // Observe the preferred temperature-units preference reactively so the chip re-renders when the
        // user changes units, and the DI lookup stays out of the composable body (was read ad hoc via
        // PreferencesEntryPoint in formatTemperature on each recomposition).
        viewModelScope.launch {
            preferencesRepository
                .observeString(R.string.preference_key_preferred_temperature_units, null)
                .collect { units -> _state.update { it.copy(preferredTempUnits = units) } }
        }
    }

    /** Fetch the forecast once per region, or clear it when [regionId] is null. */
    private fun setRegion(regionId: Long?) {
        if (regionId == null) {
            fetchedRegionId = null
            _state.update { it.copy(data = null) }
            return
        }
        if (fetchedRegionId == regionId) {
            return
        }
        fetchedRegionId = regionId
        viewModelScope.launch {
            weatherRepo.currentForecast(regionId).onSuccess { data ->
                _state.update { it.copy(data = data) }
            }
        }
    }
}
