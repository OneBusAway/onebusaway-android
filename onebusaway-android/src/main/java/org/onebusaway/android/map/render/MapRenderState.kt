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

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.onebusaway.android.map.bike.BikeStation
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.util.GeoPoint

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
 * The map's content padding, in pixels. [topPx] keeps the compass, vehicle markers, and framed content
 * below the floating top chrome or the active route-focus control, whichever extends farther down;
 * [bottomPx] keeps the focused stop above the arrivals sheet. Held as declarative state and applied by
 * the renderer (the Google adapter uses `GoogleMap.setPadding`) instead of an
 * imperative `mapView.setPadding(...)` poke. Kept in its own flow (not [MapRenderSnapshot]) so a
 * padding change doesn't recompose the overlay content.
 */
data class MapPadding(val topPx: Int = 0, val bottomPx: Int = 0)

/** The route line color a renderer falls back to when a [RoutePolyline] carries no color of its own. */
const val DEFAULT_ROUTE_LINE_COLOR: Int = 0xFF0000FF.toInt()

/** Optional renderer-bound geometry transforms. Lines opt in explicitly; the default is pass-through. */
enum class RoutePolylineTransform {
    VIEWPORT_CLIP,
    ZOOM_SIMPLIFY
}

/**
 * One route/itinerary polyline: an ordered list of points and an optional [color]. A null [color]
 * means "use the [DEFAULT_ROUTE_LINE_COLOR]" — choosing the fallback is a display decision, so producers
 * can pass a route's raw (possibly absent) GTFS color straight through and renderers draw [resolvedColor].
 * [widthProfile] overrides the renderer's default line width when set, including its zoom-dependent
 * multiplier schedule.
 *
 * [directional] asks the flavor renderer to stamp travel-direction arrows/chevrons along the line — set
 * it only when [points] are ordered in the direction of travel and that orientation is meaningful (a
 * single trip/leg shape, or a route narrowed to one selected direction). The whole-route merged shape,
 * whose points aren't a single travel order, leaves it false so the line reads as undirected. Whether a
 * renderer can honor it is flavor-specific (Google stamps a chevron texture; the maplibre classic
 * annotation has no arrow support yet).
 *
 * [dashed] asks the renderer to draw a dashed stroke instead of solid — set it for a preview/hint line
 * that should read as distinct from the primary route shown (the route-continuation line, #1691), so it
 * doesn't blend into a busy basemap the way a plain solid stroke can.
 *
 * [transforms] opts this line into renderer-bound geometry processing. Canonical [points] stay intact in
 * [MapRenderState] for framing and other consumers; both native adapters apply the requested transforms
 * only to the list they render. An empty set is a strict pass-through.
 */
data class RoutePolyline(
    val color: Int?,
    val points: List<GeoPoint>,
    val widthProfile: RouteLineWidthProfile? = null,
    val directional: Boolean = false,
    val dashed: Boolean = false,
    val transforms: Set<RoutePolylineTransform> = emptySet()
) {
    /** The [color] to draw, applying the [DEFAULT_ROUTE_LINE_COLOR] fallback in one place for every renderer. */
    val resolvedColor: Int get() = color ?: DEFAULT_ROUTE_LINE_COLOR
}

/**
 * A generic pin added by code outside the map package (trip-plan start/end, the report location
 * picker). [hue] is a Google `BitmapDescriptorFactory` hue in [0, 360), or null for the default pin.
 */
data class GenericMarker(val point: GeoPoint, val hue: Float?)

/**
 * One real-time vehicle marker. [status] is the raw io/elements status (the renderer derives the
 * icon, color, and info-window text from it, paired with the shared [MapVehicles.response]);
 * [activeTripId] is the stable key used for marker identity + animation; [isRealtime] is the
 * draw-time decision (from whatever produced the drawn point — the extrapolation anchor or the current
 * status) that selects the live-vs-scheduled icon.
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
    // The last real fix's position on the route shape (the glide's seed), where the selected vehicle's
    // most-recent-data dot is drawn — so the dot sits at the band's origin, not the raw off-shape reported
    // lat/lng. Null when there's no shape/anchor; the renderer then falls back to the reported location.
    val dataFixPoint: GeoPoint? = null
)

/**
 * One bike-rental marker. [station] is the app-owned domain type (the renderer reads its
 * name/availability for the info window and its floating-vs-station flag for the icon).
 * [bikeshareVisible] on the snapshot is the layer/directions-mode gate; the per-zoom icon band is
 * chosen live by the renderer.
 */
data class BikeMarker(
    val id: String,
    val point: GeoPoint,
    val isFloatingBike: Boolean,
    val station: BikeStation
)

/**
 * One bus-stop marker. [direction]/[routeType] choose the icon + anchor; [stop] is the raw pojo
 * couriered so a tap can notify focus listeners. Whether this stop renders focused (the 1.5x icon) is
 * decided by [MapRenderSnapshot.focusedStopId], not stored here, so focusing is a one-field change.
 * [favorite] is stored here (it's a per-stop property that changes as the user stars/unstars), driving
 * the distinctive star icon + tap preference (#1680).
 *
 * [presentedRoutes] identifies the route-direction variants in the current presentation that serve
 * this stop. A non-empty set makes [routeStop] true: [point] is projected onto the route centerline
 * and renders as the trip-map-style circle instead of the direction-anchored icon. Carrying identities,
 * rather than only a boolean, lets a stop-focus handoff preserve every shared route's color.
 */
data class StopMarker(
    val id: String,
    val point: GeoPoint,
    val direction: String,
    val routeType: Int,
    val stop: ObaStop,
    val favorite: Boolean = false,
    val presentedRoutes: Set<RouteDirectionKey> = emptySet()
) {
    val routeStop: Boolean get() = presentedRoutes.isNotEmpty()
}

/**
 * A tappable badge marking where the selected vehicle's block continues onto a different route
 * (#1691): a small pill halfway along the continuation line showing [routeShortName]. Tapping it
 * navigates the map to [routeId], preferring [directionId] (the neighbor trip's own direction) over the
 * target route's default when the wire response resolved one (via
 * [org.onebusaway.android.map.MapViewModel.toRoute]).
 */
data class ContinuationBadge(
    val point: GeoPoint,
    val routeId: String,
    val routeShortName: String,
    val directionId: Int?
)

/**
 * A tappable label for one route in focused-stop adjacency view (#1827), anchored once in geographic
 * space so the map SDK naturally carries it through pan and zoom. Rendered by both flavors (#1913).
 */
data class RouteBadge(
    val routeId: String,
    val routeShortName: String,
    val color: Int,
    val point: GeoPoint,
    val directionId: Int?
)

/**
 * The arrowhead terminating a route-continuation line (#1691), at [point] oriented along [bearing]
 * (compass degrees, 0°=N — the shape's travel direction at that point) so it visually points onward
 * into the next route.
 */
data class ContinuationArrow(val point: GeoPoint, val bearing: Float)

/**
 * The selected vehicle's route continuation (#1691): a single optional overlay, not a list — the
 * trigger is the one selected vehicle, so at most one is ever shown. [polyline] runs from the
 * transition point to [arrow]'s point, with [badge] halfway between them. Absent (null on
 * [MapRenderSnapshot.routeContinuation]) when nothing is selected, the block doesn't continue onto a
 * different route, or the continuation data hasn't resolved yet.
 */
data class RouteContinuation(val polyline: RoutePolyline, val arrow: ContinuationArrow, val badge: ContinuationBadge)

/** Immutable snapshot of everything the map should render. Grows one overlay per phase. */
data class MapRenderSnapshot(
    val routePolylines: List<RoutePolyline> = emptyList(),
    val routeBadges: List<RouteBadge> = emptyList(),
    val genericMarkers: Map<Int, GenericMarker> = emptyMap(),
    val bikeStations: List<BikeMarker> = emptyList(),
    val bikeshareVisible: Boolean = false,
    val stops: List<StopMarker> = emptyList(),
    // True when route mode asks stop circles to follow the focused-route zoom ramp even without an
    // individually focused stop.
    val routeModeScalesStopsWithZoom: Boolean = false,
    // The currently focused stop id, couriered so the vehicle info-window's "more info" tap can deep
    // link into TripDetails scoped to that stop (the legacy VehicleOverlay.Controller hook).
    val focusedStopId: String? = null,
    // The zoom band the stops render in (full directional icon vs small dot). Derived from the camera
    // by StopsMapController and carried here so a pure zoom re-fires the renderer like any other
    // snapshot change — keeping the renderer a pure function of the snapshot (no live camera reads).
    val stopBand: StopBand = StopBand.FULL,
    // The selected vehicle's route continuation (#1691), or null. A discrete, infrequently-changing
    // annotation like the fields above, so it rides the same renderStatic() redraw path rather than
    // the 20Hz vehicle-motion sampler.
    val routeContinuation: RouteContinuation? = null
) {
    /** Focused-stop adjacency and route focus use the same route-stop zoom scale. */
    val routeStopsScaleWithZoom: Boolean
        get() = routeModeScalesStopsWithZoom || focusedStopId != null

    /**
     * Stop focus with no route selected yet: a stop is focused but route mode isn't active, so the
     * adjacent route-stop circles recede (half size) to set this mode apart from selected-route focus,
     * where one route is thickened instead (#1985).
     *
     * Like [routeStopsScaleWithZoom] above, this reads [routeModeScalesStopsWithZoom] as the "route mode
     * active" signal — the three presentation states (plain / stop-focus / route-focus) are decoded from
     * two booleans. If a fourth state ever appears, that's the seam to promote to an explicit mode enum.
     */
    val stopFocusRecedesAdjacent: Boolean
        get() = focusedStopId != null && !routeModeScalesStopsWithZoom
}

/**
 * The route-mode vehicle **set**: which vehicles exist and the [response] their icons/info-windows
 * derive from. This is discrete state — it changes only at events (a new poll, a direction switch,
 * leaving route mode), so it's *pushed* (see [MapRenderState.vehicleSet]) and the renderer reconciles
 * markers from each emission. Per-frame *motion* is a separate concern (see
 * [MapRenderState.vehiclesSampler]); the [markers] here carry only a seed position for a freshly-added
 * marker, which the motion sampler immediately supersedes. The selected vehicle is tracked separately
 * (see [MapRenderState.selectedVehicleTripId]).
 */
data class MapVehicles(
    val markers: List<VehicleMarker> = emptyList(),
    val response: RouteTrips? = null
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

    // Map content padding (top chrome/route focus + arrivals-sheet bottom), in its own flow so a
    // padding change doesn't redraw the overlay content. The Google adapter applies it globally via
    // map.setPadding (which its newLatLngBounds framing then honors); maplibre's newLatLngBounds ignores
    // persistent padding, so its Points framing reads this value and folds the insets into the fit
    // directly (see MapLibreCameraCommands.applyFramingIntent).
    private val _padding = MutableStateFlow(MapPadding())

    val padding: StateFlow<MapPadding> = _padding.asStateFlow()

    // Top padding is the lower of two independently reported edges: the always-present floating-chrome
    // inset (status bar + FAB-row clearance) and the active focus banner's bottom edge. Keeping
    // absolute edges avoids double-counting the chrome when the banner sits below it.
    private var topChromeInsetPx = 0
    private var focusBannerBottomEdgePx = 0

    private fun applyTopPadding() = _padding.update { it.copy(topPx = maxOf(topChromeInsetPx, focusBannerBottomEdgePx)) }

    /** The floating top-chrome inset (status bar + FAB-row clearance); keeps the compass below the FABs. */
    fun setTopChromeInset(px: Int) {
        topChromeInsetPx = px
        applyTopPadding()
    }

    /** The active focus banner's absolute bottom edge (0 clears banner-specific padding). */
    fun setFocusBannerBottomEdge(px: Int) {
        focusBannerBottomEdgePx = px
        applyTopPadding()
    }

    // Bottom padding is the greater of two independently reported insets — the arrivals sheet and the
    // directions results sheet — mirroring [applyTopPadding]. They're mutually exclusive today (a focused
    // stop and directions focus never coexist), but combining by max means neither writer has to know
    // that, and a stale 0 from the inactive source can't clobber the active inset on a focus transition.
    private var arrivalsBottomInsetPx = 0
    private var directionsBottomInsetPx = 0

    private fun applyBottomPadding() = _padding.update { it.copy(bottomPx = maxOf(arrivalsBottomInsetPx, directionsBottomInsetPx)) }

    /** The arrivals sheet's bottom inset (keeps the focused stop above the sheet). */
    fun setBottomPadding(px: Int) {
        arrivalsBottomInsetPx = px
        applyBottomPadding()
    }

    /** The directions results sheet's bottom inset (keeps a focused itinerary step above the sheet). */
    fun setDirectionsBottomInset(px: Int) {
        directionsBottomInsetPx = px
        applyBottomPadding()
    }

    // The live lat/lng -> screen projector, published by the flavor adapter once the map is laid out
    // (backed by the map's Projection). Lets map-SDK-agnostic callers locate a marker on screen without
    // touching the map SDK; null while the map isn't ready.
    private val _projector = MutableStateFlow<MapProjector?>(null)

    val projector: StateFlow<MapProjector?> = _projector.asStateFlow()

    fun setProjector(projector: MapProjector?) {
        _projector.value = projector
    }

    // Transient one-shot camera gestures a controller dispatches and the flavor adapter applies against
    // its imperative map (animateCamera/moveCamera). replay=0: a gesture dispatched with no map
    // subscribed is meant to be discarded — it carries no lasting intent to catch a late subscriber up
    // on (that's [framingIntent]'s job). Buffered so the synchronous dispatch never suspends or drops
    // under a brief burst (e.g. the vehicle poll).
    private val _cameraGestures = MutableSharedFlow<CameraCommand>(extraBufferCapacity = 16)

    val cameraGestures: SharedFlow<CameraCommand> = _cameraGestures.asSharedFlow()

    fun dispatchGesture(command: CameraCommand) {
        _cameraGestures.tryEmit(command)
    }

    // Transient one-shot "ping" ripples: a circle radiating out from a vehicle, played once to draw the eye
    // to a just-focused one (#1764). Carries the vehicle's **trip id** (not a fixed point) so the renderer
    // can center the ripple on the vehicle marker's live position each frame — the marker slides from its
    // raw fallback to its shape-projected spot once the trip's shape loads, and the ripple must follow it
    // rather than stay planted where the vehicle was when focused. Transient like [cameraGestures] —
    // replay=0 so a ping emitted with no map subscribed is discarded, buffered so the emit never drops.
    private val _mapPings = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val mapPings: SharedFlow<String> = _mapPings.asSharedFlow()

    fun emitPing(tripId: String) {
        _mapPings.tryEmit(tripId)
    }

    // The map's retained framing intent (fit route / itinerary / region / a fixed point), or null for no
    // framing. A replay=1 SharedFlow rather than a StateFlow, for two reasons a plain StateFlow can't
    // serve at once: (1) a fresh adapter — a config-change or process-restore recompose, or the map
    // composed behind the directions results sheet (#1640) — replays the current framing and re-applies
    // it, which is what lets the host drop the pendingFrameCommands / cameraCommandsSubscribed deferral
    // machinery a replay=0 flow forced; and (2) re-emitting the *same* framing (re-tapping the shown
    // route to snap back to its extent) still re-fires, whereas a StateFlow would swallow the identical
    // value as a no-op. Null is emitted to clear framing when the map leaves a framed view, so a stale
    // route fit isn't re-applied in nearby-stops mode.
    private val _framingIntent = MutableSharedFlow<FramingIntent?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val framingIntent: SharedFlow<FramingIntent?> = _framingIntent.asSharedFlow()

    fun frame(intent: FramingIntent) {
        _framingIntent.tryEmit(intent)
    }

    fun clearFraming() {
        _framingIntent.tryEmit(null)
    }

    fun getRoutePolylines(): List<RoutePolyline> = _snapshot.value.routePolylines

    // The active single route's canonical geometry for FramingIntent.Route. This is camera state, not
    // rendered state: focused-stop adjacency keeps sibling routes visible, but they must not widen the
    // selected route's camera box to their union. Like the other mutators, this is main-thread confined.
    internal var routeFramingPolylines: List<RoutePolyline> = emptyList()
        private set

    fun setRoutePolylines(
        polylines: List<RoutePolyline>,
        framingPolylines: List<RoutePolyline> = polylines,
        routeModeScalesStopsWithZoom: Boolean = false
    ) {
        routeFramingPolylines = framingPolylines
        _snapshot.update {
            it.copy(
                routePolylines = polylines,
                routeModeScalesStopsWithZoom = routeModeScalesStopsWithZoom
            )
        }
    }

    fun clearRoutePolylines() = setRoutePolylines(emptyList())

    /** Sets the adjacency route badges (#1827), rendered by both flavors (#1913). */
    fun setRouteBadges(badges: List<RouteBadge>) {
        _snapshot.update { it.copy(routeBadges = badges) }
    }

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

    // --- Vehicles (the old VehicleOverlay). The layer is split by change-rate so the renderer never has ---
    // --- to *infer* a discrete change from a per-frame value: ---
    // ---   • the SET (which vehicles + their icons/response) is discrete, so it's pushed here and the ---
    // ---     renderer reconciles markers on each emission (a poll, a direction switch, or leaving mode); ---
    // ---   • MOTION (each vehicle's position) is continuous, so it stays a per-frame [vehiclesSampler] ---
    // ---     the renderer pulls to move the already-reconciled markers. ---
    // --- Both null => not in route mode (the renderer draws nothing). ---

    private val _vehicleSet = MutableStateFlow<MapVehicles?>(null)

    /** The current vehicle set; the renderer reconciles its markers whenever this emits. */
    val vehicleSet: StateFlow<MapVehicles?> = _vehicleSet.asStateFlow()

    /** Publish the vehicle set (on a poll / direction switch), or null on leaving route mode. */
    fun setVehicleSet(set: MapVehicles?) {
        _vehicleSet.value = set
    }

    private val _vehiclesSampler = MutableStateFlow<FrameSampler<MapVehicles>?>(null)

    /** Per-frame motion for the current set: the renderer pulls a frame to move markers in place. */
    val vehiclesSampler: StateFlow<FrameSampler<MapVehicles>?> = _vehiclesSampler.asStateFlow()

    fun setVehiclesSampler(sampler: FrameSampler<MapVehicles>?) {
        _vehiclesSampler.value = sampler
    }

    // The selected vehicle (its most-recent-data marker is shown), set by a tap and independent of the
    // per-frame marker sampling, so it sits in its own flow.
    private val _selectedVehicleTripId = MutableStateFlow<String?>(null)

    val selectedVehicleTripId: StateFlow<String?> = _selectedVehicleTripId.asStateFlow()

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

    /** Sets (or clears) the selected vehicle's route continuation (#1691); redrawn with the next snapshot. */
    fun setRouteContinuation(continuation: RouteContinuation?) {
        _snapshot.update { it.copy(routeContinuation = continuation) }
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
}
