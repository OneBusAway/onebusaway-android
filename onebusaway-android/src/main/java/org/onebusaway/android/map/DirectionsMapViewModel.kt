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

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.map.bike.BikeStationsRepository
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.opentripplanner.api.model.Itinerary

/**
 * The map view model for the **trip-plan directions** screen (trip results). It owns a [MapHost] + a
 * [DirectionsMapController] + a [BikeLayerController] and nothing else — no nearby-stops loader, no
 * route header / vehicle poll, no trip-focus overlay, no Home command bus. That keeps the trip-results
 * map from instantiating the home map's full machinery just to draw an itinerary.
 *
 * [showItinerary] draws the selected itinerary (and turns on its own bike-share stations) and frames
 * it — deferring the frame to the first camera-idle if the map adapter isn't attached yet (see
 * [MapHost.frameItinerary]), so the initial fit isn't lost when the map is composed behind the results
 * sheet on plan completion. Deps are constructor-injected by Hilt.
 */
@HiltViewModel
class DirectionsMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bikeStationsRepository: BikeStationsRepository,
    regionRepo: RegionRepository,
    locationRepository: LocationRepository,
    prefsRepository: PreferencesRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val mapHost = MapHost(
        scope = viewModelScope,
        savedStateHandle = savedStateHandle,
        regionRepo = regionRepo,
        locationRepository = locationRepository,
        prefsRepository = prefsRepository,
        context = context,
    )

    /** The shared map surface the flavor adapter binds to. */
    val host: MapHost get() = mapHost

    private val directionsController = DirectionsMapController(mapHost)

    private val bikeController = BikeLayerController(
        host = mapHost,
        bikeStationsRepository = bikeStationsRepository,
        prefsRepository = prefsRepository,
        regionRepository = regionRepo,
        scope = viewModelScope,
    )

    /** Draw [itinerary]'s legs + start/end pins and load its bike-share stations. */
    fun showItinerary(itinerary: Itinerary) {
        directionsController.clear()
        mapHost.renderState.clearRoutePolylines()
        directionsController.start(itinerary)
        bikeController.start(
            directions = true,
            selectedBikeStationIds = DirectionsMapController.bikeStationIdsFromItinerary(itinerary),
        )
    }
}
