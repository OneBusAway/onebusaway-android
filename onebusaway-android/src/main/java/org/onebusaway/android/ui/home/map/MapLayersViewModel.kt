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
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.map.MapFlavourCapabilities
import org.onebusaway.android.map.basemapFromPref
import org.onebusaway.android.map.onDemandDeployment
import org.onebusaway.android.map.onDemandSupportedFlow
import org.onebusaway.android.map.rental.RENTALS_VISIBLE_BY_DEFAULT
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.defaultVisible
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BikeshareAvailability

/**
 * The map layers sheet's state (spec §3.9), derived reactively from the five preferences, the region
 * and the on-demand support verdict. Writes go straight to the preferences; the rental loader
 * re-reads them through `MapViewModel.syncRentalLayersFromPreferences()` after each tap.
 */
@HiltViewModel
class MapLayersViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    regionRepo: RegionRepository,
    private val onDemandSupport: OnDemandSupport,
    demoMode: DemoModeState,
    capabilities: MapFlavourCapabilities
) : ViewModel() {

    private val _state = MutableStateFlow(MapLayersUiState.EMPTY)
    val state: StateFlow<MapLayersUiState> = _state.asStateFlow()

    // Bumped by [refresh] so a 404 recorded since the last emission is noticed when the sheet opens.
    private val refreshTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val layerPrefs = combine(
                prefs.observeBoolean(R.string.preference_key_show_ondemand_zones, true),
                prefs.observeBoolean(R.string.preference_key_layer_bikeshare_visible, RENTALS_VISIBLE_BY_DEFAULT),
                prefs.observeBoolean(R.string.preference_key_layer_bikes_visible, RentalLayer.BIKES.defaultVisible),
                prefs.observeBoolean(R.string.preference_key_layer_scooters_visible, RentalLayer.SCOOTERS.defaultVisible),
                prefs.observeString(R.string.preference_key_basemap, null)
            ) { zones, master, bikes, scooters, basemap -> LayerPrefs(zones, master, bikes, scooters, basemapFromPref(basemap)) }
            // PF-13: the transit tile follows the observable absent set rather than a one-shot
            // isKnownUnsupported read, so a 404 recorded after this flow already emitted still hides it
            // (spec §2.10) instead of waiting for the next unrelated recomposition.
            val onDemandTileVisible = onDemandSupportedFlow(onDemandDeployment(regionRepo, prefs, demoMode), onDemandSupport.absent)
            combine(
                layerPrefs,
                onDemandTileVisible,
                regionRepo.region.combine(demoMode.active) { region, demo -> region to demo },
                prefs.observeString(R.string.preference_key_otp_api_url, null),
                refreshTick
            ) { current, tileVisible, (region, demo), otpUrl, _ ->
                layersUiState(
                    prefs = current,
                    onDemandTileVisible = tileVisible,
                    rentalsEnabled = demo || BikeshareAvailability.isStationLayerEnabled(region, otpUrl),
                    hasBasemapChoice = capabilities.hasBasemapChoice
                )
            }.distinctUntilChanged().collect { _state.value = it }
        }
    }

    /** Re-derive the state — called when the sheet opens, since a 404 verdict arrives without a preference change. */
    fun refresh() {
        refreshTick.value++
    }

    fun toggle(id: LayerTileId) = write(current().toggled(id))

    fun setBasemap(basemap: Basemap) = prefs.setString(R.string.preference_key_basemap, basemap.prefValue)

    /** Spec §3.9 Reset: zones on, rentals master off, bikes on, scooters off, basemap standard. */
    fun reset() = write(LayerPrefs.DEFAULTS)

    private fun current() = LayerPrefs(
        zones = prefs.getBoolean(R.string.preference_key_show_ondemand_zones, true),
        rentalsMaster = prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, RENTALS_VISIBLE_BY_DEFAULT),
        bikes = prefs.getBoolean(R.string.preference_key_layer_bikes_visible, RentalLayer.BIKES.defaultVisible),
        scooters = prefs.getBoolean(R.string.preference_key_layer_scooters_visible, RentalLayer.SCOOTERS.defaultVisible),
        basemap = basemapFromPref(prefs.getString(R.string.preference_key_basemap, null))
    )

    private fun write(next: LayerPrefs) {
        prefs.setBoolean(R.string.preference_key_show_ondemand_zones, next.zones)
        prefs.setBoolean(R.string.preference_key_layer_bikeshare_visible, next.rentalsMaster)
        prefs.setBoolean(R.string.preference_key_layer_bikes_visible, next.bikes)
        prefs.setBoolean(R.string.preference_key_layer_scooters_visible, next.scooters)
        prefs.setString(R.string.preference_key_basemap, next.basemap.prefValue)
    }
}
