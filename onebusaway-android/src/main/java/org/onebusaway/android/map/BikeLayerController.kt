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
package org.onebusaway.android.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BikeshareAvailability
import org.onebusaway.android.map.bike.BikeAction
import org.onebusaway.android.map.bike.BikeStationsRepository
import org.onebusaway.android.map.bike.bikeAction
import org.onebusaway.android.map.bike.filterStations
import org.onebusaway.android.map.render.BikeMarker
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.preferences.PreferencesRepository
import org.opentripplanner.routing.bike_rental.BikeRentalStation

/**
 * The bikeshare overlay (the legacy `BikeshareMapController`). It **overlays every view** rather than
 * being one: a cold driver over [MapHost] that loads bike-rental stations for the current viewport
 * whenever the bikeshare layer is on (home map) or a directions itinerary references specific bike
 * stations. [start] re-launches the loader for a given view (the directions flag + its station filter);
 * [setBikeshareLayerVisible] toggles the home-map layer; [stop] cancels.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BikeLayerController(
    private val host: MapHost,
    private val bikeStationsRepository: BikeStationsRepository,
    private val prefsRepository: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val scope: CoroutineScope,
) {

    // Defaults off; the host pushes the real `LayerUtils.isBikeshareLayerVisible()` value on resume
    // (kept off the construction path so the model stays JVM-constructible, like WeatherViewModel).
    private val _bikeshareVisible = MutableStateFlow(false)

    private var loadJob: Job? = null

    fun setBikeshareLayerVisible(visible: Boolean, persist: Boolean = false) {
        // The user-driven toggle persists the choice through the seam; the startup sync (which reads
        // the pref) just applies it, so persistence stays opt-in.
        if (persist) {
            prefsRepository.setBoolean(R.string.preference_key_layer_bikeshare_visible, visible)
        }
        _bikeshareVisible.value = visible
    }

    /**
     * (Re)start the bike loader for the current mode. [directions] forces the stations on (the trip's
     * own bike-share stations), filtered to [selectedBikeStationIds]; otherwise the home-map layer
     * toggle gates them.
     */
    fun start(directions: Boolean, selectedBikeStationIds: List<String>?) {
        loadJob?.cancel()
        loadJob = scope.launch {
            combine(
                host.camera.filterNotNull().debounce(STOP_LOAD_DEBOUNCE_MS),
                _bikeshareVisible,
            ) { camera, layerVisible -> camera to layerVisible }
                // collectLatest so a newer viewport cancels an in-flight station load (the old loadJob?.cancel()).
                .collectLatest { (camera, layerVisible) ->
                    if (!BikeshareAvailability.isEnabled(
                            regionRepository.currentRegion(),
                            prefsRepository.getString(R.string.preference_key_otp_api_url, null),
                        )
                    ) {
                        return@collectLatest
                    }
                    when (bikeAction(directions, selectedBikeStationIds, layerVisible)) {
                        BikeAction.LEAVE -> {}
                        BikeAction.CLEAR -> clearBikeStations()
                        BikeAction.SHOW -> {
                            val stations = bikeStationsRepository
                                .getStations(camera.southWest.toLocation(), camera.northEast.toLocation())
                                .getOrNull() ?: return@collectLatest
                            filterStations(stations, selectedBikeStationIds)?.let {
                                showBikeStations(it, bikeshareVisible = directions || layerVisible)
                            }
                        }
                    }
                }
        }
    }

    fun stop() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun showBikeStations(stations: List<BikeRentalStation>, bikeshareVisible: Boolean) {
        val markers = stations.map {
            BikeMarker(it.id, GeoPoint(it.y, it.x), it.isFloatingBike, it)
        }
        host.renderState.setBikeStations(markers, bikeshareVisible)
    }

    private fun clearBikeStations() = host.renderState.clearBikeStations()
}
