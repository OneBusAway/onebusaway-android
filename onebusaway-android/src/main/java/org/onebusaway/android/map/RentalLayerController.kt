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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.RentalMarker
import org.onebusaway.android.map.rental.RentalDensity
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.RentalPlace
import org.onebusaway.android.map.rental.RentalPlacesRepository
import org.onebusaway.android.map.rental.isWithinRentalZoomGate
import org.onebusaway.android.map.rental.preferenceKey
import org.onebusaway.android.map.rental.rentalDensity
import org.onebusaway.android.map.rental.rentalLayersFromPreferences
import org.onebusaway.android.map.rental.rentalLayersOf
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BikeshareAvailability
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.toLocation

/**
 * The rental overlay — bikes and scooters (#2168), formerly the bikeshare-only `BikeLayerController`.
 * It **overlays every view** rather than being one: a cold driver over [MapHost] that loads rental
 * vehicles and docks for the current viewport whenever a layer is on. [start] re-launches the loader
 * for a view; [setRentalsVisible] and [setLayerVisible] drive the map's master button and its two mode
 * toggles; [stop] cancels, and [hide] additionally clears the map — what directions mode does.
 *
 * **The two layers share one fetch.** `vehicleRentalsByBbox` returns everything in the box regardless,
 * so turning both on costs exactly one request; the split happens in [rentalLayersOf] after the
 * response lands.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RentalLayerController(
    private val host: MapHost,
    private val rentalPlacesRepository: RentalPlacesRepository,
    private val prefsRepository: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val scope: CoroutineScope
) {

    // Defaults to nothing visible; the host pushes the real `LayerUtils.visibleRentalLayers()` value on
    // resume (kept off the construction path so the model stays JVM-constructible, like WeatherViewModel).
    private val visibleLayers = MutableStateFlow(emptySet<RentalLayer>())

    private var loadJob: Job? = null

    // The last response and the viewport that produced it — see [placesFor]. Confined to the loader
    // coroutine and the main-thread setters, so no synchronization.
    private var cachedViewport: CameraSnapshot? = null
    private var cachedPlaces: List<RentalPlace>? = null

    private val _loading = MutableStateFlow(false)

    /**
     * Whether a rental request is in flight — the spinner on the map's rental button.
     *
     * Every request, whoever triggered it: a tap that switched something on, and equally the reload a
     * settled pan or zoom fires. The layer can take seconds to answer over a wide box, and a map
     * quietly fetching while still showing the previous viewport's markers is worse than one that says
     * so.
     *
     * A redraw served from [placesFor]'s cache is not a request and never raises this — which is what
     * keeps the mode toggles from flashing it, since they don't reach the network at all.
     */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /**
     * Show or hide rentals entirely — the master button. The two mode toggles keep their own settings
     * while it is off, so flipping it back restores what the rider had rather than turning both on.
     */
    fun setRentalsVisible(visible: Boolean) {
        prefsRepository.setBoolean(R.string.preference_key_layer_bikeshare_visible, visible)
        syncFromPreferences()
    }

    /**
     * Show or hide one mode, under the master.
     *
     * Costs no request: the response already holds both modes, so this redraws from cache (see
     * [placesFor]). It reaches the network only if the viewport moved since the last fetch.
     */
    fun setLayerVisible(layer: RentalLayer, visible: Boolean) {
        prefsRepository.setBoolean(layer.preferenceKey, visible)
        syncFromPreferences()
    }

    /**
     * Re-read what is on. Both setters write their preference and then come back through here rather
     * than computing the new set themselves, so the drawn layers always match what
     * [rentalLayersFromPreferences] says — one definition, no chance of the button and the map
     * disagreeing.
     */
    private fun syncFromPreferences() {
        visibleLayers.value = rentalLayersFromPreferences(prefsRepository)
    }

    /** Apply the layer set directly — the resume-time sync from preferences. */
    fun setVisibleLayers(layers: Set<RentalLayer>) {
        visibleLayers.value = layers
    }

    /**
     * (Re)start the rental loader. The home-map layer toggles gate what it draws.
     *
     * Directions mode does not call this — it calls [hide]. Rentals used to be forced on there and
     * filtered to the trip's own vehicles, which meant an itinerary drew rental markers the rider
     * could neither switch off nor account for; the trip's own bike legs already say where the vehicle
     * is picked up.
     */
    fun start() {
        loadJob?.cancel()
        loadJob = scope.launch {
            combine(
                // Settle on drag-end, matching the stop loader: hold the load until the gesture
                // settles so a pan fires one load at drag-end, not one per intermediate camera-idle.
                host.camera.filterNotNull().debounce(STOP_LOAD_DEBOUNCE_MS)
                    .filter { !host.cameraInteracting.value },
                visibleLayers
            ) { camera, layers -> camera to layers }
                // collectLatest so a newer viewport cancels an in-flight load (the old loadJob?.cancel()).
                .collectLatest { (camera, layers) ->
                    if (!BikeshareAvailability.isStationLayerEnabled(
                            regionRepository.currentRegion(),
                            prefsRepository.getString(R.string.preference_key_otp_api_url, null)
                        )
                    ) {
                        return@collectLatest
                    }
                    if (layers.isEmpty()) {
                        // Switching the layers off retracts the guardrail notice with them: "zoom in
                        // to see bikes and scooters" is nonsense once the rider has said they don't
                        // want to see them.
                        clearRentals()
                    } else {
                        // The zoom gate comes *before* the request: `vehicleRentalsByBbox` has no
                        // server-side limit, so a region-wide viewport must cost no round trip at all
                        // rather than 13k entities we then throw away.
                        if (!isWithinRentalZoomGate(camera.latSpan)) {
                            clearRentals()
                            host.setRentalsNeedCloserZoom(true)
                            return@collectLatest
                        }
                        val places = placesFor(camera) ?: return@collectLatest
                        showRentals(places.forLayers(layers), rentalsVisible = true)
                    }
                }
        }
    }

    /** Leave the map with no rentals on it and the loader off — what directions mode does. */
    fun hide() {
        stop()
        clearRentals()
    }

    fun stop() {
        loadJob?.cancel()
        loadJob = null
        host.setRentalsNeedCloserZoom(false)
        _loading.value = false
        // Drop the cache with the loader: the next `start` may be a different region or a different
        // rental server, and a viewport that merely *compares* equal to the cached one would then
        // redraw the old server's vehicles.
        cachedViewport = null
        cachedPlaces = null
    }

    /**
     * Every rental in [camera]'s box — from the last response when this is the same viewport we
     * already fetched, else a fresh request.
     *
     * **The mode toggles must not cost a request.** `vehicleRentalsByBbox` returns bikes *and*
     * scooters regardless of what is switched on, so which modes are drawn is a filter over a response
     * we already hold ([forLayers]); before this cache, flipping a mode re-entered the loader with the
     * same camera and refetched thousands of vehicles to show a subset of what was already on screen.
     * Switching views (nearby ↔ route ↔ directions) restarts the loader against the same viewport too,
     * and likewise now redraws from the cache.
     *
     * Keyed on the whole [CameraSnapshot] rather than a rounded centre: it is a data class, so an
     * unmoved camera compares equal, and any real movement misses and refetches — which is what should
     * happen, since the box changed.
     */
    private suspend fun placesFor(camera: CameraSnapshot): List<RentalPlace>? {
        cachedPlaces?.takeIf { cachedViewport == camera }?.let { return it }
        // Only a real request lights the button — the cache hit above returns without touching it.
        _loading.value = true
        val result = try {
            rentalPlacesRepository.getRentals(camera.southWest.toLocation(), camera.northEast.toLocation())
        } finally {
            // `finally`, so a load cancelled mid-flight can't strand the spinner lit: collectLatest
            // cancels this the moment the viewport moves again, and the next load lights it afresh.
            _loading.value = false
        }
        val places = result.getOrNull() ?: return null
        cachedViewport = camera
        cachedPlaces = places
        return places
    }

    /**
     * The places [layers] asks for. A place belonging to no layer at all — a rental car, an `OTHER` —
     * is dropped rather than drawn under a layer it isn't (see [rentalLayersOf]).
     */
    private fun List<RentalPlace>.forLayers(layers: Set<RentalLayer>): List<RentalPlace> = filter { place -> rentalLayersOf(place).any { it in layers } }

    private fun showRentals(places: List<RentalPlace>, rentalsVisible: Boolean) {
        when (val density = rentalDensity(places)) {
            is RentalDensity.TooMany -> {
                // Too dense to draw honestly (see RentalGuardrails): show none and ask for a tighter
                // viewport, rather than an arbitrary subset that would misreport the nearest vehicle.
                clearRentals()
                host.setRentalsNeedCloserZoom(true)
            }

            is RentalDensity.Draw -> {
                host.setRentalsNeedCloserZoom(false)
                val markers = density.places.map {
                    RentalMarker(it.id, GeoPoint(it.latitude, it.longitude), it)
                }
                // A refresh that found the same fleet in the same state produces an *equal* snapshot,
                // and a StateFlow doesn't re-emit an equal value — so the static layer isn't torn down
                // and an open detail window survives. That holds only while every type on this path
                // stays a value type: `RentalMarker`, `RentalPlace` and `MapRenderSnapshot` are all
                // data classes, and giving any of them identity would silently start re-rendering (and
                // closing the rider's window) on every poll.
                host.renderState.setRentals(markers, rentalsVisible)
            }
        }
    }

    private fun clearRentals() {
        host.renderState.clearRentals()
        host.setRentalsNeedCloserZoom(false)
    }
}
