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
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.MapFlavourCapabilities
import org.onebusaway.android.map.onDemandDeployment
import org.onebusaway.android.map.onDemandSupportedFlow
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BikeshareAvailability

/** The map-chrome visibility gates: which FABs/controls show over the map, derived from prefs + region. */
data class MapChromeState(
    val zoomControls: Boolean = false,
    val leftHand: Boolean = false,
    val layersFab: Boolean = false
)

/**
 * The map-chrome gates as a self-contained feature module (mirrors [org.onebusaway.android.ui.home.weather.WeatherViewModel]):
 * the show-zoom-controls / left-hand-mode / layers-button-shown prefs + the region-derived rental- and
 * on-demand-availability predicates, pulled out of HomeViewModel/HomeUiState (the former
 * `HomeEnvironment` collector + chrome gates). [MapFeature] obtains this via `hiltViewModel()` scoped to
 * the HOME nav entry — a deliberate lighter alternative to the activity-scoped+passed-down
 * weather/donation/survey wiring, since the gates are pure derived state with no lifecycle to coordinate.
 *
 * The layers button's own tint/badge and its rental/on-demand tile contents now live in
 * [MapLayersViewModel] (spec §3.9); this view model only decides whether the button shows at all.
 */
@HiltViewModel
class MapChromeViewModel @Inject constructor(
    prefsRepo: PreferencesRepository,
    regionRepo: RegionRepository,
    demoMode: DemoModeState,
    onDemandSupport: OnDemandSupport,
    capabilities: MapFlavourCapabilities
) : ViewModel() {

    private val _state = MutableStateFlow(MapChromeState())
    val state: StateFlow<MapChromeState> = _state.asStateFlow()

    init {
        // Self-collect the chrome-gate inputs from their reactive sources. All writers go through the
        // DataStore-backed PreferencesRepository, so a pref change re-derives the gates with no host push.
        viewModelScope.launch {
            // The button's own visibility is a preference independent of what it opens: a rider can hide
            // the control (long-press, or Settings) without that touching any layer's state, so showing
            // it again brings back exactly what it offered before.
            val buttonShown = prefsRepo.observeBoolean(R.string.preference_key_show_rental_button, true)
            // PF-13: the on-demand tile follows the observable absent set rather than a one-shot
            // isKnownUnsupported read, so a 404 recorded after this flow already emitted still turns the
            // button off (spec §2.10) instead of waiting on some unrelated recomposition.
            val onDemandTileVisible = onDemandSupportedFlow(onDemandDeployment(regionRepo, prefsRepo, demoMode), onDemandSupport.absent)
            combine(
                prefsRepo.observeBoolean(R.string.preference_key_show_zoom_controls, false),
                prefsRepo.observeBoolean(R.string.preference_key_left_hand_mode, false),
                buttonShown,
                // Paired rather than passed as a sixth flow so this stays inside the typed five-flow
                // overload — a sixth input would fall back to the untyped vararg form.
                regionRepo.region.combine(demoMode.active) { region, demo -> region to demo },
                onDemandTileVisible.combine(prefsRepo.observeString(R.string.preference_key_otp_api_url, null)) { tileVisible, otpUrl -> tileVisible to otpUrl }
            ) { zoomControls, leftHand, shown, (region, demoActive), (tileVisible, otpUrl) ->
                // Reactive re-derivation of rental availability for this consumer, tracking region +
                // the OTP-URL pref, so this stays a live flow while the trip-planning call sites
                // resolve theirs per-call from a Context.
                //
                // The demo transit system always publishes rentals (#2164): the scripted tutorial's
                // micromobility step has to have a button to point at whatever region the user is
                // actually in, and demo mode is exactly the promise that the tour's content is there.
                val rentalsEnabled = demoActive || BikeshareAvailability.isStationLayerEnabled(region, otpUrl)
                // Spec §3.9: the button reaches the on-demand tile in regions without bikeshare, and the
                // basemap control on the google flavour; it hides only when the sheet would be empty.
                MapChromeState(
                    zoomControls = zoomControls,
                    leftHand = leftHand,
                    layersFab = (rentalsEnabled || tileVisible || capabilities.hasBasemapChoice) && shown
                )
            }.distinctUntilChanged().collect { _state.value = it }
        }
    }
}
