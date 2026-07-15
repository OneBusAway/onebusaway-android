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
// Classic annotation API (Marker/Polyline/Icon/IconFactory) is deprecated in maplibre 11.x but still
// functional; file-level so the deprecated *imports* are covered too. Migration tracked in #1728.
@file:Suppress("DEPRECATION")

package org.onebusaway.android.map.maplibre

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
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
import org.maplibre.android.maps.Style
import org.onebusaway.android.R
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.map.compose.formatDataAge
import org.onebusaway.android.map.render.BikeBand
import org.onebusaway.android.map.render.BikeBitmaps
import org.onebusaway.android.map.render.BikeMarker
import org.onebusaway.android.map.render.CorrectionSmoother
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapPing
import org.onebusaway.android.map.render.MapRenderSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.PingTarget
import org.onebusaway.android.map.render.MapVehicles
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.TripMarkerBitmaps
import org.onebusaway.android.map.render.TripOverlay
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.map.render.bikeZoomBand
import org.onebusaway.android.map.render.routeLineWidthScale
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.getRouteDisplayName

/**
 * The maplibre counterpart of the Google `ObaMapContent`: it draws the shared [MapRenderState] onto
 * the map imperatively, using the classic maplibre annotation API (the same one the old
 * `StopOverlay` used), keeping marker→data maps so the host can route taps back to focus/info-window
 * handlers.
 *
 * Three redraw paths split by update cadence:
 *  - [renderRoutePolylines] independently reconciles the infrequently-changing route layer, so
 *    stop-only viewport updates retain every long native line.
 *  - [renderStatic] clear-and-redraws the remaining static annotations (bikes / generics / trip-stop
 *    dots); [MapLibreStopMarkerLayer] reconciles stops in place so unchanged stops neither blink nor
 *    receive redundant native position writes.
 *  - [renderDynamic] (the live vehicle markers + the selected vehicle's band/fast-estimate marker) is pulled each
 *    display frame by the adapter's vsync loop. It updates marker positions **in place** (so an open
 *    info window survives and there's no per-frame flicker) and only adds/removes annotations as the
 *    identity set changes; the band's polylines, which carry no interaction state, are remove+re-added.
 *
 * maplibre markers have no per-marker anchor and the classic info window is title/snippet, so the rich
 * Google Compose info windows degrade to a title + snippet here (a deliberate flavor gap).
 *
 * The classic annotation API (Marker/Polyline/Icon/IconFactory) is deprecated in maplibre 11.x but
 * still fully functional. This whole renderer — and the tap/info-window layer it feeds — is built on
 * it, and the replacement (SymbolManager/LineManager) has no info-window support, so migrating is a
 * feature-level rewrite (tracked in #1728), not a lint fix. Suppressed file-wide (see the top).
 */
class MapLibreRenderer(
    private val map: MapLibreMap,
    mapStyle: Style,
    private val context: Context,
    private val renderState: MapRenderState,
) : PingTarget {
    private val stopMarkerLayer = MapLibreStopMarkerLayer(map, context)
    private val bikeByMarker = HashMap<Marker, BikeMarker>()

    private val vehicleByMarker = HashMap<Marker, VehicleMarker>()

    // The non-route static annotations added by the last [renderStatic], removed (not map.clear()) on
    // the next so the retained route and per-frame dynamic layers survive a static redraw.
    private val staticAnnotations = mutableListOf<Annotation>()

    // Whole-route lines are reconciled independently from the combined static snapshot: stop list,
    // focus, or bike changes retain these native polylines. Snapshot copies keep the same List instance,
    // making the common stop-only update an O(1) identity check; equal republished values are retained too.
    private val routePolylines = mutableListOf<Polyline>()
    private var renderedRoutePolylines: List<RoutePolyline> = emptyList()
    private var renderedRouteWidthScale: Float? = null

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

    // The one-shot "ping" ripple (#1764): a ring-bitmap marker grown + faded over [MapPing.DURATION],
    // recentered each frame on trip [pingTripId]'s vehicle marker so it follows the icon as it settles (the
    // classic annotation API has no circle). [pingStart] is null until the first tick stamps it; null id = no ping.
    private var pingMarker: Marker? = null
    private var pingTripId: String? = null
    private var pingStart: WallTime? = null
    private val pingColor by lazy { ContextCompat.getColor(context, R.color.theme_primary) }
    private val density = context.resources.displayMetrics.density
    private val routeStopCircleLayer = MapLibreRouteStopCircleLayer(map, mapStyle, density)
    // Reused across the ripple's frames — redrawn in place rather than reallocated each frame (the ring
    // is a bitmap because the classic annotation API has no circle). Freed with the ping in clearPing.
    private var pingBitmap: Bitmap? = null
    private val pingPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }

    /** Redraw the static layer (everything but the live vehicles + trip-focus overlay). */
    fun renderStatic(snapshot: MapRenderSnapshot = renderState.snapshot.value) {
        // Remove only our own non-route static annotations (not map.clear(), which would also wipe the
        // retained route and per-frame dynamic layers), then redraw them from the snapshot.
        if (staticAnnotations.isNotEmpty()) {
            map.removeAnnotations(staticAnnotations)
            staticAnnotations.clear()
        }
        // Stop markers are reconciled in place (not in staticAnnotations), so they survive this; only
        // the bike tap map is cleared here.
        bikeByMarker.clear()

        stopMarkerLayer.render(snapshot.stops, snapshot.focusedStopId, snapshot.stopBand)
        routeStopCircleLayer.render(snapshot.stops, snapshot.focusedStopId)

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

    /** Reconcile the independently collected route layer, retaining equal native polylines. */
    fun renderRoutePolylines(next: List<RoutePolyline> = renderState.snapshot.value.routePolylines) {
        if (renderedRoutePolylines === next || renderedRoutePolylines == next) return

        if (routePolylines.isNotEmpty()) map.removeAnnotations(routePolylines)
        routePolylines.clear()
        renderedRoutePolylines = next
        val widthScale = routeLineWidthScale(map.cameraPosition.zoom.toFloat())
        renderedRouteWidthScale = widthScale

        for (polyline in next) {
            val options = PolylineOptions()
                .color(polyline.resolvedColor)
                .width(baseRouteWidth(polyline) * widthScale)
                .addPoints(polyline.points)
            routePolylines.add(map.addPolyline(options))
        }
    }

    private fun PolylineOptions.addPoints(points: List<GeoPoint>): PolylineOptions {
        for (point in points) add(point.toLatLng())
        return this
    }

    fun onCameraSettled(zoom: Float) {
        val widthScale = routeLineWidthScale(zoom)
        if (widthScale == renderedRouteWidthScale) return
        renderedRouteWidthScale = widthScale
        for (index in routePolylines.indices) {
            routePolylines[index].width = baseRouteWidth(renderedRoutePolylines[index]) * widthScale
        }
    }

    private fun baseRouteWidth(polyline: RoutePolyline): Float = polyline.widthDp ?: ROUTE_WIDTH_DP

    /**
     * Update the dynamic layer for one display frame: the route's live [vehicles] (null off route mode)
     * and the selected vehicle's [overlay] (null when nothing is selected). Markers move in place (smoothed
     * across a fresh fix via [nowMs]); the band is re-added.
     */
    fun renderDynamic(overlay: TripOverlay?, vehicles: MapVehicles?, nowMs: Long) {
        moveVehicles(vehicles, nowMs)
        updateTripOverlay(overlay, nowMs)
    }

    /** Releases renderer-owned annotations and extracted style layers before MapView destruction. */
    fun dispose() {
        vehicleSmoother.retainOnly(emptySet())
        tripSmoother.retainOnly(emptySet())
        dotSmoother.retainOnly(emptySet())
        clearPing()
        stopMarkerLayer.dispose()
        routeStopCircleLayer.dispose()
        map.removeAnnotations()

        staticAnnotations.clear()
        routePolylines.clear()
        renderedRoutePolylines = emptyList()
        renderedRouteWidthScale = null
        vehicleMarkersByTripId.clear()
        tripMarkersByRole.clear()
        bandPolylines.clear()
        vehicleByMarker.clear()
        bikeByMarker.clear()
        vehicleIconDirection.clear()
        mostRecentDataMarker = null
        lastVehicleResponse = null
    }

    /** Start a one-shot ping ripple on trip [tripId]'s vehicle; the driver calls [tickPing] to animate it (#1764). */
    override fun startPing(tripId: String) {
        clearPing()
        pingTripId = tripId
        pingStart = null // stamped on the first tick
    }

    /** Remove any in-flight ping ripple (a superseded/cancelled ping). */
    override fun cancelPing() = clearPing()

    // Advance the ping ripple one frame: recenter on the vehicle marker's live position (so it follows the
    // icon as it settles onto its shape-projected spot), regrow the ring bitmap (bigger radius, fading
    // color) and re-set the marker icon. Returns false — and removes the marker — when the ripple completes
    // or the vehicle is gone. Driven by the driver's own full-rate frame loop so the ripple is smooth. The
    // bitmap is a constant max-size square so the ring stays centered as it grows inside it.
    override fun tickPing(now: WallTime): Boolean {
        val tripId = pingTripId ?: return false
        val center = vehicleMarkersByTripId[tripId]?.position ?: run { clearPing(); return false }
        val start = pingStart ?: now.also { pingStart = it }
        val elapsed = now - start
        if (MapPing.isDone(elapsed)) {
            clearPing()
            return false
        }
        val progress = MapPing.progress(elapsed)
        val maxRadiusPx = (MapPing.MAX_RADIUS_DP * density).toInt()
        val radiusPx = maxRadiusPx * MapPing.radiusFraction(progress)
        val size = maxRadiusPx * 2
        val bitmap = pingBitmap?.takeIf { it.width == size } ?: createBitmap(size, size).also { pingBitmap = it }
        bitmap.eraseColor(0)
        pingPaint.color = MapPing.withAlpha(pingColor, MapPing.alpha(progress))
        pingPaint.strokeWidth = MapPing.STROKE_DP * density
        Canvas(bitmap).drawCircle(size / 2f, size / 2f, radiusPx.coerceAtLeast(0f), pingPaint)
        val icon = iconFactory.fromBitmap(bitmap)
        val existing = pingMarker
        if (existing == null) {
            pingMarker = map.addMarker(MarkerOptions().position(center).icon(icon))
        } else {
            existing.position = center
            existing.icon = icon
        }
        return true
    }

    private fun clearPing() {
        pingMarker?.let { map.removeAnnotation(it) }
        pingMarker = null
        pingTripId = null
        pingStart = null
        pingBitmap = null
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
        updateMostRecentDataDot(nowMs)
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
    private fun updateMostRecentDataDot(nowMs: Long) {
        val selectedId = renderState.selectedVehicleTripId.value
        // Read the dot's inputs from the reconciled (per-poll) set, not the per-frame motion samples:
        // the fix point + age are discrete, changing only when a new poll lands, and the set is where the
        // shape-projected [VehicleMarker.dataFixPoint] is carried (the motion samples leave it null).
        val selected = selectedId?.let { id -> vehicleMarkersByTripId[id]?.let { vehicleByMarker[it] } }
        // The dot marks the last fix at the glide's origin: the shape-projected anchor point when we
        // have it (so it coincides with the uncertainty band's origin), falling back to the raw reported
        // lat/lng for a vehicle we aren't extrapolating on a shape (#1752).
        val reported = selected?.let { it.status.lastKnownLocation ?: it.status.position }
        val target = selected?.dataFixPoint ?: reported?.let { GeoPoint(it.latitude, it.longitude) }
        if (selected == null || target == null) {
            mostRecentDataMarker?.let { map.removeAnnotation(it) }
            mostRecentDataMarker = null
            dotSmoother.retainOnly(emptySet())
            dotSelectedId = null
            dotAgeSeconds = -1L
            return
        }
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

    // The vehicle disc badge is centered in its bitmap, and maplibre's classic Marker centers an icon on
    // the point, so the badge lands on the route centerline with no anchor adjustment (#1752).
    private fun vehicleIcon(vehicle: VehicleMarker, response: RouteTrips): Icon =
        iconFactory.fromBitmap(VehicleBitmaps.vehicleBitmap(context, vehicle, response))

    /**
     * Move this marker to [latLng] and, if its info window is open, reposition it to follow — maplibre
     * repositions an open window on camera moves but not when a marker's position changes between them,
     * so a gliding marker would otherwise leave its bubble behind (the Google flavor moves both together).
     */
    private fun Marker.moveTo(latLng: LatLng) {
        position = latLng
        if (isInfoWindowShown) getInfoWindow()?.update()
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
        // The fast-estimate marker moves in place (keeping any open info window); the fix instant drives
        // the smoother's correction.
        updateTripMarker("fast", overlay?.fastEstimatePoint, fastEstimateIcon, "Fast estimate", overlay?.fixTimeMs ?: 0L, nowMs)
        // Drop smoother state for the marker's role once it's gone (overlay went null on deselect).
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

    private val fastEstimateIcon: Icon by lazy {
        iconFactory.fromBitmap(TripMarkerBitmaps.circle(context, R.drawable.ic_fast_estimate))
    }
    // The signal glyph is light, so tint it gray to read on the white disc (the most-recent-data dot).
    private val dataAgeIcon: Icon by lazy {
        iconFactory.fromBitmap(
            TripMarkerBitmaps.circle(context, R.drawable.ic_signal_indicator, TripMarkerBitmaps.STROKE_COLOR)
        )
    }
    fun stopForMarker(marker: Marker): StopMarker? = stopMarkerLayer.stopForMarker(marker)

    fun routeStopAt(point: LatLng): StopMarker? = routeStopCircleLayer.stopAt(point)

    fun bikeForMarker(marker: Marker): BikeMarker? = bikeByMarker[marker]

    fun vehicleForMarker(marker: Marker): VehicleMarker? = vehicleByMarker[marker]

    /**
     * If [marker] is the ping ripple, the vehicle marker it's centered on (else null) — so a tap on the
     * ripple selects the vehicle underneath rather than being swallowed. maplibre's classic Marker has no
     * `clickable(false)` (Google draws the ping as a non-clickable Circle), so the click listener routes a
     * ping tap through to its vehicle via this (#1764).
     */
    fun vehicleMarkerUnderPing(marker: Marker): Marker? =
        if (marker == pingMarker) pingTripId?.let { vehicleMarkersByTripId[it] } else null

    /** The current trips-for-route response, needed to render a vehicle's info window. */
    fun vehicleResponse(): RouteTrips? = lastVehicleResponse

    companion object {
        private const val ROUTE_WIDTH_DP = 3f
        private const val TRIP_BAND_WIDTH_DP = 6f
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
