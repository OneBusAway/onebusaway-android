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
import androidx.collection.LruCache
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
import org.onebusaway.android.map.compose.rentalContentDescription
import org.onebusaway.android.map.mapRouteLineCaseColor
import org.onebusaway.android.map.render.BadgedRoute
import org.onebusaway.android.map.render.ContinuationBadgeBitmaps
import org.onebusaway.android.map.render.CorrectionSmoother
import org.onebusaway.android.map.render.MapPing
import org.onebusaway.android.map.render.MapRenderSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.MapVehicles
import org.onebusaway.android.map.render.PingTarget
import org.onebusaway.android.map.render.PinnedTripBitmaps
import org.onebusaway.android.map.render.PinnedTripMarker
import org.onebusaway.android.map.render.RentalBand
import org.onebusaway.android.map.render.RentalBitmaps
import org.onebusaway.android.map.render.RentalMarker
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineReconciler
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.TripMarkerBitmaps
import org.onebusaway.android.map.render.TripOverlay
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.map.render.formatDataAge
import org.onebusaway.android.map.render.rentalZoomBand
import org.onebusaway.android.map.render.routeLineWidthScale
import org.onebusaway.android.map.rental.rentalChargeFraction
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ThemeUtils
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
 *  - [renderStatic] clear-and-redraws the remaining static annotations (bikes / generics);
 *    [MapLibreStopMarkerLayer] reconciles stops in place so unchanged stops neither blink nor
 *    receive redundant native position writes.
 *  - [renderDynamic] (the live vehicle markers + the selected vehicle's band/fast-estimate marker) is pulled each
 *    display frame by the adapter's vsync loop. It updates marker positions **in place** (so an open
 *    info window survives and there's no per-frame flicker) and only adds/removes annotations as the
 *    identity set changes; the band's polylines, which carry no interaction state, are remove+re-added.
 *
 * maplibre markers have no per-marker anchor, so what the Google flavor anchors precisely sits by
 * maplibre's own convention here (a deliberate flavor gap).
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
    private val renderState: MapRenderState
) : PingTarget {
    private val stopMarkerLayer = MapLibreStopMarkerLayer(map, context)

    // A line's case colour, read at draw time rather than carried on the line: it follows the theme, because
    // the basemap it separates its line from does (see [mapRouteLineCaseColor]). Shared by everything that
    // draws in it — the case itself, and the interline cut, which is why they can't drift apart.
    private val caseColorOf: (RoutePolyline) -> Int =
        { mapRouteLineCaseColor(it.resolvedColor, ThemeUtils.isInDarkMode(context)) }

    // The marks on a route line's ends, drawn as style layers because the classic polyline annotation has no
    // configurable cap: the endpoint bulbs, and the interline cutover slashes (#2127). Rendered together
    // (see [renderLineDecorations]) — both are sized from the line's stroke width, so both answer the camera.
    private val routeEndpointBulbLayer = MapLibreRouteEndpointBulbLayer(mapStyle)

    private val interlineSeamLayer = MapLibreInterlineSeamLayer(
        mapStyle,
        context.resources.displayMetrics.density,
        caseColorOf = caseColorOf
    )
    private val rentalByMarker = HashMap<Marker, RentalMarker>()

    // Native sprites for the rental markers, keyed by everything that varies the artwork. Bounded by
    // (layers x kinds x charge buckets) + 1, so a plain map rather than an LruCache.
    private val rentalIcons = HashMap<String, Icon>()
    private var pinnedTripByMarker: Pair<Marker, PinnedTripMarker>? = null

    private val vehicleByMarker = HashMap<Marker, VehicleMarker>()

    // Route badge tap targets — adjacency (#1827) and a directions ride (#2101) — mirroring the Google
    // flavor's routeBadgeByMarker. Their geographic anchors are laid out once upstream; these markers
    // then move naturally with the map through pan and zoom.
    // Every route-badge marker the last static render drew, including the inert ones: a camera settle has
    // to re-stamp a label whose zoom schedule gives it a different size there (#2102) whether or not it
    // leads anywhere. [routeBadgeForMarker] applies the tappability filter, so this stays one registry
    // rather than two collections written in the same loop and cleared in the same places.
    private val routeBadgeByMarker = HashMap<Marker, RouteBadge>()

    // The zoom the route-badge icons above were last stamped at. Synced to the live camera by each static
    // redraw and moved by each settle, so the scale a label draws at always answers the camera it's under.
    private var renderedBadgeZoom = map.cameraPosition.zoom.toFloat()

    // One Icon per distinct label bitmap, the maplibre counterpart of the Google flavor's descriptorCache
    // and keyed the same way ([ContinuationBadgeBitmaps.badgeKey]). Not an optimization to taste: an Icon
    // carries a full ARGB copy of its bitmap and registers a native sprite, iconFactory mints a fresh id
    // per call so two icons made from equal bitmaps never dedupe, and the SDK doesn't release the icon a
    // marker is replacing. Re-stamping badges on every camera settle without this would retain a bitmap
    // and a sprite per settle for the life of the map. Freed in [dispose].
    //
    // An LRU rather than a plain map, for the reason the Google flavor's [BitmapDescriptorCache] is one:
    // quantizing scaleAt bounds only the *size* dimension of the key, while the label content in it — the
    // names and colors on the pill — turns over with every route and itinerary shown, and [renderStatic]
    // drops those markers without dropping their entries. Unbounded, a long session accumulates a bitmap
    // and a sprite per label it ever drew. Evicting is safe: a marker holds its own reference to the Icon
    // it was given, so a still-drawn label keeps its sprite and an evicted key is merely re-minted on the
    // next request. See [BADGE_ICON_CACHE_SIZE] for the sizing.
    private val routeBadgeIcons = LruCache<String, Icon>(BADGE_ICON_CACHE_SIZE)

    // The non-route static annotations added by the last [renderStatic], removed (not map.clear()) on
    // the next so the retained route and per-frame dynamic layers survive a static redraw.
    private val staticAnnotations = mutableListOf<Annotation>()

    // Whole-route lines are reconciled independently from the combined static snapshot: stop list,
    // focus, or bike changes retain these native polylines. The flavor-neutral reconcile/width bookkeeping
    // lives in the shared [RoutePolylineReconciler] (#1906); only the four maplibre-specific line
    // operations below are supplied here.
    private val routePolylineReconciler = RoutePolylineReconciler<Polyline>(
        widthOf = ::routeWidth,
        createLine = { polyline, width ->
            map.addPolyline(
                PolylineOptions()
                    .color(polyline.resolvedColor)
                    .width(width)
                    .addPoints(polyline.points)
            )
        },
        removeLines = { lines -> map.removeAnnotations(lines) },
        setWidth = { line, width -> line.width = width },
        caseColorOf = caseColorOf,
        // maplibre annotation widths are already in dp, so no density conversion is involved.
        caseExtraWidth = { it.case.extraWidthDp }
    )

    // The dynamic layer, tracked by identity so [renderDynamic] can move markers in place: route
    // vehicles keyed by active trip id, the trip-focus estimate markers keyed by role, and the band's
    // (interaction-free) polylines re-added each frame. [lastVehicleResponse] is the current poll, set on
    // each vehicle-set reconcile, so a scale change can re-stamp the retained markers' icons.
    private val vehicleMarkersByTripId = HashMap<String, Marker>()
    private val tripMarkersByRole = HashMap<String, Marker>()
    private val bandPolylines = mutableListOf<Polyline>()
    private var lastVehicleResponse: RouteTrips? = null

    // The 8-way heading slot last stamped on each vehicle's icon, keyed by trip id, so the hot path can
    // re-stamp the direction arrow as a vehicle glides — only when its heading octant flips, not every frame.
    private val vehicleIconDirection = HashMap<String, Int>()
    private var renderedVehicleScale = routeLineWidthScale(map.cameraPosition.zoom.toFloat())

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
    private val routeStopCircleLayer = MapLibreRouteStopCircleLayer(
        map,
        mapStyle,
        density,
        ContextCompat.getColor(context, R.color.route_stop_fill),
        ContextCompat.getColor(context, R.color.map_stop_focus),
        ContextCompat.getColor(context, R.color.route_stop_outline)
    )

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
        // the bike / route-badge tap maps are cleared here.
        rentalByMarker.clear()
        rentalIcons.clear()
        pinnedTripByMarker = null
        routeBadgeByMarker.clear()

        stopMarkerLayer.render(snapshot.stops, snapshot.focusedStopId, snapshot.stopBand)
        routeStopCircleLayer.render(
            snapshot.stops,
            snapshot.focusedStopId,
            snapshot.routeStopsScaleWithZoom,
            snapshot.stopFocusRecedesAdjacent
        )

        if (snapshot.rentalsVisible) {
            val band = rentalZoomBand(map.cameraPosition.zoom.toFloat())
            if (band != RentalBand.HIDDEN) {
                val metric = PreferenceUtils.getUnitsAreMetricFromPreferences(context)
                for (rental in snapshot.rentals) {
                    val icon = rentalIcon(rental, band)
                    // Title is kept only so a marker tap opens the info window (the InfoWindowAdapter
                    // renders the shared RentalInfoWindow composable instead of the title/snippet); the
                    // snippet is the marker's content description, so a rider using TalkBack hears the
                    // occupancy and charge (#2168).
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(rental.point.toLatLng())
                            .icon(icon)
                            .title(rental.place.name)
                            .snippet(rentalContentDescription(context, rental.place, metric))
                    )
                    staticAnnotations.add(marker)
                    rentalByMarker[marker] = rental
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

        renderRouteBadges(snapshot.routeBadges)

        // The parked trip's head (#2053), added dead last — which on this flavor is the *only* way to
        // say "on top". The classic annotation API carries no z-index (contrast the Google renderer,
        // which asks for one explicitly), so draw and hit order are add order, and a stop under the pin
        // would otherwise take every tap meant for it. Trips start at stops, so that is the ordinary
        // case rather than a corner one.
        snapshot.pinnedTripMarker?.let { pinned ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(pinned.point.toLatLng())
                    .icon(iconFactory.fromBitmap(PinnedTripBitmaps.pin(context)))
                    .title(context.getString(R.string.trip_plan_pinned_resume))
            )
            staticAnnotations.add(marker)
            pinnedTripByMarker = marker to pinned
        }
    }

    // Parity with the Google flavor's renderRouteBadges (#1827/#1913): the classic Marker centers its
    // icon on the point by default, so no anchor call is needed here (contrast Google's explicit
    // .anchor(0.5f, 0.5f)). Draw order is add-order in maplibre (no z-index on classic markers), so
    // adding these last keeps them on top of the stops/bikes/generics drawn above.
    private fun renderRouteBadges(badges: List<RouteBadge>) {
        // Read the camera rather than trusting the last settle: a static redraw can land mid-gesture, and a
        // label stamped at a stale zoom would keep the wrong size until the camera next moved.
        renderedBadgeZoom = map.cameraPosition.zoom.toFloat()
        for (badge in badges) {
            val marker = map.addMarker(
                MarkerOptions()
                    .position(badge.point.toLatLng())
                    .icon(routeBadgeIcon(badge.routes, badge.scale.scaleAt(renderedBadgeZoom)))
            )
            staticAnnotations.add(marker)
            routeBadgeByMarker[marker] = badge
        }
    }

    /**
     * Re-stamp the route labels that draw at a different size at the settled [zoom] (#2102, #2195). A label
     * whose camera stayed within a flat end of its ramp, or moved less than one quantization step, resolves
     * to the same scale either side of the move and is left alone (see [RouteBadgeScaleProfile.scaleAt]).
     */
    private fun updateRouteBadgeScale(zoom: Float) {
        val previous = renderedBadgeZoom
        if (zoom == previous) return
        renderedBadgeZoom = zoom
        for ((marker, badge) in routeBadgeByMarker) {
            val scale = badge.scale.scaleAt(zoom)
            if (scale != badge.scale.scaleAt(previous)) marker.icon = routeBadgeIcon(badge.routes, scale)
        }
    }

    private fun routeBadgeIcon(routes: List<BadgedRoute>, scale: Float): Icon {
        val darkMode = ThemeUtils.isInDarkMode(context)
        val key = ContinuationBadgeBitmaps.badgeKey(routes, darkMode, scale)
        return routeBadgeIcons.get(key) ?: iconFactory
            .fromBitmap(ContinuationBadgeBitmaps.badge(routes, density, darkMode, scale))
            .also { routeBadgeIcons.put(key, it) }
    }

    /** Reconcile the independently collected route layer, retaining equal native polylines. */
    fun renderRoutePolylines(next: List<RoutePolyline> = renderState.snapshot.value.routePolylines) {
        val zoom = map.cameraPosition.zoom.toFloat()
        routePolylineReconciler.reconcile(next, zoom)
        renderLineDecorations(next, zoom)
    }

    /** Redraw the end marks of [lines] for a camera at [zoom] — see the decoration layers above. */
    private fun renderLineDecorations(lines: List<RoutePolyline>, zoom: Float) {
        routeEndpointBulbLayer.render(lines) { routeWidth(it, zoom) }
        interlineSeamLayer.render(lines) { routeWidth(it, zoom) }
    }

    private fun PolylineOptions.addPoints(points: List<GeoPoint>): PolylineOptions {
        for (point in points) add(point.toLatLng())
        return this
    }

    fun onCameraSettled(zoom: Float) {
        routePolylineReconciler.resyncWidths(zoom)
        renderLineDecorations(renderState.snapshot.value.routePolylines, zoom)
        val detailScale = routeLineWidthScale(zoom)
        updateVehicleScale(detailScale)
        updateRouteBadgeScale(zoom)
    }

    private fun routeWidth(polyline: RoutePolyline, zoom: Float): Float = polyline.widthProfile?.thicknessAt(zoom) ?: (ROUTE_WIDTH_DP * routeLineWidthScale(zoom))

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
        routeEndpointBulbLayer.dispose()
        interlineSeamLayer.dispose()
        // Clear the route lines first (removes them from the map), then mass-remove the rest.
        routePolylineReconciler.clear()
        map.removeAnnotations()

        staticAnnotations.clear()
        vehicleMarkersByTripId.clear()
        tripMarkersByRole.clear()
        bandPolylines.clear()
        vehicleByMarker.clear()
        rentalByMarker.clear()
        rentalIcons.clear()
        pinnedTripByMarker = null
        routeBadgeByMarker.clear()
        routeBadgeIcons.evictAll()
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
        val center = vehicleMarkersByTripId[tripId]?.position ?: run {
            clearPing()
            return false
        }
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
    private fun vehicleIcon(vehicle: VehicleMarker, response: RouteTrips): Icon = iconFactory.fromBitmap(
        VehicleBitmaps.vehicleBitmap(context, vehicle, response, renderedVehicleScale)
    )

    /** Re-stamp retained vehicle markers only when the settle-time detail scale changes. */
    private fun updateVehicleScale(scale: Float) {
        if (scale == renderedVehicleScale) return
        renderedVehicleScale = scale
        val response = lastVehicleResponse ?: return
        for ((marker, vehicle) in vehicleByMarker) marker.icon = vehicleIcon(vehicle, response)
    }

    /**
     * Move this marker to [latLng] and, if its info window is open, reposition it to follow — maplibre
     * repositions an open window on camera moves but not when a marker's position changes between them,
     * so a gliding marker would otherwise leave its bubble behind (the Google flavor moves both together).
     */
    private fun Marker.moveTo(latLng: LatLng) {
        position = latLng
        if (isInfoWindowShown) getInfoWindow()?.update()
    }

    /**
     * The marker's title, carrying the crowding the pips draw so it isn't sight-only (#2194) — mirroring
     * the Google flavor. Classic maplibre markers expose no accessibility node of their own, so on this
     * flavor the title reaches a screen reader only if #1728's SymbolManager migration gives it one; the
     * text is set the same way regardless, so it's there when that lands.
     */
    private fun vehicleTitle(vehicle: VehicleMarker, response: RouteTrips): String {
        val trip = response.trip(vehicle.status.activeTripId) ?: return ""
        val route = response.route(trip.routeId) ?: return ""
        val name = getRouteDisplayName(route) + " - " + MyTextUtils.formatDisplayText(trip.headsign)
        val occupancy = VehicleBitmaps.occupancyLabelRes(vehicle) ?: return name
        return name + " - " + context.getString(occupancy)
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
        nowMs: Long
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

    /**
     * The [Icon] for [rental] at [band] — the layer's colour and glyph, filled by its charge ring.
     *
     * Cached, because `renderStatic` rebuilds every annotation on every snapshot and each
     * `iconFactory.fromBitmap` mints a fresh native sprite id: minting per marker per redraw would
     * accumulate native textures for icons that are, at most, `bands x layers x kinds x charge buckets`
     * distinct. The key mirrors what actually varies the artwork — `RentalBitmaps` quantizes the charge
     * itself, so the bucket, not the raw reading, is what belongs in the key.
     *
     * maplibre centres every marker icon on its point, which is exactly where a badge belongs, so there
     * is no per-marker anchor to set here, unlike the Google flavor.
     */
    private fun rentalIcon(rental: RentalMarker, band: RentalBand): Icon {
        if (band != RentalBand.BIG) {
            return rentalIcons.get(SMALL_RENTAL_ICON_KEY)
                ?: iconFactory.fromBitmap(RentalBitmaps.small(context))
                    .also { rentalIcons.put(SMALL_RENTAL_ICON_KEY, it) }
        }
        val charge = rentalChargeFraction(rental.place)
        val key = "${rental.layer}/${rental.place.kind}/${RentalBitmaps.chargeBucket(charge)}"
        return rentalIcons.get(key)
            ?: iconFactory.fromBitmap(RentalBitmaps.big(context, rental.layer, rental.place.kind, charge))
                .also { rentalIcons.put(key, it) }
    }

    fun rentalForMarker(marker: Marker): RentalMarker? = rentalByMarker[marker]

    fun pinnedTripForMarker(marker: Marker): PinnedTripMarker? = pinnedTripByMarker?.takeIf { it.first == marker }?.second

    fun vehicleForMarker(marker: Marker): VehicleMarker? = vehicleByMarker[marker]

    /**
     * The route badge tapped, or null if [marker] isn't one — or names a label that leads nowhere (see
     * [RouteBadge.tap]), whose tap falls through to the map like any other unclaimed one.
     */
    fun routeBadgeForMarker(marker: Marker): RouteBadge? = routeBadgeByMarker[marker]?.takeIf { it.tap != null }

    /**
     * If [marker] is the ping ripple, the vehicle marker it's centered on (else null) — so a tap on the
     * ripple selects the vehicle underneath rather than being swallowed. maplibre's classic Marker has no
     * `clickable(false)` (Google draws the ping as a non-clickable Circle), so the click listener routes a
     * ping tap through to its vehicle via this (#1764).
     */
    fun vehicleMarkerUnderPing(marker: Marker): Marker? = if (marker == pingMarker) pingTripId?.let { vehicleMarkersByTripId[it] } else null

    companion object {
        private const val ROUTE_WIDTH_DP = 3f
        private const val TRIP_BAND_WIDTH_DP = 6f

        // Sized to hold a whole zoom session's worth of the labels currently on the map, so panning the
        // ramp end to end never evicts a label that is still drawn: a directions itinerary, or a focused
        // stop's adjacent routes, shows on the order of ten distinct pills, each of which can be asked for
        // at any of the nine sizes [RouteBadgeScaleProfile.scaleAt]'s sixteenths quantize its ramp to, in
        // either theme. Matches the Google flavor's DESCRIPTOR_CACHE_SIZE, which covers the same badges
        // alongside its other icons.
        private const val BADGE_ICON_CACHE_SIZE = 256
    }
}

internal fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/** Cache key for the band-independent small rental dot — see MapLibreRenderer.rentalIcon. */
private const val SMALL_RENTAL_ICON_KEY = "small"
