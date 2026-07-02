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
package org.onebusaway.android.map.render

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.ObaTripStatus
import org.opentripplanner.routing.bike_rental.BikeRentalStation

/** A geographic point, flavor-neutral (carries no Google/maplibre `LatLng` dependency). */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** A screen pixel position in the composition's root coordinate space (flavor-neutral). */
data class ScreenOffset(val x: Float, val y: Float)

/**
 * Projects a [GeoPoint] to a root-space screen position, or null if the map isn't laid out yet or the
 * point is off screen. A flavor map adapter publishes one via [MapRenderState.setProjector]; map-SDK-
 * agnostic callers (e.g. the onboarding spotlight) use it without depending on Google/maplibre.
 */
fun interface MapProjector {
    fun toScreen(point: GeoPoint): ScreenOffset?
}

/**
 * The map's content padding, in pixels. [topPx] keeps content (vehicle markers) below the route-mode
 * header; [bottomPx] keeps the focused stop above the arrivals sheet. Held as declarative state and
 * applied by the renderer (the Google adapter's `GoogleMap(contentPadding=…)`) instead of an
 * imperative `mapView.setPadding(...)` poke. Kept in its own flow (not [MapRenderSnapshot]) so a
 * padding change doesn't recompose the overlay content.
 */
data class MapPadding(val topPx: Int = 0, val bottomPx: Int = 0)

/** The route line color a renderer falls back to when a [RoutePolyline] carries no color of its own. */
const val DEFAULT_ROUTE_LINE_COLOR: Int = 0xFF0000FF.toInt()

/**
 * One route/itinerary polyline: an ordered list of points and an optional [color]. The directional-arrow
 * stamp is a rendering detail added by the flavor renderer, so it isn't part of the state. A null [color]
 * means "use the [DEFAULT_ROUTE_LINE_COLOR]" — choosing the fallback is a display decision, so producers
 * can pass a route's raw (possibly absent) GTFS color straight through and renderers draw [resolvedColor].
 * [widthDp] overrides the renderer's default line width when set (the trip-focus map draws a thicker line).
 */
data class RoutePolyline(val color: Int?, val points: List<GeoPoint>, val widthDp: Float? = null) {
    /** The [color] to draw, applying the [DEFAULT_ROUTE_LINE_COLOR] fallback in one place for every renderer. */
    val resolvedColor: Int get() = color ?: DEFAULT_ROUTE_LINE_COLOR
}

/**
 * A scheduled-stop dot on the trip-focus map: a small disc placed at a stop's position along the trip
 * shape (distinct from the route-mode [StopMarker]'s direction-anchored icon). [selected] draws the
 * filled-center variant.
 */
data class TripStopDot(val point: GeoPoint, val selected: Boolean = false)

/**
 * A generic pin added by code outside the map package (trip-plan start/end, the report location
 * picker). [hue] is a Google `BitmapDescriptorFactory` hue in [0, 360), or null for the default pin.
 */
data class GenericMarker(val point: GeoPoint, val hue: Float?)

/**
 * One real-time vehicle marker. [status] is the raw io/elements status (the renderer derives the
 * icon, color, and info-window text from it, paired with the shared [MapVehicles.response]);
 * [activeTripId] is the stable key used for marker identity + animation; [isRealtime] is the
 * populate-time decision (last-known location present + predicted) that selects the live-vs-scheduled icon.
 */
data class VehicleMarker(
    val activeTripId: String,
    val point: GeoPoint,
    val isRealtime: Boolean,
    val status: ObaTripStatus,
    // The latest fix's instant — constant between fixes (so [point] is extrapolated forward from it),
    // changing when fresh AVL data arrives. The renderer animates the marker across the fix jump.
    val fixTimeMs: Long = 0L,
    // The vehicle's movement bearing along the route shape at [point] (compass degrees, 0°=N), so the
    // direction arrow tracks the glide. [Float.NaN] off-shape: the renderer falls back to the orientation.
    val bearing: Float = Float.NaN,
)

/**
 * One bike-rental marker. [station] is the raw OTP pojo (the renderer reads its name/availability for
 * the info window and its floating-vs-station flag for the icon). [bikeshareVisible] on the snapshot
 * is the layer/directions-mode gate; the per-zoom icon band is chosen live by the renderer.
 */
data class BikeMarker(
    val id: String,
    val point: GeoPoint,
    val isFloatingBike: Boolean,
    val station: BikeRentalStation,
)

/**
 * One bus-stop marker. [direction]/[routeType] choose the icon + anchor; [stop] is the raw pojo
 * couriered so a tap can notify focus listeners. Whether this stop renders focused (the 1.5x icon) is
 * decided by [MapRenderSnapshot.focusedStopId], not stored here, so focusing is a one-field change.
 */
data class StopMarker(
    val id: String,
    val point: GeoPoint,
    val direction: String,
    val routeType: Int,
    val stop: ObaStop,
)

/** Immutable snapshot of everything the map should render. Grows one overlay per phase. */
data class MapRenderSnapshot(
    val routePolylines: List<RoutePolyline> = emptyList(),
    val genericMarkers: Map<Int, GenericMarker> = emptyMap(),
    val bikeStations: List<BikeMarker> = emptyList(),
    val bikeshareVisible: Boolean = false,
    val stops: List<StopMarker> = emptyList(),
    // The currently focused stop id, couriered so the vehicle info-window's "more info" tap can deep
    // link into TripDetails scoped to that stop (the legacy VehicleOverlay.Controller hook).
    val focusedStopId: String? = null,
    // The zoom band the stops render in (full directional icon vs small dot). Derived from the camera
    // by StopsMapController and carried here so a pure zoom re-fires the renderer like any other
    // snapshot change — keeping the renderer a pure function of the snapshot (no live camera reads).
    val stopBand: StopBand = StopBand.FULL,
)

/**
 * The route-mode vehicle layer: the markers and the [response] their icons/info-windows derive from.
 * Produced on demand by a [FrameSampler]: the renderer pulls a fresh frame (re-extrapolated to the
 * frame's clock) each display frame, so the per-frame motion lives in the UI's frame loop rather than a
 * ~20×/second push. The selected vehicle is tracked separately (see [MapRenderState.selectedVehicleTripId]).
 */
data class MapVehicles(
    val markers: List<VehicleMarker> = emptyList(),
    val response: RouteTrips? = null,
)

/**
 * Produces a renderable frame [T] — the trip-focus overlay or the live vehicle layer — for a frame
 * time in the **device** clock domain, or null when there's nothing to draw. A flavor renderer pulls
 * one each display frame; a controller installs it on entering its view and clears it (null) on
 * leaving, so a non-null sampler doubles as the "view active" signal. This keeps the frame cadence in
 * the renderer's frame loop rather than a push from the producer.
 *
 * A plain function type: a return-position generic is already covariant, so no `out` is needed, and a
 * controller installs one as a `{ nowMs -> … }` lambda.
 */
typealias FrameSampler<T> = (nowMs: Long) -> T?

/**
 * Flavor-neutral, declarative model of the map's overlay content. Use-case controllers mutate this
 * model instead of touching the map SDK directly; each flavor's imperative renderer observes
 * [snapshot] and draws it onto its map (`GoogleMapRenderer` on the Google flavor, `MapLibreRenderer`
 * on maplibre — both native markers/polylines).
 *
 * It lives in `src/main` so both flavors share a single model. Mutators are plain methods (rather
 * than Kotlin properties) so the Java hosts can call them idiomatically.
 */
class MapRenderState {

    private val _snapshot = MutableStateFlow(MapRenderSnapshot())

    val snapshot: StateFlow<MapRenderSnapshot> = _snapshot.asStateFlow()

    // Map content padding (route-header top + arrivals-sheet bottom), in its own flow so a padding
    // change doesn't redraw the overlay content. The renderer applies it (both flavors: map.setPadding).
    private val _padding = MutableStateFlow(MapPadding())

    val padding: StateFlow<MapPadding> = _padding.asStateFlow()

    fun setTopPadding(px: Int) = _padding.update { it.copy(topPx = px) }

    fun setBottomPadding(px: Int) = _padding.update { it.copy(bottomPx = px) }

    // The live lat/lng -> screen projector, published by the flavor adapter once the map is laid out
    // (backed by the map's Projection). Lets map-SDK-agnostic callers locate a marker on screen without
    // touching the map SDK; null while the map isn't ready.
    private val _projector = MutableStateFlow<MapProjector?>(null)

    val projector: StateFlow<MapProjector?> = _projector.asStateFlow()

    fun setProjector(projector: MapProjector?) {
        _projector.value = projector
    }

    // One-shot camera intents a controller dispatches and the flavor adapter applies against its
    // imperative map (animateCamera/moveCamera). Buffered so the synchronous dispatch never suspends or
    // drops under a brief burst (e.g. the vehicle poll).
    private val _cameraCommands = MutableSharedFlow<CameraCommand>(extraBufferCapacity = 16)

    val cameraCommands: SharedFlow<CameraCommand> = _cameraCommands.asSharedFlow()

    fun dispatchCamera(command: CameraCommand) {
        _cameraCommands.tryEmit(command)
    }

    fun getRoutePolylines(): List<RoutePolyline> = _snapshot.value.routePolylines

    fun setRoutePolylines(polylines: List<RoutePolyline>) {
        _snapshot.update { it.copy(routePolylines = polylines) }
    }

    fun clearRoutePolylines() = setRoutePolylines(emptyList())

    // --- Generic markers (the old SimpleMarkerOverlay): monotonic int IDs the caller keeps to ---
    // --- remove the marker later. Unlike the old overlay, these survive until the map renders, ---
    // --- so addMarker never has to return a "not ready" sentinel. ---

    // Like every mutator here, addMarker/removeMarker are called only from the main thread (VM/controller
    // methods driven by the UI), so the nextMarkerId increment needs no synchronization and StateFlow.update
    // handles the snapshot atomicity.
    private var nextMarkerId = 0

    fun addMarker(point: GeoPoint, hue: Float?): Int {
        val id = nextMarkerId++
        _snapshot.update { it.copy(genericMarkers = it.genericMarkers + (id to GenericMarker(point, hue))) }
        return id
    }

    fun removeMarker(id: Int) {
        _snapshot.update { it.copy(genericMarkers = it.genericMarkers - id) }
    }

    // --- Vehicles (the old VehicleOverlay): the renderer pulls a live marker frame from this sampler ---
    // --- each display frame (re-extrapolated to the frame's clock), so per-frame motion lives in the ---
    // --- UI's frame loop, not a ~20Hz push. Null => not in route mode (the renderer draws nothing). ---

    private val _vehiclesSampler = MutableStateFlow<FrameSampler<MapVehicles>?>(null)

    val vehiclesSampler: StateFlow<FrameSampler<MapVehicles>?> = _vehiclesSampler.asStateFlow()

    fun setVehiclesSampler(sampler: FrameSampler<MapVehicles>?) {
        _vehiclesSampler.value = sampler
    }

    // The selected vehicle (its most-recent-data marker is shown), set by a tap and independent of the
    // per-frame marker sampling, so it sits in its own flow.
    private val _selectedVehicleTripId = MutableStateFlow<String?>(null)

    val selectedVehicleTripId: StateFlow<String?> = _selectedVehicleTripId.asStateFlow()

    // The trip-focus estimate marker whose tap label is open ("best"/"fast"/"dataAge"), or null. Set by
    // a marker tap, cleared by a map tap — the trip map's only UI selection.
    private val _selectedTripMarker = MutableStateFlow<String?>(null)

    val selectedTripMarker: StateFlow<String?> = _selectedTripMarker.asStateFlow()

    fun setSelectedTripMarker(marker: String?) {
        _selectedTripMarker.value = marker
    }

    // --- Bike stations (the old BikeStationOverlay): the per-zoom icon band is chosen by the ---
    // --- renderer; [bikeshareVisible] carries the layer/directions-mode gate. ---

    fun setBikeStations(stations: List<BikeMarker>, bikeshareVisible: Boolean) {
        _snapshot.update { it.copy(bikeStations = stations, bikeshareVisible = bikeshareVisible) }
    }

    fun clearBikeStations() {
        _snapshot.update { it.copy(bikeStations = emptyList()) }
    }

    // --- Stops: the host owns accumulation/cap + focus; this just holds the current list + id. ---

    fun setStops(stops: List<StopMarker>) {
        _snapshot.update { it.copy(stops = stops) }
    }

    fun setFocusedStopId(stopId: String?) {
        _snapshot.update { it.copy(focusedStopId = stopId) }
    }

    /** Sets the stop zoom band (full icon vs dot); a no-op emission when unchanged (StateFlow dedups). */
    fun setStopBand(band: StopBand) {
        _snapshot.update { it.copy(stopBand = band) }
    }

    /** Selects (or deselects with null) a vehicle by trip id; the renderer shows its data marker. */
    fun setSelectedVehicle(tripId: String?) {
        _selectedVehicleTripId.value = tripId
    }

    // --- Trip-focus overlay (the speed-estimation trip map): the renderer pulls the extrapolated ---
    // --- vehicle + uncertainty band from this sampler each display frame. Kept separate from the ---
    // --- snapshot so per-frame sampling doesn't recompose stops/routes. Null whenever the map isn't ---
    // --- in trip-focus mode. ---

    private val _tripOverlaySampler = MutableStateFlow<FrameSampler<TripOverlay>?>(null)

    val tripOverlaySampler: StateFlow<FrameSampler<TripOverlay>?> = _tripOverlaySampler.asStateFlow()

    fun setTripOverlaySampler(sampler: FrameSampler<TripOverlay>?) {
        _tripOverlaySampler.value = sampler
    }

    // The trip-focus map's scheduled-stop dots — static for the trip (set once on entry), so they sit
    // in their own flow rather than the ~20Hz tripOverlay.
    private val _tripStops = MutableStateFlow<List<TripStopDot>>(emptyList())

    val tripStops: StateFlow<List<TripStopDot>> = _tripStops.asStateFlow()

    fun setTripStops(stops: List<TripStopDot>) {
        _tripStops.value = stops
    }

    fun clearTripStops() {
        _tripStops.value = emptyList()
    }
}
