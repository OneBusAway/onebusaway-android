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
package org.onebusaway.android.ui.home.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/** The map-chrome visibility gates: which FABs/controls show over the map, derived from prefs + region. */
data class MapChromeState(
    val zoomControls: Boolean = false,
    val leftHand: Boolean = false,
    val layersFab: Boolean = false,
    val bikeshareActive: Boolean = false,
)

/**
 * The map-chrome gates as a self-contained feature module (mirrors [org.onebusaway.android.ui.home.weather.WeatherViewModel]):
 * the show-zoom-controls / left-hand-mode / bikeshare-layer prefs + the region-derived bikeshare-enabled
 * predicate, pulled out of HomeViewModel/HomeUiState (the former `HomeEnvironment` collector + chrome
 * gates). [MapFeature] obtains this via `hiltViewModel()` scoped to the HOME nav entry — a
 * deliberate lighter alternative to the activity-scoped+passed-down weather/donation/survey wiring, since
 * the gates are pure derived state with no lifecycle to coordinate.
 */
@HiltViewModel
class MapChromeViewModel @Inject constructor(
    prefsRepo: PreferencesRepository,
    regionRepo: RegionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MapChromeState())
    val state: StateFlow<MapChromeState> = _state.asStateFlow()

    init {
        // Self-collect the chrome-gate inputs from their reactive sources. All writers go through the
        // DataStore-backed PreferencesRepository, so a pref change re-derives the gates with no host push.
        viewModelScope.launch {
            combine(
                prefsRepo.observeBoolean(R.string.preference_key_show_zoom_controls, false),
                prefsRepo.observeBoolean(R.string.preference_key_left_hand_mode, false),
                prefsRepo.observeBoolean(R.string.preference_key_layer_bikeshare_visible, true),
                regionRepo.region,
                prefsRepo.observeString(R.string.preference_key_otp_api_url, null),
            ) { zoomControls, leftHand, bikeVisible, region, otpUrl ->
                // Reactive re-derivation of Application.isBikeshareEnabled() for this consumer, so the
                // gate tracks region + the OTP-URL pref instead of reading the Application static. The
                // same predicate still lives in Application.isBikeshareEnabled(), LayerUtils, and
                // MapViewModel's resume sync; converging them onto one shared reactive source is the
                // deferred LayerUtils TODO(D4) — keep these in sync until then.
                val bikeshareEnabled =
                    (region != null && region.supportsOtpBikeshare) || !otpUrl.isNullOrEmpty()
                MapChromeState(
                    zoomControls = zoomControls,
                    leftHand = leftHand,
                    layersFab = bikeshareEnabled,
                    bikeshareActive = bikeshareEnabled && bikeVisible,
                )
            }.distinctUntilChanged().collect { _state.value = it }
        }
    }
}
