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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.demo.DemoModeState
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
import org.onebusaway.android.map.rental.rentalMarkerLayer
import org.onebusaway.android.map.rental.rentalSource
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
@OptIn(ExperimentalCoroutinesApi::class)
class RentalLayerController(
    private val host: MapHost,
    private val rentalPlacesRepository: RentalPlacesRepository,
    private val prefsRepository: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val demoMode: DemoModeState,
    private val scope: CoroutineScope
) {

    // Defaults to nothing visible; the owner calls [syncFromPreferences] on resume (kept off the
    // construction path so the model stays JVM-constructible, like WeatherViewModel).
    private val visibleLayers = MutableStateFlow(emptySet<RentalLayer>())

    private var loadJob: Job? = null

    // The last response and the viewport that produced it — see [placesFor]. Confined to the loader
    // coroutine and the main-thread setters, so no synchronization.
    private var cachedViewport: CameraSnapshot? = null
    private var cachedSource: String? = null
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
     * Re-read what is on — after a toggle, and on resume.
     *
     * Both setters write their preference and come back through here rather than computing the new set
     * themselves, so the drawn layers always match what [rentalLayersFromPreferences] says: one
     * definition, no chance of the buttons and the map disagreeing.
     *
     * The owner calls this on resume rather than pushing a set in. It used to push one, computed by a
     * `LayerUtils` helper that answered *empty while no region was selected* — which on a fresh install
     * ran before region discovery landed and seeded this controller with nothing, permanently, while
     * the buttons (reading the preferences directly) drew themselves lit over a map that never loaded.
     * Whether there is a rental server to ask is this loader's question and it asks it on every
     * emission; a second, region-gated copy of the answer at seed time could only ever go stale.
     */
    fun syncFromPreferences() {
        visibleLayers.value = rentalLayersFromPreferences(prefsRepository)
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
                host.settledCamera(),
                visibleLayers,
                // The rental server itself is an input, not a fact read once inside the collector: a
                // region switch changes which endpoint answers, and neither the camera nor the toggles
                // need move for that to happen. Demo mode is the second such switch (#2164) — entering
                // and leaving it changes who answers without touching the region. Distinct so an
                // unrelated region field can't re-fire it.
                combine(regionRepository.region, demoMode.active) { _, _ -> rentalSourceKey() }
                    .distinctUntilChanged()
            ) { camera, layers, source -> Triple(camera, layers, source) }
                // collectLatest so a newer viewport cancels an in-flight load (the old loadJob?.cancel()).
                .collectLatest { (camera, layers, source) ->
                    if (source == null) {
                        // No server to ask. Clear rather than return: the markers on screen belong to
                        // whatever server answered last, and leaving them would show a region's
                        // vehicles over a region that has none.
                        clearRentals()
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
                        val places = placesFor(camera, source) ?: return@collectLatest
                        showRentals(places.forLayers(layers), layers, rentalsVisible = true)
                    }
                }
        }
    }

    /**
     * Which rental server this device would ask right now, as a cache/reload key — or null when it has
     * none, which is also the app's "no rental layer here" answer.
     *
     * A string rather than the [org.onebusaway.android.map.rental.RentalSource] itself so that both
     * halves of the question — is there a server, and *which* one — collapse into one comparable value
     * the flow can be distinct on.
     */
    private fun rentalSourceKey(): String? {
        // The demo transit system publishes its own rentals from the device (#2164), so it has a server
        // to ask wherever the rider is — including in a region with no bikeshare at all, and on a first
        // launch with no region resolved yet. Answered before the region gate rather than inside
        // [DemoRentalPlacesRepository], because a null here stops the loader before the repository is
        // ever reached: the tour's micromobility step would light its button over an empty map.
        if (demoMode.isActive) return DEMO_RENTAL_SOURCE
        val customUrl = prefsRepository.getString(R.string.preference_key_otp_api_url, null)
        if (!BikeshareAvailability.isStationLayerEnabled(regionRepository.currentRegion(), customUrl)) return null
        return rentalSource(
            customOtpApiUrl = customUrl,
            customUrlUsesGraphQl = prefsRepository.getBoolean(R.string.preference_key_otp_api_url_is_graphql, false),
            region = regionRepository.region.value
        )?.toString()
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
        cachedSource = null
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
     *
     * **And on [source]**, because a viewport comparing equal across a region switch would otherwise
     * redraw the previous server's vehicles: the box is the same, the answer is not.
     */
    private suspend fun placesFor(camera: CameraSnapshot, source: String): List<RentalPlace>? {
        cachedPlaces?.takeIf { cachedViewport == camera && cachedSource == source }?.let { return it }
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
        cachedSource = source
        cachedPlaces = places
        return places
    }

    /**
     * The places [layers] asks for. A place belonging to no layer at all — a rental car, an `OTHER` —
     * is dropped rather than drawn under a layer it isn't (see [rentalLayersOf]).
     */
    private fun List<RentalPlace>.forLayers(layers: Set<RentalLayer>): List<RentalPlace> = filter { place -> rentalLayersOf(place).any { it in layers } }

    private fun showRentals(places: List<RentalPlace>, enabled: Set<RentalLayer>, rentalsVisible: Boolean) {
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
                    RentalMarker(it.id, GeoPoint(it.latitude, it.longitude), it, rentalMarkerLayer(it, enabled))
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

    private companion object {
        /**
         * The source key demo mode answers under. Distinct from every real one (those are OTP URLs), so
         * entering or leaving the demo system invalidates the viewport cache in [placesFor] the same way
         * a region switch does, rather than redrawing the other deployment's vehicles.
         */
        const val DEMO_RENTAL_SOURCE = "demo"
    }
}
