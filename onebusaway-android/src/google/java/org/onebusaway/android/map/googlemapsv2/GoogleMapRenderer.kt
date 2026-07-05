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
package org.onebusaway.android.map.googlemapsv2

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.android.gms.maps.model.TextureStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onebusaway.android.R
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.map.compose.formatDataAge
import org.onebusaway.android.map.googlemapsv2.compose.BikeIcons
import org.onebusaway.android.map.render.BikeBand
import org.onebusaway.android.map.render.BikeMarker
import org.onebusaway.android.map.render.CorrectionSmoother
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.MapVehicles
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.map.render.StopIconKind
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.TripMarkerBitmaps
import org.onebusaway.android.map.render.TripOverlay
import org.onebusaway.android.map.render.TripStopBitmaps
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.map.render.bikeZoomBand
import org.onebusaway.android.map.render.stopIconKind
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.getRouteDisplayName
import java.util.concurrent.TimeUnit

/**
 * The Google counterpart of `MapLibreRenderer`: it draws the shared [MapRenderState] onto a real
 * [GoogleMap] imperatively (native [Marker]/[Polyline] annotations), keeping marker→data maps so the
 * host can route taps back to focus / info-window handlers. It replaces the declarative maps-compose
 * `ObaMapContent` + the `VehicleMarkerLayer`/`TripMarkerLayer` Compose overlays.
 *
 * Two redraw paths, the same two recomposition boundaries the Compose flavor had:
 *  - [renderStatic] clear-and-redraws the static annotations (route polylines / bikes / generics /
 *    trip-stop dots) and reconciles the stop markers in place ([reconcileStopMarkers], so unchanged
 *    stops don't blink), driven by snapshot/trip-stop changes (viewport loads, the vehicle poll,
 *    focus) — a bounded cost.
 *  - [renderDynamic] (the live route vehicles + the trip-focus band/estimate markers) is pulled at
 *    ~20Hz by the adapter's frame loop. It moves native markers **in place** to their freshly
 *    extrapolated positions (so the icons glide with the map, an open info window survives, and there's
 *    no recomposition/projection-overlay jitter) and only adds/removes annotations as the identity set
 *    changes; the band's polylines, which carry no interaction state, are remove + re-added. (The 20Hz
 *    cadence — not the display rate — is what keeps the moving markers reliably tappable; see the
 *    adapter's frame-interval note.)
 */
class GoogleMapRenderer(
    private val map: GoogleMap,
    private val context: Context,
    private val renderState: MapRenderState,
) {
    private val stopByMarker = HashMap<Marker, StopMarker>()

    // Stop markers tracked by stop id so [reconcileStopMarkers] can diff them in place (add new,
    // remove gone, re-icon only on a focus flip) instead of clear-and-redraw — keeping unchanged stops
    // from blinking on every static redraw. Like the vehicle markers, these are NOT in [staticMarkers]
    // and so survive [clearStatic]; [renderedFocusedStopId] is the focus the icons were last drawn for.
    private val stopMarkersByStopId = HashMap<String, Marker>()
    private var renderedFocusedStopId: String? = null
    // The zoom band the stop icons were last drawn for (full icon vs dot); see [reconcileStopMarkers].
    private var renderedStopBand = StopBand.FULL

    private val bikeByMarker = HashMap<Marker, BikeMarker>()

    private val vehicleByMarker = HashMap<Marker, VehicleMarker>()

    // The latest trips-for-route poll, published as it changes (after the markers are reconciled). The
    // change-detector for the vehicle reconcile, the source a vehicle info window reads its content from,
    // and the signal a collector (the adapter) uses to re-render an open bubble from the fresh data.
    private val _vehicleResponse = MutableStateFlow<RouteTrips?>(null)
    val vehicleResponse: StateFlow<RouteTrips?> = _vehicleResponse.asStateFlow()

    // The static annotations added by the last [renderStatic], removed (not map.clear()) on the next so
    // the per-frame dynamic layer below survives a static redraw.
    private val staticMarkers = mutableListOf<Marker>()
    private val staticPolylines = mutableListOf<Polyline>()

    // The dynamic layer, tracked by identity so [renderDynamic] can move markers in place: route vehicles
    // keyed by active trip id, and the band's (interaction-free) polylines re-added each frame. The
    // trip-focus estimate markers are self-contained [TripEstimateMarker]s.
    private val vehicleMarkersByTripId = HashMap<String, Marker>()
    private val bandPolylines = mutableListOf<Polyline>()

    // The 8-way heading slot last stamped on each vehicle's icon, keyed by trip id. Lets the hot path
    // re-stamp the direction arrow as a vehicle glides (its bearing tracks the route shape) without
    // doing icon work every frame — only when the discrete heading octant actually changes.
    private val vehicleIconDirection = HashMap<String, Int>()

    // Smooths each moving route vehicle across a fresh-AVL jump (a decaying correction layered on the
    // dead-reckon glide), then tracks the live target between fixes. Keyed by trip id.
    private val vehicleSmoother = CorrectionSmoother()

    // The trip-focus estimate markers (best estimate, fast estimate, data-age dot): each owns its native
    // marker + ease state, a fixed info-window title, and a distinct z-index, and glides to a fresh fix.
    // Distinct z-indexes are load-bearing: these markers overlap and extrapolate every frame, so a shared
    // z-index lets gms re-order them by latitude per frame — the overlapping icons' alpha then flickers.
    // The data-age dot also carries a live "N ago" body. Icons resolve lazily on first show.
    private val vehicleEstimate = TripEstimateMarker({ vehicleEstimateIcon() }, "Best estimate", VEHICLE_ESTIMATE_Z_INDEX)
    private val fastEstimate = TripEstimateMarker({ fastEstimateIcon() }, "Fast estimate", FAST_ESTIMATE_Z_INDEX)
    private val dataAgeEstimate = TripEstimateMarker({ dataAgeIcon() }, MOST_RECENT_DATA_TITLE, DATA_AGE_Z_INDEX)
    // Smooths the most-recent-data dot to a fresh fix (it's static between fixes). Tracks the dot's current
    // selection + fix so we only move / refresh on an actual change or while settling — never while its
    // bubble is open longer than the settle.
    private val dotSmoother = CorrectionSmoother()
    private var dotSelectedId: String? = null
    private var dotFixTimeMs: Long = 0L
    private var dotAgeSeconds: Long = -1L

    // The selected vehicle's most-recent-data dot: a static marker at its last actual AVL fix (where the
    // live estimate was last corrected from), shown while a vehicle is selected, with a "Most recent
    // data" + fix-age info window (the SDK's default title/snippet). Null when nothing is selected.
    private var mostRecentDataMarker: Marker? = null

    private val bikeIcons by lazy { BikeIcons(context) }

    private val density = context.resources.displayMetrics.density

    // The directional-arrow chevron stamp is color-independent, so build it once; the per-polyline
    // color is applied by the StrokeStyle below. (Same texture the legacy GoogleMapHost route overlay
    // used.)
    private val arrowStamp: TextureStyle by lazy {
        TextureStyle.newBuilder(
            BitmapDescriptorFactory.fromResource(R.drawable.ic_navigation_expand_more)
        ).build()
    }

    // Wraps each distinct marker icon in a BitmapDescriptor exactly once, keyed by a stable logical id, so
    // the reconcile path reuses descriptors (and skips the bitmap decode/tint entirely) instead of minting
    // a fresh native texture on each heading-octant change at ~20Hz. Released in [dispose]. See
    // [BitmapDescriptorCache] for the logical-key/bounding rationale.
    private val descriptorCache =
        BitmapDescriptorCache(DESCRIPTOR_CACHE_SIZE) { BitmapDescriptorFactory.fromBitmap(it) }

    // Remove the redrawn static annotations — polylines, trip-stop dots, bikes, generic markers (not
    // map.clear(), which would also wipe the per-frame dynamic layer) — and clear the bike tap map
    // [bikeByMarker]. The reconciled stop markers are tracked apart in [stopMarkersByStopId] and
    // deliberately survive (their tap map [stopByMarker] is left intact too). Shared by [renderStatic]
    // (before it redraws) and [dispose].
    private fun clearStatic() {
        staticMarkers.forEach { it.remove() }
        staticMarkers.clear()
        staticPolylines.forEach { it.remove() }
        staticPolylines.clear()
        bikeByMarker.clear()
    }

    /** Redraw the static layer (everything but the live vehicles + trip-focus overlay). */
    fun renderStatic() {
        clearStatic()

        val snapshot = renderState.snapshot.value

        for (polyline in snapshot.routePolylines) {
            val width = polyline.widthDp?.let { it * density } ?: DEFAULT_ROUTE_WIDTH_PX
            val options = PolylineOptions()
                .width(width)
                .addSpan(StyleSpan(StrokeStyle.colorBuilder(polyline.resolvedColor).stamp(arrowStamp).build()))
            for (point in polyline.points) options.add(point.toLatLng())
            staticPolylines.add(map.addPolyline(options))
        }

        // Trip-focus scheduled-stop dots (static for the trip), drawn over the line but under the live
        // overlay markers.
        for (stop in renderState.tripStops.value) {
            staticMarkers.add(
                map.addMarker(
                    MarkerOptions()
                        .position(stop.point.toLatLng())
                        .icon(tripStopIcon(stop.selected))
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                        .zIndex(1f)
                )!!
            )
        }

        reconcileStopMarkers(snapshot.stops, snapshot.focusedStopId, snapshot.stopBand)

        if (snapshot.bikeshareVisible) {
            val band = bikeZoomBand(map.cameraPosition.zoom)
            if (band != BikeBand.HIDDEN) {
                for (bike in snapshot.bikeStations) {
                    val icon = when {
                        band == BikeBand.BIG && bike.isFloatingBike -> bikeIcons.bigFloating
                        band == BikeBand.BIG -> bikeIcons.bigStation
                        else -> bikeIcons.small
                    }
                    // Title is kept only so a marker tap opens the info window; the InfoWindowAdapter
                    // renders the shared BikeInfoWindow composable instead of the title text.
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(bike.point.toLatLng())
                            .icon(icon)
                            .title(bike.station.name)
                    )!!
                    staticMarkers.add(marker)
                    bikeByMarker[marker] = bike
                }
            }
        }

        for ((_, generic) in snapshot.genericMarkers) {
            val options = MarkerOptions().position(generic.point.toLatLng())
            generic.hue?.let { options.icon(BitmapDescriptorFactory.defaultMarker(it)) }
            staticMarkers.add(map.addMarker(options)!!)
        }
    }

    /**
     * Diff the stop markers against [stops] in place (the [reconcileVehicleMarkers] pattern): remove
     * markers whose id has left, add markers for new ids, and re-icon an existing marker only when its
     * icon kind changes — a focus flip or a zoom-band crossing ([band], full icon ⇄ dot). Unchanged
     * stops keep their native marker, so they don't blink on a static redraw. Tracked in
     * [stopMarkersByStopId] (not [staticMarkers]) so [clearStatic] leaves them be.
     */
    private fun reconcileStopMarkers(stops: List<StopMarker>, focusedStopId: String?, band: StopBand) {
        val liveIds = stops.mapTo(HashSet()) { it.id }
        val gone = stopMarkersByStopId.iterator()
        while (gone.hasNext()) {
            val entry = gone.next()
            if (entry.key !in liveIds) {
                stopByMarker.remove(entry.value)
                entry.value.remove()
                gone.remove()
            }
        }
        for (stop in stops) {
            val kind = stopIconKind(stop.id == focusedStopId, band)
            val existing = stopMarkersByStopId[stop.id]
            if (existing == null) {
                val (anchorX, anchorY) = stopAnchor(stop, kind)
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(stop.point.toLatLng())
                        .icon(stopIcon(stop, kind))
                        .flat(true)
                        .anchor(anchorX, anchorY)
                )!!
                stopMarkersByStopId[stop.id] = marker
                stopByMarker[marker] = stop
            } else if (stopIconKind(stop.id == renderedFocusedStopId, renderedStopBand) != kind) {
                // Only the markers whose icon kind changed need a new icon (and matching anchor: the
                // full icon is anchored on its circle per direction, the dot at the marker center).
                existing.setIcon(stopIcon(stop, kind))
                val (anchorX, anchorY) = stopAnchor(stop, kind)
                existing.setAnchor(anchorX, anchorY)
            }
        }
        renderedFocusedStopId = focusedStopId
        renderedStopBand = band
    }

    private fun stopIcon(stop: StopMarker, kind: StopIconKind): BitmapDescriptor = when (kind) {
        StopIconKind.FULL -> StopIconFactory.stopIcon(context, stop.direction, stop.routeType)
        StopIconKind.FULL_FOCUSED -> StopIconFactory.focusedStopIcon(context, stop.direction, stop.routeType)
        StopIconKind.DOT -> StopIconFactory.dotStopIcon(context)
        StopIconKind.DOT_FOCUSED -> StopIconFactory.focusedDotStopIcon(context)
    }

    private fun stopAnchor(stop: StopMarker, kind: StopIconKind): Pair<Float, Float> =
        when (kind) {
            StopIconKind.DOT, StopIconKind.DOT_FOCUSED -> 0.5f to 0.5f
            else -> StopIconFactory.anchorX(stop.direction) to StopIconFactory.anchorY(stop.direction)
        }

    /**
     * Tear down every native annotation and drop all marker-smoothing state. The adapter calls this from
     * its onDispose, before `MapView.onDestroy()`. Forget the smoother state first, then remove the
     * markers/polylines it tracked.
     */
    fun dispose() {
        vehicleSmoother.retainOnly(emptySet())
        dotSmoother.retainOnly(emptySet())

        clearStatic()

        stopMarkersByStopId.values.forEach { it.remove() }
        stopMarkersByStopId.clear()
        stopByMarker.clear()
        renderedFocusedStopId = null

        vehicleMarkersByTripId.values.forEach { it.remove() }
        vehicleMarkersByTripId.clear()
        vehicleByMarker.clear()
        vehicleIconDirection.clear()

        bandPolylines.forEach { it.remove() }
        bandPolylines.clear()

        mostRecentDataMarker?.remove()
        mostRecentDataMarker = null

        vehicleEstimate.dispose()
        fastEstimate.dispose()
        dataAgeEstimate.dispose()

        // Drop our wrapped descriptors so their native textures are released once the markers using them
        // are gone. (The source bitmaps are owned by the shared static caches and are deliberately not
        // recycled here — other renderer instances / the maplibre flavor reuse them.)
        descriptorCache.clear()
    }

    /**
     * Update the dynamic layer for one dynamic tick: the route's live [vehicles] (null off route mode)
     * and the trip-focus [overlay] (null off trip-focus). Moving markers smooth across a fresh fix (a
     * decaying correction on the dead-reckon glide); the band is re-added.
     */
    fun renderDynamic(overlay: TripOverlay?, vehicles: MapVehicles?, nowMs: Long) {
        moveVehicles(vehicles, nowMs)
        updateTripOverlay(overlay, nowMs)
    }

    /**
     * Reconcile the vehicle marker *set* (add/remove markers, refresh icons/titles/tap-routing) against a
     * pushed [MapRenderState.vehicleSet] emission — a new poll, a direction switch, or leaving route mode
     * (null). Driven reactively by the adapter, not the frame loop, so the set changes the instant it's
     * published rather than being inferred from the per-frame motion sample.
     */
    fun reconcileVehicles(set: MapVehicles?) {
        reconcileVehicleMarkers(set?.markers.orEmpty(), set?.response)
        // Publish after reconcile so a collector that re-renders an open bubble sees the fresh markers.
        _vehicleResponse.value = set?.response
    }

    // Per-frame motion: move each already-reconciled marker to its smoothed extrapolated position (a
    // decaying correction across a fix change) — no set diffing or icon work on the hot path, only an
    // icon re-stamp when a vehicle's heading octant flips. Markers not yet reconciled are skipped.
    private fun moveVehicles(vehicles: MapVehicles?, nowMs: Long) {
        val response = vehicles?.response
        val markers = vehicles?.markers.orEmpty()
        for (vehicle in markers) {
            val marker = vehicleMarkersByTripId[vehicle.activeTripId] ?: continue
            marker.position = vehicleSmoother
                .displayPosition(vehicle.activeTripId, vehicle.point, vehicle.fixTimeMs, nowMs)
                .toLatLng()
            // Re-stamp the direction arrow as the vehicle glides, but only when its heading octant flips
            // (the only thing that changes the icon between polls) — keeping setIcon off the every-frame path.
            if (response != null) {
                val direction = VehicleBitmaps.directionIndex(vehicle)
                if (vehicleIconDirection.put(vehicle.activeTripId, direction) != direction) {
                    marker.setIcon(vehicleIcon(vehicle, response))
                }
            }
        }
        updateMostRecentDataDot(markers, nowMs)
    }

    /**
     * Show a dot at the selected vehicle's last actual AVL fix (the host sets the selection on a vehicle
     * tap via [MapRenderState.selectedVehicleTripId]); remove it when nothing's selected or the vehicle
     * leaves. The dot marks where the data came from, not the live estimate, so it's static between
     * fixes and **smooths** (via [dotSmoother]) to each fresh fix. Its info window is the SDK default "Most
     * recent data" title + the fix-age snippet.
     *
     * Critically, nothing on the marker is touched while its bubble is open: the SDK info window is one
     * monolithic bitmap, so re-setting the snippet (or position) while shown redraws the whole bubble —
     * the flicker. So we move it only on an actual change or while a fix correction is still settling,
     * and refresh the age only while closed (it's current when reopened, frozen while open — like every
     * other info window here).
     */
    private fun updateMostRecentDataDot(markers: List<VehicleMarker>, nowMs: Long) {
        val selectedId = renderState.selectedVehicleTripId.value
        val selected = selectedId?.let { id -> markers.firstOrNull { it.activeTripId == id } }
        val reported = selected?.let { it.status.lastKnownLocation ?: it.status.position }
        if (selected == null || reported == null) {
            mostRecentDataMarker?.remove()
            mostRecentDataMarker = null
            dotSmoother.retainOnly(emptySet())
            dotSelectedId = null
            dotAgeSeconds = -1L
            return
        }
        val target = GeoPoint(reported.latitude, reported.longitude)
        val ageSeconds = TimeUnit.MILLISECONDS.toSeconds(nowMs - selected.fixTimeMs)
        val existing = mostRecentDataMarker
        if (existing == null) {
            val marker = map.addMarker(
                MarkerOptions()
                    .position(target.toLatLng())
                    .icon(dataAgeIcon())
                    .anchor(0.5f, 0.5f)
                    .zIndex(0.5f)
                    .title(MOST_RECENT_DATA_TITLE)
                    .snippet(formatDataAge(context.resources, ageSeconds))
            )!!
            mostRecentDataMarker = marker
            dotAgeSeconds = ageSeconds
            // The dot is created only after a no-selection gap cleared the smoother, so just prime it
            // (records the shown position; no correction).
            dotSmoother.prime(selectedId, target, selected.fixTimeMs)
        } else {
            // Move on an actual change of fix/selection, then keep driving the decay until it settles —
            // never an unconditional per-tick set (that would redraw an open bubble).
            val changed = selectedId != dotSelectedId || selected.fixTimeMs != dotFixTimeMs
            if (changed) dotSmoother.retainOnly(setOf(selectedId))
            if (changed || dotSmoother.isSettling(selectedId)) {
                existing.position =
                    dotSmoother.displayPosition(selectedId, target, selected.fixTimeMs, nowMs).toLatLng()
            }
            // Refresh the age only when the second rolls over and the bubble is closed: skips the
            // per-tick string format, and avoids redrawing an open bubble (the monolithic SDK window).
            if (ageSeconds != dotAgeSeconds && !existing.isInfoWindowShown) {
                existing.snippet = formatDataAge(context.resources, ageSeconds)
                dotAgeSeconds = ageSeconds
            }
        }
        dotSelectedId = selectedId
        dotFixTimeMs = selected.fixTimeMs
    }

    /** Add/remove vehicle markers to match [markers], (re)setting their icons, titles, and tap data. */
    private fun reconcileVehicleMarkers(markers: List<VehicleMarker>, response: RouteTrips?) {
        val liveIds = markers.mapTo(HashSet()) { it.activeTripId }
        vehicleSmoother.retainOnly(liveIds)
        vehicleIconDirection.keys.retainAll(liveIds)
        val gone = vehicleMarkersByTripId.iterator()
        while (gone.hasNext()) {
            val entry = gone.next()
            if (entry.key !in liveIds) {
                vehicleByMarker.remove(entry.value)
                entry.value.remove()
                gone.remove()
            }
        }
        if (response == null) return
        for (vehicle in markers) {
            val existing = vehicleMarkersByTripId[vehicle.activeTripId]
            if (existing == null) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(vehicle.point.toLatLng())
                        .icon(vehicleIcon(vehicle, response))
                        .title(vehicleTitle(vehicle, response))
                        .zIndex(VEHICLE_Z_INDEX)
                )!!
                vehicleMarkersByTripId[vehicle.activeTripId] = marker
                vehicleByMarker[marker] = vehicle
            } else {
                existing.setIcon(vehicleIcon(vehicle, response))
                existing.title = vehicleTitle(vehicle, response)
                vehicleByMarker[existing] = vehicle
            }
            // The poll refreshes the icon (color + heading); record the stamped octant so the hot path
            // doesn't redundantly re-stamp it this frame.
            vehicleIconDirection[vehicle.activeTripId] = VehicleBitmaps.directionIndex(vehicle)
        }
    }

    private fun vehicleIcon(vehicle: VehicleMarker, response: RouteTrips): BitmapDescriptor =
        descriptorCache.get(VehicleBitmaps.iconKey(vehicle, response)) {
            VehicleBitmaps.vehicleBitmap(context, vehicle, response)
        }

    private fun vehicleTitle(vehicle: VehicleMarker, response: RouteTrips): String {
        val trip = response.trip(vehicle.status.activeTripId) ?: return ""
        val route = response.route(trip.routeId) ?: return ""
        return getRouteDisplayName(route) + " - " + MyTextUtils.formatDisplayText(trip.headsign)
    }

    private fun updateTripOverlay(overlay: TripOverlay?, nowMs: Long) {
        // Reconcile the uncertainty band IN PLACE. Its points + PDF-weighted colors shift every frame, but
        // removing and re-adding the polylines each frame flickers: for the frame they're gone the static
        // route line underneath shows through (it z-fights), then the band pops back on top. So update each
        // existing segment's points/color and only add/remove polylines when the segment count changes.
        val band = overlay?.band.orEmpty()
        for ((i, segment) in band.withIndex()) {
            val points = segment.points.map { it.toLatLng() }
            val existing = bandPolylines.getOrNull(i)
            if (existing == null) {
                bandPolylines.add(
                    map.addPolyline(
                        PolylineOptions().addAll(points)
                            .color(segment.colorArgb).width(TRIP_BAND_WIDTH_PX).zIndex(TRIP_BAND_Z_INDEX)
                    )
                )
            } else {
                existing.points = points
                existing.color = segment.colorArgb
            }
        }
        while (bandPolylines.size > band.size) {
            bandPolylines.removeAt(bandPolylines.size - 1).remove()
        }
        // Each estimate marker owns its smoothing + fixed title; hand it the fresh fix, the tick clock,
        // and (for the data-age dot) its live age body.
        val fixTimeMs = overlay?.fixTimeMs ?: 0L
        vehicleEstimate.update(overlay?.vehiclePoint, fixTimeMs, nowMs)
        fastEstimate.update(overlay?.fastEstimatePoint, fixTimeMs, nowMs)
        dataAgeEstimate.update(
            overlay?.dataAge?.point,
            fixTimeMs,
            nowMs,
            body = overlay?.dataAge?.let { "${it.ageMillis / 1000}s ago" },
        )
    }

    /**
     * One trip-focus estimate marker (best estimate, fast estimate, or the data-age dot): owns its native
     * [Marker] and its own [CorrectionSmoother], with a fixed info-window [title] set at creation. [update]
     * creates it on the first non-null point, removes it on null, smooths it across a fresh fix, and
     * refreshes the optional [body] (info-window snippet — only the data-age dot uses it). The icon
     * resolves lazily on first show via [iconProvider] (so the trip-focus icons aren't built in plain
     * route mode). Driven every tick (the overlay sampler runs each frame), so the decay just progresses.
     */
    private inner class TripEstimateMarker(
        private val iconProvider: () -> BitmapDescriptor,
        private val title: String,
        private val zIndex: Float,
    ) {
        private var marker: Marker? = null
        private val smoother = CorrectionSmoother()

        fun update(point: GeoPoint?, fixTimeMs: Long, nowMs: Long, body: String? = null) {
            val existing = marker
            if (point == null) {
                existing?.remove()
                marker = null
                smoother.retainOnly(emptySet()) // drop any in-flight correction + forget
                return
            }
            if (existing == null) {
                marker = map.addMarker(
                    MarkerOptions()
                        .position(point.toLatLng())
                        .icon(iconProvider())
                        .title(title)
                        .snippet(body)
                        .anchor(0.5f, 0.5f)
                        .zIndex(zIndex)
                )!!
                smoother.prime(ESTIMATE_EASE_KEY, point, fixTimeMs)
                return
            }
            // Refresh the body only while the bubble is closed: the SDK info window is one bitmap, so
            // re-setting the snippet while shown redraws/flickers it.
            if (body != existing.snippet && !existing.isInfoWindowShown) existing.snippet = body
            existing.position =
                smoother.displayPosition(ESTIMATE_EASE_KEY, point, fixTimeMs, nowMs).toLatLng()
        }

        /** Remove the marker and drop any in-flight correction — the null-point branch of [update]. */
        fun dispose() = update(null, 0L, 0L)
    }

    // The trip-focus icons, cached through [descriptorCache] by a stable per-icon key so each resolves
    // lazily on first show and reuses one descriptor thereafter (released, with the rest, in [dispose]).
    private fun vehicleEstimateIcon(): BitmapDescriptor = tripCircleIcon(R.drawable.ic_vehicle_position)

    private fun fastEstimateIcon(): BitmapDescriptor = tripCircleIcon(R.drawable.ic_fast_estimate)

    // The signal glyph is light, so tint it gray to read on the white disc.
    private fun dataAgeIcon(): BitmapDescriptor =
        tripCircleIcon(R.drawable.ic_signal_indicator, TripMarkerBitmaps.STROKE_COLOR)

    private fun tripCircleIcon(drawableRes: Int, tintColor: Int = 0): BitmapDescriptor =
        descriptorCache.get("circle:$drawableRes:$tintColor") {
            TripMarkerBitmaps.circle(context, drawableRes, tintColor)
        }

    private fun tripStopIcon(selected: Boolean): BitmapDescriptor =
        descriptorCache.get("stop:$selected") { TripStopBitmaps.dot(selected) }

    fun stopForMarker(marker: Marker): StopMarker? = stopByMarker[marker]

    fun bikeForMarker(marker: Marker): BikeMarker? = bikeByMarker[marker]

    fun vehicleForMarker(marker: Marker): VehicleMarker? = vehicleByMarker[marker]

    /** The live route-vehicle marker for [tripId], or null if that vehicle isn't currently drawn. */
    fun vehicleMarkerForTripId(tripId: String): Marker? = vehicleMarkersByTripId[tripId]

    companion object {
        // gms polyline/marker dimensions are in screen pixels.
        private const val DEFAULT_ROUTE_WIDTH_PX = 10f
        private const val TRIP_BAND_WIDTH_PX = 44f

        // z-index used to show vehicle markers on top of stop markers (default marker z-index is 0).
        private const val VEHICLE_Z_INDEX = 1f

        // The uncertainty band draws above the static route line; the estimate markers above the band,
        // each at a distinct z-index so overlapping ones keep a stable draw order (no per-frame alpha
        // flicker). Best estimate on top, then fast estimate, then the data-age dot.
        private const val TRIP_BAND_Z_INDEX = 2f
        private const val DATA_AGE_Z_INDEX = 3f
        private const val FAST_ESTIMATE_Z_INDEX = 4f
        private const val VEHICLE_ESTIMATE_Z_INDEX = 5f

        // The (arbitrary, constant) ease key for a TripEstimateMarker's single-marker easer.
        private const val ESTIMATE_EASE_KEY = "estimate"

        private const val MOST_RECENT_DATA_TITLE = "Most recent data"

        // Comfortably covers a busy route's live working set — a vehicle type's 8 heading octants across a
        // handful of schedule-deviation colors, times a few route types, plus the 5 fixed trip-focus icons
        // — so descriptors are reused as vehicles turn, not thrashed. Bounded so a long, varied session
        // can't grow it without limit (evicting a still-shown icon just re-wraps it on next request).
        private const val DESCRIPTOR_CACHE_SIZE = 256
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
