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
package org.onebusaway.android.map.maplibre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.util.concurrent.TimeUnit
import org.maplibre.android.annotations.Annotation
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.onebusaway.android.R
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.map.compose.formatDataAge
import org.onebusaway.android.map.render.BikeBand
import org.onebusaway.android.map.render.BikeBitmaps
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

/**
 * The maplibre counterpart of the Google `ObaMapContent`: it draws the shared [MapRenderState] onto
 * the map imperatively, using the classic maplibre annotation API (the same one the old
 * `StopOverlay` used), keeping marker→data maps so the host can route taps back to focus/info-window
 * handlers.
 *
 * Two redraw paths, mirroring the Google flavor's two recomposition boundaries:
 *  - [renderStatic] clear-and-redraws the static annotations (route polylines / bikes / generics /
 *    trip-stop dots) and reconciles the stop markers in place ([reconcileStopMarkers], so unchanged
 *    stops don't blink), driven by snapshot/trip-stop changes (viewport loads, the vehicle poll,
 *    focus) — a bounded cost.
 *  - [renderDynamic] (the live vehicle markers + the trip-focus band/estimate markers) is pulled each
 *    display frame by the adapter's vsync loop. It updates marker positions **in place** (so an open
 *    info window survives and there's no per-frame flicker) and only adds/removes annotations as the
 *    identity set changes; the band's polylines, which carry no interaction state, are remove+re-added.
 *
 * maplibre markers have no per-marker anchor and the classic info window is title/snippet, so the rich
 * Google Compose info windows degrade to a title + snippet here (a deliberate flavor gap).
 */
class MapLibreRenderer(
    private val map: MapLibreMap,
    private val context: Context,
    private val renderState: MapRenderState,
) {
    private val stopByMarker = HashMap<Marker, StopMarker>()

    // Stop markers tracked by stop id so [reconcileStopMarkers] can diff them in place (add new,
    // remove gone, re-icon only on a focus flip) instead of clear-and-redraw — keeping unchanged stops
    // from blinking on every static redraw. Like the vehicle markers, these are NOT in
    // [staticAnnotations] and so survive a static redraw; [renderedFocusedStopId] is the focus the
    // icons were last drawn for. They live until MapView.onDestroy(), like vehicleMarkersByTripId.
    private val stopMarkersByStopId = HashMap<String, Marker>()
    private var renderedFocusedStopId: String? = null
    // The zoom band the stop icons were last drawn for (full icon vs dot); see [reconcileStopMarkers].
    private var renderedStopBand = StopBand.FULL

    private val bikeByMarker = HashMap<Marker, BikeMarker>()

    private val vehicleByMarker = HashMap<Marker, VehicleMarker>()

    // The static annotations added by the last [renderStatic], removed (not map.clear()) on the next so
    // the per-frame dynamic layer below survives a static redraw.
    private val staticAnnotations = mutableListOf<Annotation>()

    // The dynamic layer, tracked by identity so [renderDynamic] can move markers in place: route
    // vehicles keyed by active trip id, the trip-focus estimate markers keyed by role, and the band's
    // (interaction-free) polylines re-added each frame. [lastVehicleResponse] is the current poll, set on
    // each vehicle-set reconcile and read by [vehicleResponse].
    private val vehicleMarkersByTripId = HashMap<String, Marker>()
    private val tripMarkersByRole = HashMap<String, Marker>()
    private val bandPolylines = mutableListOf<Polyline>()
    private var lastVehicleResponse: RouteTrips? = null

    // The 8-way heading slot last stamped on each vehicle's icon, keyed by trip id, so the hot path can
    // re-stamp the direction arrow as a vehicle glides — only when its heading octant flips, not every frame.
    private val vehicleIconDirection = HashMap<String, Int>()

    // Smooth markers across a fresh-AVL jump (a decaying correction on the dead-reckon glide) so a fix
    // doesn't pop. Route vehicles keyed by trip id; the trip-focus estimate markers keyed by role.
    private val vehicleSmoother = CorrectionSmoother()
    private val tripSmoother = CorrectionSmoother()

    // The selected vehicle's most-recent-data dot: a marker at its last actual AVL fix (where the live
    // estimate was last corrected from), shown while a vehicle is selected, with a "Most recent data"
    // title + fix-age snippet. Static between fixes; smooths (via [dotSmoother]) to each fresh fix.
    private val dotSmoother = CorrectionSmoother()
    private var mostRecentDataMarker: Marker? = null
    private var dotSelectedId: String? = null
    private var dotFixTimeMs: Long = 0L
    private var dotAgeSeconds: Long = -1L

    private val iconFactory = IconFactory.getInstance(context)

    /** Redraw the static layer (everything but the live vehicles + trip-focus overlay). */
    fun renderStatic() {
        val snapshot = renderState.snapshot.value
        // Remove only our own static annotations (not map.clear(), which would also wipe the per-frame
        // dynamic layer), then redraw them from the snapshot. Classic annotations have no diffing.
        if (staticAnnotations.isNotEmpty()) {
            map.removeAnnotations(staticAnnotations)
            staticAnnotations.clear()
        }
        // Stop markers are reconciled in place (not in staticAnnotations), so they survive this; only
        // the bike tap map is cleared here.
        bikeByMarker.clear()

        for (polyline in snapshot.routePolylines) {
            val options = PolylineOptions().color(polyline.resolvedColor).width(polyline.widthDp ?: ROUTE_WIDTH_DP)
            for (point in polyline.points) {
                options.add(point.toLatLng())
            }
            staticAnnotations.add(map.addPolyline(options))
        }

        // Trip-focus scheduled-stop dots (static for the trip), drawn over the line but under the
        // live overlay markers.
        for (stop in renderState.tripStops.value) {
            staticAnnotations.add(
                map.addMarker(
                    MarkerOptions()
                        .position(stop.point.toLatLng())
                        .icon(if (stop.selected) tripStopSelectedIcon else tripStopIcon)
                )
            )
        }

        reconcileStopMarkers(snapshot.stops, snapshot.focusedStopId, snapshot.stopBand)

        if (snapshot.bikeshareVisible) {
            val band = bikeZoomBand(map.cameraPosition.zoom.toFloat())
            if (band != BikeBand.HIDDEN) {
                for (bike in snapshot.bikeStations) {
                    val bitmap = when {
                        band == BikeBand.BIG && bike.isFloatingBike -> BikeBitmaps.bigFloating(context)
                        band == BikeBand.BIG -> BikeBitmaps.bigStation(context)
                        else -> BikeBitmaps.small(context)
                    }
                    val station = bike.station
                    // Title is kept only so a marker tap opens the info window; the InfoWindowAdapter
                    // renders the shared BikeInfoWindow composable instead of the title/snippet.
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(bike.point.toLatLng())
                            .icon(iconFactory.fromBitmap(bitmap))
                            .title(station.name)
                    )
                    staticAnnotations.add(marker)
                    bikeByMarker[marker] = bike
                }
            }
        }

        for ((_, generic) in snapshot.genericMarkers) {
            // The classic default marker has no hue, so the green/red start/end distinction is lost
            // on maplibre (a minor flavor gap vs. the Google pins).
            staticAnnotations.add(
                map.addMarker(
                    MarkerOptions().position(generic.point.toLatLng())
                )
            )
        }
    }

    /**
     * Diff the stop markers against [stops] in place (the [reconcileVehicleMarkers] pattern): remove
     * markers whose id has left, add markers for new ids, and re-icon an existing marker only when its
     * icon kind changes — a focus flip or a zoom-band crossing ([band], full icon ⇄ dot). Unchanged
     * stops keep their native marker, so they don't blink on a static redraw. Tracked in
     * [stopMarkersByStopId] (not [staticAnnotations]) so a static redraw leaves them.
     */
    private fun reconcileStopMarkers(stops: List<StopMarker>, focusedStopId: String?, band: StopBand) {
        val liveIds = stops.mapTo(HashSet()) { it.id }
        val gone = stopMarkersByStopId.iterator()
        while (gone.hasNext()) {
            val entry = gone.next()
            if (entry.key !in liveIds) {
                map.removeAnnotation(entry.value)
                stopByMarker.remove(entry.value)
                gone.remove()
            }
        }
        for (stop in stops) {
            val kind = stopIconKind(stop.id == focusedStopId, band)
            val existing = stopMarkersByStopId[stop.id]
            if (existing == null) {
                val marker = map.addMarker(
                    MarkerOptions().position(stop.point.toLatLng()).icon(stopIcon(stop, kind))
                )
                stopMarkersByStopId[stop.id] = marker
                stopByMarker[marker] = stop
            } else if (stopIconKind(stop.id == renderedFocusedStopId, renderedStopBand) != kind) {
                // Only the markers whose icon kind changed need a new icon (maplibre centers the icon
                // on the position, so the dot lands on the stop with no anchor change).
                existing.icon = stopIcon(stop, kind)
            }
        }
        renderedFocusedStopId = focusedStopId
        renderedStopBand = band
    }

    private fun stopIcon(stop: StopMarker, kind: StopIconKind): Icon = when (kind) {
        StopIconKind.FULL -> MapLibreStopIcons.iconForDirection(context, stop.direction)
        StopIconKind.FULL_FOCUSED -> MapLibreStopIcons.focusedIconForDirection(context, stop.direction)
        StopIconKind.DOT -> MapLibreStopIcons.dotIcon(context)
        StopIconKind.DOT_FOCUSED -> MapLibreStopIcons.focusedDotIcon(context)
    }

    /**
     * Update the dynamic layer for one display frame: the route's live [vehicles] (null off route mode)
     * and the trip-focus [overlay] (null off trip-focus). Markers move in place (smoothed across a fresh
     * fix via [nowMs]); the band is re-added.
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
        lastVehicleResponse = set?.response
    }

    // Per-frame motion: move each already-reconciled marker to its smoothed extrapolated position — no set
    // diffing or icon work on the hot path, only an icon re-stamp when a vehicle's heading octant flips.
    // Markers not yet reconciled are skipped.
    private fun moveVehicles(vehicles: MapVehicles?, nowMs: Long) {
        val response = vehicles?.response
        val markers = vehicles?.markers.orEmpty()
        for (vehicle in markers) {
            val marker = vehicleMarkersByTripId[vehicle.activeTripId] ?: continue
            marker.moveTo(
                vehicleSmoother.displayPosition(vehicle.activeTripId, vehicle.point, vehicle.fixTimeMs, nowMs).toLatLng()
            )
            // Re-stamp the direction arrow as the vehicle glides, but only when its heading octant flips
            // (the only thing that changes the icon between polls) — keeping icon work off the every-frame path.
            if (response != null) {
                val direction = VehicleBitmaps.directionIndex(vehicle)
                if (vehicleIconDirection.put(vehicle.activeTripId, direction) != direction) {
                    marker.icon = vehicleIcon(vehicle, response)
                }
            }
        }
        updateMostRecentDataDot(markers, nowMs)
    }

    /**
     * Show a dot at the selected vehicle's last actual AVL fix (the host sets the selection on a vehicle
     * tap via [MapRenderState.selectedVehicleTripId]); remove it when nothing's selected or the vehicle
     * leaves. The dot marks where the data came from, not the live estimate, so it's static between fixes
     * and **smooths** (via [dotSmoother]) to each fresh fix. Its info window is the SDK default "Most
     * recent data" title + fix-age snippet. Mirrors the Google flavor's most-recent-data dot.
     *
     * As on Google, the marker is touched only on an actual change or while a fix correction is still
     * settling — never an unconditional per-tick set, which would redraw an open bubble; the age is
     * refreshed only while the bubble is closed.
     */
    private fun updateMostRecentDataDot(markers: List<VehicleMarker>, nowMs: Long) {
        val selectedId = renderState.selectedVehicleTripId.value
        val selected = selectedId?.let { id -> markers.firstOrNull { it.activeTripId == id } }
        val reported = selected?.let { it.status.lastKnownLocation ?: it.status.position }
        if (selected == null || reported == null) {
            mostRecentDataMarker?.let { map.removeAnnotation(it) }
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
            mostRecentDataMarker = map.addMarker(
                MarkerOptions()
                    .position(target.toLatLng())
                    .icon(dataAgeIcon)
                    .title(context.getString(R.string.marker_most_recent_data))
                    .snippet(formatDataAge(context.resources, ageSeconds))
            )
            dotAgeSeconds = ageSeconds
            // The dot is created only after a no-selection gap cleared the smoother, so just prime it
            // (records the shown position; no correction).
            dotSmoother.prime(selectedId, target, selected.fixTimeMs)
        } else {
            val changed = selectedId != dotSelectedId || selected.fixTimeMs != dotFixTimeMs
            if (changed) dotSmoother.retainOnly(setOf(selectedId))
            if (changed || dotSmoother.isSettling(selectedId)) {
                existing.moveTo(
                    dotSmoother.displayPosition(selectedId, target, selected.fixTimeMs, nowMs).toLatLng()
                )
            }
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
                map.removeAnnotation(entry.value)
                vehicleByMarker.remove(entry.value)
                gone.remove()
            }
        }
        if (response == null) return
        for (vehicle in markers) {
            val existing = vehicleMarkersByTripId[vehicle.activeTripId]
            if (existing == null) {
                val marker = map.addMarker(
                    MarkerOptions().position(vehicle.point.toLatLng())
                        .icon(vehicleIcon(vehicle, response))
                        .title(vehicleTitle(vehicle, response))
                )
                vehicleMarkersByTripId[vehicle.activeTripId] = marker
                vehicleByMarker[marker] = vehicle
            } else {
                existing.icon = vehicleIcon(vehicle, response)
                existing.title = vehicleTitle(vehicle, response)
                vehicleByMarker[existing] = vehicle
            }
            // The poll refreshes the icon (color + heading); record the stamped octant so the hot path
            // doesn't redundantly re-stamp it this frame.
            vehicleIconDirection[vehicle.activeTripId] = VehicleBitmaps.directionIndex(vehicle)
        }
    }

    private fun vehicleIcon(vehicle: VehicleMarker, response: RouteTrips): Icon =
        iconFactory.fromBitmap(
            bottomAnchored(VehicleBitmaps.vehicleBitmap(context, vehicle, response))
        )

    /**
     * maplibre's classic [Marker] centers an icon bitmap on the point, but the vehicle pin's tip is its
     * bottom-center (matching the Google flavor's default 0.5/1.0 anchor). Pad [bitmap] below by its own
     * height so the doubled bitmap's center lands on the original bottom-center — i.e. a bottom anchor.
     */
    /**
     * Move this marker to [latLng] and, if its info window is open, reposition it to follow — maplibre
     * repositions an open window on camera moves but not when a marker's position changes between them,
     * so a gliding marker would otherwise leave its bubble behind (the Google flavor moves both together).
     */
    private fun Marker.moveTo(latLng: LatLng) {
        position = latLng
        if (isInfoWindowShown) getInfoWindow()?.update()
    }

    private fun bottomAnchored(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height * 2, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, null)
        return out
    }

    private fun vehicleTitle(vehicle: VehicleMarker, response: RouteTrips): String {
        val trip = response.trip(vehicle.status.activeTripId) ?: return ""
        val route = response.route(trip.routeId) ?: return ""
        return getRouteDisplayName(route) + " - " + MyTextUtils.formatDisplayText(trip.headsign)
    }

    private fun updateTripOverlay(overlay: TripOverlay?, nowMs: Long) {
        // Reconcile the uncertainty band IN PLACE. Its points + alpha-graded colors shift every frame,
        // but removing and re-adding the polylines each frame thrashes the classic annotation system —
        // they never render steadily, just a faint flicker. So mutate each segment's points/color
        // (setPoints/setColor re-render that one polyline) and only add/remove when the count changes.
        val band = overlay?.band.orEmpty()
        for ((i, segment) in band.withIndex()) {
            val points = segment.points.map { it.toLatLng() }
            val existing = bandPolylines.getOrNull(i)
            if (existing == null) {
                bandPolylines.add(
                    map.addPolyline(
                        PolylineOptions().addAll(points).color(segment.colorArgb).width(TRIP_BAND_WIDTH_DP)
                    )
                )
            } else {
                existing.points = points
                existing.color = segment.colorArgb
            }
        }
        while (bandPolylines.size > band.size) {
            map.removeAnnotation(bandPolylines.removeAt(bandPolylines.size - 1))
        }
        // Estimate markers move in place (keeping any open info window); the data-age title ticks each
        // second, so refresh it when it changes. The fix instant drives the smoother's correction.
        val fixTimeMs = overlay?.fixTimeMs ?: 0L
        updateTripMarker("vehicle", overlay?.vehiclePoint, vehicleEstimateIcon, "Best estimate", fixTimeMs, nowMs)
        updateTripMarker("fast", overlay?.fastEstimatePoint, fastEstimateIcon, "Fast estimate", fixTimeMs, nowMs)
        updateTripMarker(
            "dataAge",
            overlay?.dataAge?.point,
            dataAgeIcon,
            overlay?.dataAge?.let { "${it.ageMillis / 1000}s ago" } ?: "",
            fixTimeMs,
            nowMs,
        )
        // Drop smoother state for any role whose marker is gone (overlay went null off trip-focus).
        tripSmoother.retainOnly(tripMarkersByRole.keys)
    }

    private fun updateTripMarker(
        role: String,
        point: GeoPoint?,
        icon: Icon,
        title: String,
        fixTimeMs: Long,
        nowMs: Long,
    ) {
        val existing = tripMarkersByRole[role]
        if (point == null) {
            existing?.let {
                map.removeAnnotation(it)
                tripMarkersByRole.remove(role)
            }
            return
        }
        if (existing == null) {
            tripMarkersByRole[role] =
                map.addMarker(MarkerOptions().position(point.toLatLng()).icon(icon).title(title))
            tripSmoother.prime(role, point, fixTimeMs)
        } else {
            existing.moveTo(tripSmoother.displayPosition(role, point, fixTimeMs, nowMs).toLatLng())
            if (existing.title != title) existing.title = title
        }
    }

    private val vehicleEstimateIcon: Icon by lazy {
        iconFactory.fromBitmap(TripMarkerBitmaps.circle(context, R.drawable.ic_vehicle_position))
    }
    private val fastEstimateIcon: Icon by lazy {
        iconFactory.fromBitmap(TripMarkerBitmaps.circle(context, R.drawable.ic_fast_estimate))
    }
    // The signal glyph is light, so tint it gray to read on the white disc (createDataReceivedIcon).
    private val dataAgeIcon: Icon by lazy {
        iconFactory.fromBitmap(
            TripMarkerBitmaps.circle(context, R.drawable.ic_signal_indicator, TripMarkerBitmaps.STROKE_COLOR)
        )
    }
    private val tripStopIcon: Icon by lazy { iconFactory.fromBitmap(TripStopBitmaps.dot(selected = false)) }
    private val tripStopSelectedIcon: Icon by lazy { iconFactory.fromBitmap(TripStopBitmaps.dot(selected = true)) }

    fun stopForMarker(marker: Marker): StopMarker? = stopByMarker[marker]

    fun bikeForMarker(marker: Marker): BikeMarker? = bikeByMarker[marker]

    fun vehicleForMarker(marker: Marker): VehicleMarker? = vehicleByMarker[marker]

    /** The current trips-for-route response, needed to render a vehicle's info window. */
    fun vehicleResponse(): RouteTrips? = lastVehicleResponse

    companion object {
        private const val ROUTE_WIDTH_DP = 3f
        private const val TRIP_BAND_WIDTH_DP = 6f
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
