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
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.defaultVisible
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BikeshareAvailability

/** The map-chrome visibility gates: which FABs/controls show over the map, derived from prefs + region. */
data class MapChromeState(
    val zoomControls: Boolean = false,
    val leftHand: Boolean = false,
    val layersFab: Boolean = false,
    val rentalsActive: Boolean = false,
    /** The two mode toggles, shown only while [rentalsActive]. */
    val bikesActive: Boolean = false,
    val scootersActive: Boolean = false
)

/**
 * The map-chrome gates as a self-contained feature module (mirrors [org.onebusaway.android.ui.home.weather.WeatherViewModel]):
 * the show-zoom-controls / left-hand-mode / rental-layer prefs + the region-derived rental-enabled
 * predicate, pulled out of HomeViewModel/HomeUiState (the former `HomeEnvironment` collector + chrome
 * gates). [MapFeature] obtains this via `hiltViewModel()` scoped to the HOME nav entry — a
 * deliberate lighter alternative to the activity-scoped+passed-down weather/donation/survey wiring, since
 * the gates are pure derived state with no lifecycle to coordinate.
 */
@HiltViewModel
class MapChromeViewModel @Inject constructor(
    prefsRepo: PreferencesRepository,
    regionRepo: RegionRepository,
    demoMode: DemoModeState
) : ViewModel() {

    private val _state = MutableStateFlow(MapChromeState())
    val state: StateFlow<MapChromeState> = _state.asStateFlow()

    init {
        // Self-collect the chrome-gate inputs from their reactive sources. All writers go through the
        // DataStore-backed PreferencesRepository, so a pref change re-derives the gates with no host push.
        viewModelScope.launch {
            // The three rental preferences are combined first so the outer combine stays inside the
            // typed five-flow overload — seven inputs would fall back to the untyped vararg form.
            val rentals = combine(
                // Off by default — see RENTALS_VISIBLE_BY_DEFAULT; this must agree with it, or the
                // button's tint and the drawn map disagree on a fresh install.
                prefsRepo.observeBoolean(R.string.preference_key_layer_bikeshare_visible, false),
                prefsRepo.observeBoolean(
                    R.string.preference_key_layer_bikes_visible,
                    RentalLayer.BIKES.defaultVisible
                ),
                prefsRepo.observeBoolean(
                    R.string.preference_key_layer_scooters_visible,
                    RentalLayer.SCOOTERS.defaultVisible
                )
            ) { master, bikes, scooters -> Triple(master, bikes, scooters) }
            // The button's own visibility is a fourth, independent preference: a rider can hide the
            // control (long-press, or Settings) without that touching which layers are switched on, so
            // showing it again brings back exactly what they had.
            val buttonShown = prefsRepo.observeBoolean(R.string.preference_key_show_rental_button, true)
            combine(
                prefsRepo.observeBoolean(R.string.preference_key_show_zoom_controls, false),
                prefsRepo.observeBoolean(R.string.preference_key_left_hand_mode, false),
                rentals.combine(buttonShown) { it, shown -> it to shown },
                // Paired rather than passed as a sixth flow so this stays inside the typed five-flow
                // overload, for the same reason the three rental preferences are combined above.
                regionRepo.region.combine(demoMode.active) { region, demo -> region to demo },
                prefsRepo.observeString(R.string.preference_key_otp_api_url, null)
            ) { zoomControls, leftHand, (rentalPrefs, shown), (region, demoActive), otpUrl ->
                val (master, bikes, scooters) = rentalPrefs
                // Reactive re-derivation of rental availability for this consumer, tracking region +
                // the OTP-URL pref, so this stays a live flow while the trip-planning call sites
                // resolve theirs per-call from a Context. This chrome drives the rental *map layer*
                // (its buttons), hence the station-layer question rather than the trip-planning one.
                //
                // The demo transit system always publishes rentals (#2164): the scripted tutorial's
                // micromobility step has to have a button to point at whatever region the user is
                // actually in, and demo mode is exactly the promise that the tour's content is there.
                val rentalsEnabled = demoActive || BikeshareAvailability.isStationLayerEnabled(region, otpUrl)
                val rentalsOn = rentalsEnabled && master
                MapChromeState(
                    zoomControls = zoomControls,
                    leftHand = leftHand,
                    layersFab = rentalsEnabled && shown,
                    rentalsActive = rentalsOn,
                    // Only meaningful while the master is on, which is also the only time the mode
                    // toggles are drawn — so their tint can't advertise a state that isn't in effect.
                    bikesActive = rentalsOn && bikes,
                    scootersActive = rentalsOn && scooters
                )
            }.distinctUntilChanged().collect { _state.value = it }
        }
    }
}
