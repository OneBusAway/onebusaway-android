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
import kotlinx.coroutines.flow.StateFlow
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/**
 * The minimal map view model for screens that only show **nearby stops** on a plain map — the
 * trip-plan location picker (pan a crosshair, read the center) and the report issue screen (tap a
 * stop / drop a pin). It owns a [MapHost] + a [StopsMapController] and nothing else: no route header,
 * no vehicle polling, no directions, no trip-focus overlay, no Home command bus. That keeps these
 * screens from instantiating (and running) the home map's full machinery just to read a lat/lon.
 *
 * Stop mode starts at construction (there is no other mode to switch to). Deps are constructor-injected
 * by Hilt; tests can build it directly with fakes (the project's standard view-model pattern).
 */
@HiltViewModel
class StopsMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mapDataSource: MapDataSource,
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

    /** The live camera, published by the adapter on each idle (the picker reads its center on confirm). */
    val camera: StateFlow<CameraSnapshot?> get() = mapHost.camera

    private val stopsController = StopsMapController(
        host = mapHost,
        mapDataSource = mapDataSource,
        regionRepo = regionRepo,
        locationRepository = locationRepository,
        scope = viewModelScope,
    )

    init {
        stopsController.start()
    }

    /** A stop marker was tapped: render-focus it + center on it. */
    fun onStopTapped(stop: ObaStop) = stopsController.onStopTapped(stop)

    /** A tap away from any marker clears the stop render focus. */
    fun onMapTapped() = stopsController.clearStopFocus()

    /** Animate/move the camera to a point (the report screen's "recenter on the chosen location"). */
    fun centerOn(lat: Double, lon: Double, animate: Boolean) = mapHost.centerOn(lat, lon, animate)

    /** Drop a generic pin (the report screen's chosen-location marker); returns its id for removal. */
    fun addMarker(latitude: Double, longitude: Double, hue: Float?): Int =
        mapHost.addMarker(latitude, longitude, hue)

    fun removeMarker(id: Int) = mapHost.removeMarker(id)
}
