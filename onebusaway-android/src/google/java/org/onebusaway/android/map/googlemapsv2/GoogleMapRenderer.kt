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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.CustomCap
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.android.gms.maps.model.TextureStyle
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onebusaway.android.R
import org.onebusaway.android.map.compose.formatDataAge
import org.onebusaway.android.map.compose.rentalContentDescription
import org.onebusaway.android.map.googlemapsv2.compose.RentalIcons
import org.onebusaway.android.map.mapRouteLineCaseColor
import org.onebusaway.android.map.render.BadgedRoute
import org.onebusaway.android.map.render.ContinuationBadge
import org.onebusaway.android.map.render.ContinuationBadgeBitmaps
import org.onebusaway.android.map.render.CorrectionSmoother
import org.onebusaway.android.map.render.InterlineSeamMark
import org.onebusaway.android.map.render.MapPing
import org.onebusaway.android.map.render.MapRenderSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.MapVehicles
import org.onebusaway.android.map.render.MarkerRendering
import org.onebusaway.android.map.render.PingTarget
import org.onebusaway.android.map.render.PinnedTripBitmaps
import org.onebusaway.android.map.render.PinnedTripMarker
import org.onebusaway.android.map.render.RentalBand
import org.onebusaway.android.map.render.RentalMarker
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RouteContinuation
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineReconciler
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.TripMarkerBitmaps
import org.onebusaway.android.map.render.TripOverlay
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.map.render.metersPerPixel
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
 * The Google counterpart of `MapLibreRenderer`: it draws the shared [MapRenderState] onto a real
 * [GoogleMap] imperatively (native [Marker]/[Polyline] annotations), keeping marker→data maps so the
 * host can route taps back to focus / info-window handlers. It replaces the declarative maps-compose
 * `ObaMapContent` + the `VehicleMarkerLayer`/`TripMarkerLayer` Compose overlays.
 *
 * Three redraw paths split by update cadence:
 *  - [renderRoutePolylines] independently reconciles the infrequently-changing route layer, so
 *    stop-only viewport updates retain every long native line.
 *  - [renderStatic] clear-and-redraws the remaining static annotations (bikes / generics);
 *    [GoogleStopMarkerLayer] reconciles stops in place so unchanged stops neither blink nor
 *    receive redundant native position/z-index writes.
 *  - [renderDynamic] (the live route vehicles + the selected vehicle's band/fast-estimate marker) is pulled at
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
    private val renderState: MapRenderState
) : PingTarget {
    private val stopMarkerLayer = GoogleStopMarkerLayer(map, context)

    // The GoogleRouteStopLayer seam keeps route-stop drawing strategies swappable behind one line.
    private val routeStopLayer: GoogleRouteStopLayer =
        GoogleRouteStopBitmapLayer(
            map,
            context.resources.displayMetrics.density,
            ContextCompat.getColor(context, R.color.route_stop_fill),
            ContextCompat.getColor(context, R.color.map_stop_focus),
            ContextCompat.getColor(context, R.color.route_stop_outline)
        )
    private val rentalByMarker = HashMap<Marker, RentalMarker>()
    private var pinnedTripByMarker: Pair<Marker, PinnedTripMarker>? = null

    private val vehicleByMarker = HashMap<Marker, VehicleMarker>()

    // The route-continuation badge marker's tap target (#1691) — at most one at a time (the trigger is
    // the single selected vehicle), but kept as a map like the other *ByMarker lookups for symmetry.
    private val continuationBadgeByMarker = HashMap<Marker, ContinuationBadge>()

    // Route badge tap targets — adjacency (#1827) and a directions ride (#2101) — mirrored by the
    // maplibre flavor (#1913). Their geographic anchors are laid out once upstream; these markers then
    // move naturally with the map through pan and zoom.
    // Every route-badge marker the last static render drew, including the inert ones: a camera settle has
    // to re-stamp a label whose zoom schedule gives it a different size there (#2102) whether or not it
    // leads anywhere. [routeBadgeForMarker] applies the tappability filter, so this stays one registry
    // rather than two collections written in the same loop and cleared in the same places.
    private val routeBadgeByMarker = HashMap<Marker, RouteBadge>()

    // The zoom the route-badge icons above were last stamped at. Synced to the live camera by each static
    // redraw and moved by each settle, so the scale a label draws at always answers the camera it's under.
    private var renderedBadgeZoom = map.cameraPosition.zoom

    // The latest trips-for-route poll, published as it changes (after the markers are reconciled). The
    // change-detector for the vehicle reconcile, the source a vehicle info window reads its content from,
    // and the signal a collector (the adapter) uses to re-render an open bubble from the fresh data.
    private val _vehicleResponse = MutableStateFlow<RouteTrips?>(null)
    val vehicleResponse: StateFlow<RouteTrips?> = _vehicleResponse.asStateFlow()

    // The non-route static annotations added by the last [renderStatic], removed (not map.clear()) on
    // the next so the retained route and per-frame dynamic layers survive a static redraw.
    private val staticMarkers = mutableListOf<Marker>()
    private val staticPolylines = mutableListOf<Polyline>()

    // Whole-route lines are reconciled independently from the combined static snapshot: stop list,
    // focus, or bike changes retain these native polylines. The flavor-neutral reconcile/width bookkeeping
    // lives in the shared [RoutePolylineReconciler] (#1906); only the four gms-specific line operations
    // below are supplied here.
    private val routePolylineReconciler = RoutePolylineReconciler<Polyline>(
        widthOf = ::routeWidthPx,
        createLine = ::addRoutePolyline,
        removeLines = { lines -> lines.forEach { it.remove() } },
        setWidth = { line, width -> line.width = width },
        // Resolved per line rather than once, and here rather than by the producer: a case's colour follows the
        // theme, because the basemap it separates its line from does (see [mapRouteLineCaseColor]).
        caseColorOf = { mapRouteLineCaseColor(it.resolvedColor, ThemeUtils.isInDarkMode(context)) },
        caseExtraWidth = { it.case.extraWidthDp * density }
    )

    // The dynamic layer, tracked by identity so [renderDynamic] can move markers in place: route vehicles
    // keyed by active trip id, and the band's (interaction-free) polylines re-added each frame. The
    // selected vehicle's fast-estimate marker is a self-contained [TripEstimateMarker].
    private val vehicleMarkersByTripId = HashMap<String, Marker>()
    private val bandPolylines = mutableListOf<Polyline>()

    // The 8-way heading slot last stamped on each vehicle's icon, keyed by trip id. Lets the hot path
    // re-stamp the direction arrow as a vehicle glides (its bearing tracks the route shape) without
    // doing icon work every frame — only when the discrete heading octant actually changes.
    private val vehicleIconDirection = HashMap<String, Int>()
    private var renderedVehicleScale = routeLineWidthScale(map.cameraPosition.zoom)

    // Smooths each moving route vehicle across a fresh-AVL jump (a decaying correction layered on the
    // dead-reckon glide), then tracks the live target between fixes. Keyed by trip id.
    private val vehicleSmoother = CorrectionSmoother()

    // The selected vehicle's fast-estimate marker: it owns its native marker + ease state, a fixed
    // info-window title, and a z-index above the band, and glides to a fresh fix. Icon resolves lazily
    // on first show.
    private val fastEstimate = TripEstimateMarker({ fastEstimateIcon() }, "Fast estimate", FAST_ESTIMATE_Z_INDEX)

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

    // The one-shot "ping" ripple (#1764): a native Circle grown + faded over [MapPing.DURATION], centered
    // each frame on trip [pingTripId]'s vehicle marker (so it follows the icon as it settles). [pingStart]
    // is null until the first tick stamps it (the animation clock is the frame's wall clock); null id = no ping.
    private var pingCircle: Circle? = null
    private var pingTripId: String? = null
    private var pingStart: WallTime? = null
    private val pingColor by lazy { ContextCompat.getColor(context, R.color.theme_primary) }

    private val rentalIcons by lazy { RentalIcons(context) }

    private val density = context.resources.displayMetrics.density

    // The directional-arrow chevron stamp is color-independent, so build it once; the per-polyline
    // color is applied by the StrokeStyle below. (Same texture the legacy GoogleMapHost route overlay
    // used.)
    private val arrowStamp: TextureStyle by lazy {
        TextureStyle.newBuilder(
            // Render the (vector) chevron to a bitmap; BitmapDescriptorFactory.fromResource can't
            // rasterize a VectorDrawable. The stamp scales to the polyline width, so [glyphScale]
            // (not the bitmap size) controls how large the chevron reads by filling more of the tile.
            // The stamp is color-independent (white); the per-polyline color comes from the
            // StrokeStyle above. keyboard_arrow_down is a neutral black template, so tint it white here.
            BitmapDescriptorFactory.fromBitmap(
                vectorToBitmap(R.drawable.keyboard_arrow_down, 36, glyphScale = 1.7f, tint = Color.WHITE)
                    .withLongitudinalSpacing(CHEVRON_REPEAT_SCALE)
            )
        ).build()
    }

    /**
     * Rasterizes a drawable (vector or raster) into a square [sizeDp]-dp bitmap at screen density.
     * [glyphScale] zooms the glyph within the tile (>1 fills more of it, cropping the transparent
     * margin); used to enlarge the polyline arrow stamp without widening the line. [tint], when set,
     * recolors the glyph (the stamp texture carries its own color, independent of the line).
     */
    private fun vectorToBitmap(resId: Int, sizeDp: Int, glyphScale: Float = 1f, tint: Int? = null): Bitmap {
        val sizePx = (sizeDp * density).toInt()
        val inset = (sizePx * (1f - glyphScale) / 2f).toInt()
        return MarkerRendering.rasterize(context, resId, sizePx, tint, inset)
    }

    /**
     * A texture stamp's top-to-bottom axis repeats along the polyline. Preserve the glyph at its
     * existing size while extending that axis with transparent space to reduce repetition density.
     */
    private fun Bitmap.withLongitudinalSpacing(factor: Int): Bitmap {
        if (factor <= 1) return this
        val spaced = createBitmap(width, height * factor)
        Canvas(spaced).drawBitmap(this, 0f, (spaced.height - height) / 2f, null)
        return spaced
    }

    // Wraps each distinct marker icon in a BitmapDescriptor exactly once, keyed by a stable logical id, so
    // the reconcile path reuses descriptors (and skips the bitmap decode/tint entirely) instead of minting
    // a fresh native texture on each heading-octant change at ~20Hz. Released in [dispose]. See
    // [BitmapDescriptorCache] for the logical-key/bounding rationale.
    private val descriptorCache =
        BitmapDescriptorCache(DESCRIPTOR_CACHE_SIZE) { BitmapDescriptorFactory.fromBitmap(it) }

    // Remove the redrawn non-route static annotations — continuation polylines, bikes, generic markers
    // (not map.clear(), which would also wipe the retained route and per-frame dynamic
    // layers) — and clear their tap maps. Reconciled route/stop annotations deliberately survive.
    // Shared by [renderStatic] (before it redraws) and [dispose].
    private fun clearStatic() {
        staticMarkers.forEach { it.remove() }
        staticMarkers.clear()
        staticPolylines.forEach { it.remove() }
        staticPolylines.clear()
        rentalByMarker.clear()
        pinnedTripByMarker = null
        continuationBadgeByMarker.clear()
        routeBadgeByMarker.clear()
    }

    /** Redraw the static layer (everything but the live vehicles + trip-focus overlay). */
    fun renderStatic(snapshot: MapRenderSnapshot = renderState.snapshot.value) {
        clearStatic()

        stopMarkerLayer.render(snapshot.stops, snapshot.focusedStopId, snapshot.stopBand)
        routeStopLayer.render(
            snapshot.stops,
            snapshot.focusedStopId,
            snapshot.routeStopsScaleWithZoom,
            snapshot.stopFocusRecedesAdjacent,
            map.cameraPosition.zoom
        )

        if (snapshot.rentalsVisible) {
            val band = rentalZoomBand(map.cameraPosition.zoom)
            if (band != RentalBand.HIDDEN) {
                val metric = PreferenceUtils.getUnitsAreMetricFromPreferences(context)
                for (rental in snapshot.rentals) {
                    // Centred on the point in both bands: the badge is the marker, and the small dot
                    // has no tip to hang off the coordinate either.
                    val options = MarkerOptions().position(rental.point.toLatLng()).anchor(0.5f, 0.5f)
                    options.icon(if (band == RentalBand.BIG) rentalIcon(rental) else rentalIcons.small)
                    // Title is kept only so a marker tap opens the info window (the InfoWindowAdapter
                    // renders the shared RentalInfoWindow composable instead of the title text); the
                    // snippet is what the SDK reads out as the marker's content description, so the
                    // occupancy/charge lines reach a rider who can't see them (#2168).
                    val marker = map.addMarkerOrFail(
                        options
                            .title(rental.place.name)
                            .snippet(rentalContentDescription(context, rental.place, metric))
                    )
                    staticMarkers.add(marker)
                    rentalByMarker[marker] = rental
                }
            }
        }

        // The parked trip's head (#2053). Drawn with the static markers so it survives every mode the
        // rider explores through, which is the whole point of a pin. Its title exists only so a tap opens
        // the window; the InfoWindowAdapter draws PinnedTripInfoWindow in place of the title text.
        snapshot.pinnedTripMarker?.let { pinned ->
            val marker = map.addMarkerOrFail(
                MarkerOptions()
                    .position(pinned.point.toLatLng())
                    .icon(descriptorCache.get("pinned-trip") { PinnedTripBitmaps.pin(context) })
                    .zIndex(PINNED_TRIP_Z_INDEX)
                    .title(context.getString(R.string.trip_plan_pinned_resume))
            )
            staticMarkers.add(marker)
            pinnedTripByMarker = marker to pinned
        }

        for ((_, generic) in snapshot.genericMarkers) {
            val options = MarkerOptions().position(generic.point.toLatLng())
            generic.hue?.let { options.icon(BitmapDescriptorFactory.defaultMarker(it)) }
            staticMarkers.add(map.addMarkerOrFail(options))
        }

        snapshot.routeContinuation?.let { continuation -> renderContinuation(continuation) }
        renderRouteBadges(snapshot.routeBadges)
    }

    private fun renderRouteBadges(badges: List<RouteBadge>) {
        // Read the camera rather than trusting the last settle: a static redraw can land mid-gesture, and a
        // label stamped at a stale zoom would keep the wrong size until the camera next moved.
        renderedBadgeZoom = map.cameraPosition.zoom
        for (badge in badges) {
            val marker = map.addMarkerOrFail(
                MarkerOptions()
                    .position(badge.point.toLatLng())
                    .icon(routeBadgeIcon(badge.routes, badge.scale.scaleAt(renderedBadgeZoom)))
                    // Centered on the anchor, so a label that changes size with zoom grows and shrinks
                    // about its own placement instead of drifting off it.
                    .anchor(0.5f, 0.5f)
                    .zIndex(ROUTE_BADGE_Z_INDEX)
            )
            staticMarkers += marker
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
            if (scale != badge.scale.scaleAt(previous)) marker.setIcon(routeBadgeIcon(badge.routes, scale))
        }
    }

    /** Reconcile the independently collected route layer, retaining equal native polylines. */
    fun renderRoutePolylines(next: List<RoutePolyline> = renderState.snapshot.value.routePolylines) {
        routePolylineReconciler.reconcile(next, map.cameraPosition.zoom)
    }

    private fun addRoutePolyline(polyline: RoutePolyline, widthPx: Float): Polyline {
        val options = PolylineOptions()
            .width(widthPx)
            .addPoints(polyline.points)
            .applyDashPattern(polyline)
        endCapFor(polyline, polyline.startMark)?.let { options.startCap(it) }
        endCapFor(polyline, polyline.endMark)?.let { options.endCap(it) }
        if (polyline.directional) {
            // Advanced spans are substantially more expensive for Maps to retessellate while
            // zooming. Reserve that path for the lines that actually need repeated chevrons.
            val stroke = StrokeStyle.colorBuilder(polyline.resolvedColor)
                .stamp(arrowStamp)
                .build()
            options.addSpan(StyleSpan(stroke))
        } else {
            // Adjacency focus uses undirected whole-route shapes. The classic solid-color path is
            // both the exact requested appearance and cheaper than an equivalent one-color span.
            options.color(polyline.resolvedColor)
        }
        return map.addPolyline(options)
    }

    /**
     * The cap [mark] asks for on [polyline], or null for a flat end. Both marks are [CustomCap]s, which is
     * what lets them be requested per end and stay right at every zoom: Maps scales a custom cap with the
     * line's stroke and orients it along the line itself, so neither needs the camera-settle re-stamp a
     * marker-based mark (the route labels) does.
     */
    private fun endCapFor(polyline: RoutePolyline, mark: RouteLineMark): CustomCap? = when (mark) {
        RouteLineMark.NONE -> null
        RouteLineMark.BULB -> endpointBulbCap(polyline.resolvedColor)
        RouteLineMark.INTERLINE_CUT -> interlineSeamCap(polyline.resolvedColor)
    }

    /** A circle 2x the stroke width, scaled by Maps together with the line at every zoom. */
    private fun endpointBulbCap(color: Int): CustomCap {
        val descriptor = descriptorCache.get("route-endpoint-bulb:$color") {
            val bitmap = createBitmap(ENDPOINT_BULB_BITMAP_PX, ENDPOINT_BULB_BITMAP_PX)
            Canvas(bitmap).drawCircle(
                ENDPOINT_BULB_BITMAP_PX / 2f,
                ENDPOINT_BULB_BITMAP_PX / 2f,
                ENDPOINT_BULB_BITMAP_PX / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            )
            bitmap
        }
        return CustomCap(descriptor, ENDPOINT_BULB_REFERENCE_WIDTH_PX)
    }

    /**
     * The interline cutover slash ([InterlineSeamMark]), drawn in the cut line's own case colour — resolved
     * here rather than by the producer because it follows the theme, exactly as a line's case does (see
     * [mapRouteLineCaseColor]).
     */
    private fun interlineSeamCap(lineColor: Int): CustomCap {
        val color = mapRouteLineCaseColor(lineColor, ThemeUtils.isInDarkMode(context))
        val descriptor = descriptorCache.get("interline-seam:$color") { InterlineSeamMark.bitmap(color) }
        return CustomCap(descriptor, InterlineSeamMark.REFERENCE_WIDTH_PX)
    }

    /**
     * Draws the selected vehicle's route continuation (#1691): a dashed line in the neighbor route's
     * own color (so it reads clearly against a busy basemap instead of blending into a gray street), an
     * arrowhead marker at its end oriented along the shape's travel direction, and a tappable pill badge
     * halfway along the line.
     */
    private fun renderContinuation(continuation: RouteContinuation) {
        val polyline = continuation.polyline
        val line = PolylineOptions()
            .color(polyline.resolvedColor)
            .width(routeWidthPx(polyline, map.cameraPosition.zoom))
            .addPoints(polyline.points)
            .applyDashPattern(polyline)
        staticPolylines.add(map.addPolyline(line))

        val arrow = continuation.arrow
        staticMarkers.add(
            map.addMarkerOrFail(
                MarkerOptions()
                    .position(arrow.point.toLatLng())
                    .icon(continuationArrowIcon(polyline.resolvedColor))
                    .anchor(0.5f, 1f)
                    .rotation(arrow.bearing)
                    .flat(true)
                    .zIndex(ROUTE_BADGE_Z_INDEX)
            )
        )

        val badge = continuation.badge
        val marker = map.addMarkerOrFail(
            MarkerOptions()
                .position(badge.point.toLatLng())
                .icon(routeBadgeIcon(listOf(BadgedRoute(badge.routeShortName, polyline.resolvedColor))))
                .anchor(0.5f, 0.5f)
                .zIndex(ROUTE_BADGE_Z_INDEX)
        )
        staticMarkers.add(marker)
        continuationBadgeByMarker[marker] = badge
    }

    /**
     * The pill naming [routes], drawn at [scale] — unscaled by default, which is what the fixed-size
     * route-continuation badge (#1691) above takes; a [RouteBadge] resolves its own from the camera.
     */
    private fun routeBadgeIcon(routes: List<BadgedRoute>, scale: Float = 1f): BitmapDescriptor {
        val darkMode = ThemeUtils.isInDarkMode(context)
        return descriptorCache.get(ContinuationBadgeBitmaps.badgeKey(routes, darkMode, scale)) {
            ContinuationBadgeBitmaps.badge(routes, density, darkMode, scale)
        }
    }

    private fun continuationArrowIcon(color: Int): BitmapDescriptor = descriptorCache.get("continuation-arrow:$color") {
        ContinuationBadgeBitmaps.arrow(color)
    }

    /** Resolve one line's complete width profile at [zoom]. */
    private fun routeWidthPx(polyline: RoutePolyline, zoom: Float): Float = polyline.widthProfile
        ?.let { it.thicknessAt(zoom) * density }
        ?: (DEFAULT_ROUTE_WIDTH_PX * routeLineWidthScale(zoom))

    /** Appends [points] to the receiver, shared by every static polyline draw. */
    private fun PolylineOptions.addPoints(points: List<GeoPoint>): PolylineOptions {
        for (point in points) add(point.toLatLng())
        return this
    }

    private fun PolylineOptions.applyDashPattern(polyline: RoutePolyline): PolylineOptions {
        val dashLengthPx = when (polyline.dash) {
            RouteLineDash.NONE -> return this
            RouteLineDash.HINT -> HINT_DASH_LENGTH_PX
            RouteLineDash.TRAIL -> TRAIL_DASH_LENGTH_PX
        }
        pattern(listOf(Dash(dashLengthPx), Gap(DASH_GAP_LENGTH_PX)))
        return this
    }

    /**
     * Tear down every native annotation and drop all marker-smoothing state. The adapter calls this from
     * its onDispose, before `MapView.onDestroy()`. Forget the smoother state first, then remove the
     * markers/polylines it tracked.
     */
    fun dispose() {
        vehicleSmoother.retainOnly(emptySet())
        dotSmoother.retainOnly(emptySet())
        routeStopLayer.dispose()

        clearStatic()
        routePolylineReconciler.clear()

        stopMarkerLayer.dispose()

        vehicleMarkersByTripId.values.forEach { it.remove() }
        vehicleMarkersByTripId.clear()
        vehicleByMarker.clear()
        vehicleIconDirection.clear()

        bandPolylines.forEach { it.remove() }
        bandPolylines.clear()

        mostRecentDataMarker?.remove()
        mostRecentDataMarker = null

        clearPing()

        fastEstimate.dispose()

        // Drop our wrapped descriptors so their native textures are released once the markers using them
        // are gone. (The source bitmaps are owned by the shared static caches and are deliberately not
        // recycled here — other renderer instances / the maplibre flavor reuse them.)
        descriptorCache.clear()
    }

    /**
     * Update the dynamic layer for one dynamic tick: the route's live [vehicles] (null off route mode)
     * and the selected vehicle's [overlay] (null when nothing is selected). Moving markers smooth across a
     * fresh fix (a decaying correction on the dead-reckon glide); the band is re-added.
     */
    fun renderDynamic(overlay: TripOverlay?, vehicles: MapVehicles?, nowMs: Long) {
        moveVehicles(vehicles, nowMs)
        updateTripOverlay(overlay, nowMs)
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
    // icon as it slides from its raw fallback onto its shape-projected spot), grow the Circle's radius (from
    // a target screen size via the current zoom, so it reads at a consistent on-screen size), and fade its
    // stroke. Returns false — and removes the Circle — when the ripple completes or the vehicle is gone.
    // Driven by the driver's own full-rate frame loop (not the 20Hz vehicle loop) so the ripple is smooth.
    // Circles draw beneath all markers in gms, so the vehicle icon stays crisp on top.
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
        val radiusPx = MapPing.MAX_RADIUS_DP * density * MapPing.radiusFraction(progress)
        val radiusMeters = radiusPx * metersPerPixel(center.latitude, map.cameraPosition.zoom.toDouble())
        val color = MapPing.withAlpha(pingColor, MapPing.alpha(progress))
        val existing = pingCircle
        if (existing == null) {
            pingCircle = map.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(radiusMeters)
                    .strokeColor(color)
                    .strokeWidth(MapPing.STROKE_DP * density)
                    .fillColor(Color.TRANSPARENT)
                    .clickable(false)
                    .zIndex(PING_Z_INDEX)
            )
        } else {
            existing.center = center
            existing.radius = radiusMeters
            existing.strokeColor = color
        }
        return true
    }

    private fun clearPing() {
        pingCircle?.remove()
        pingCircle = null
        pingTripId = null
        pingStart = null
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
        updateMostRecentDataDot(nowMs)
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
            mostRecentDataMarker?.remove()
            mostRecentDataMarker = null
            dotSmoother.retainOnly(emptySet())
            dotSelectedId = null
            dotAgeSeconds = -1L
            return
        }
        val ageSeconds = TimeUnit.MILLISECONDS.toSeconds(nowMs - selected.fixTimeMs)
        val existing = mostRecentDataMarker
        if (existing == null) {
            val marker = map.addMarkerOrFail(
                MarkerOptions()
                    .position(target.toLatLng())
                    .icon(dataAgeIcon())
                    .anchor(0.5f, 0.5f)
                    .zIndex(0.5f)
                    .title(MOST_RECENT_DATA_TITLE)
                    .snippet(formatDataAge(context.resources, ageSeconds))
            )
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
                val marker = map.addMarkerOrFail(
                    MarkerOptions()
                        .position(vehicle.point.toLatLng())
                        .icon(vehicleIcon(vehicle, response))
                        // Center the disc badge on the vehicle location, so it sits on the route
                        // centerline like the trip map's estimate marker rather than floating off it (#1752).
                        .anchor(0.5f, 0.5f)
                        .title(vehicleTitle(vehicle, response))
                        .zIndex(VEHICLE_Z_INDEX)
                )
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

    private fun vehicleIcon(vehicle: VehicleMarker, response: RouteTrips): BitmapDescriptor = descriptorCache.get(VehicleBitmaps.iconKey(context, vehicle, response, renderedVehicleScale)) {
        VehicleBitmaps.vehicleBitmap(context, vehicle, response, renderedVehicleScale)
    }

    /** Re-stamp retained vehicle markers only when the settle-time detail scale changes. */
    private fun updateVehicleScale(scale: Float) {
        if (scale == renderedVehicleScale) return
        renderedVehicleScale = scale
        val response = _vehicleResponse.value ?: return
        for ((marker, vehicle) in vehicleByMarker) marker.setIcon(vehicleIcon(vehicle, response))
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
        // The fast-estimate marker owns its smoothing + fixed title; hand it the fresh fix + tick clock.
        fastEstimate.update(overlay?.fastEstimatePoint, overlay?.fixTimeMs ?: 0L, nowMs)
    }

    /**
     * The selected vehicle's fast-estimate marker: owns its native [Marker] and its own
     * [CorrectionSmoother], with a fixed info-window [title] set at creation. [update] creates it on the
     * first non-null point, removes it on null, and smooths it across a fresh fix. The icon resolves lazily
     * on first show via [iconProvider] (so it isn't built until a vehicle is selected). Driven every tick
     * (the overlay sampler runs each frame), so the decay just progresses.
     */
    private inner class TripEstimateMarker(
        private val iconProvider: () -> BitmapDescriptor,
        private val title: String,
        private val zIndex: Float
    ) {
        private var marker: Marker? = null
        private val smoother = CorrectionSmoother()

        fun update(point: GeoPoint?, fixTimeMs: Long, nowMs: Long) {
            val existing = marker
            if (point == null) {
                existing?.remove()
                marker = null
                smoother.retainOnly(emptySet()) // drop any in-flight correction + forget
                return
            }
            if (existing == null) {
                marker = map.addMarkerOrFail(
                    MarkerOptions()
                        .position(point.toLatLng())
                        .icon(iconProvider())
                        .title(title)
                        .anchor(0.5f, 0.5f)
                        .zIndex(zIndex)
                )
                smoother.prime(ESTIMATE_EASE_KEY, point, fixTimeMs)
                return
            }
            existing.position =
                smoother.displayPosition(ESTIMATE_EASE_KEY, point, fixTimeMs, nowMs).toLatLng()
        }

        /** Remove the marker and drop any in-flight correction — the null-point branch of [update]. */
        fun dispose() = update(null, 0L, 0L)
    }

    // The overlay/dot icons, cached through [descriptorCache] by a stable per-icon key so each resolves
    // lazily on first show and reuses one descriptor thereafter (released, with the rest, in [dispose]).
    private fun fastEstimateIcon(): BitmapDescriptor = tripCircleIcon(R.drawable.ic_fast_estimate)

    // The signal glyph is light, so tint it gray to read on the white disc (used by the most-recent-data dot).
    private fun dataAgeIcon(): BitmapDescriptor = tripCircleIcon(R.drawable.ic_signal_indicator, TripMarkerBitmaps.STROKE_COLOR)

    private fun tripCircleIcon(drawableRes: Int, tintColor: Int = 0): BitmapDescriptor = descriptorCache.get("circle:$drawableRes:$tintColor") {
        TripMarkerBitmaps.circle(context, drawableRes, tintColor)
    }

    fun stopForMarker(marker: Marker): StopMarker? = stopMarkerLayer.stopForMarker(marker) ?: routeStopLayer.stopForMarker(marker)

    fun onCameraMoveStarted() = routeStopLayer.onCameraMoveStarted()

    fun onCameraSettled(zoom: Float) {
        routeStopLayer.onCameraSettled(zoom)
        routePolylineReconciler.resyncWidths(zoom)
        val detailScale = routeLineWidthScale(zoom)
        updateVehicleScale(detailScale)
        updateRouteBadgeScale(zoom)
    }

    /** The big badge for [rental] — the layer's colour and glyph, filled by its charge ring. */
    private fun rentalIcon(rental: RentalMarker): BitmapDescriptor = rentalIcons.big(
        rental.layer,
        rental.place.kind,
        rentalChargeFraction(rental.place)
    )

    fun rentalForMarker(marker: Marker): RentalMarker? = rentalByMarker[marker]

    fun pinnedTripForMarker(marker: Marker): PinnedTripMarker? = pinnedTripByMarker?.takeIf { it.first == marker }?.second

    fun vehicleForMarker(marker: Marker): VehicleMarker? = vehicleByMarker[marker]

    /** The route-continuation badge (#1691) tapped, or null if [marker] isn't that badge. */
    fun continuationBadgeForMarker(marker: Marker): ContinuationBadge? = continuationBadgeByMarker[marker]

    /**
     * The route badge tapped, or null if [marker] isn't one — or names a label that leads nowhere (see
     * [RouteBadge.tap]), whose tap falls through to the map like any other unclaimed one.
     */
    fun routeBadgeForMarker(marker: Marker): RouteBadge? = routeBadgeByMarker[marker]?.takeIf { it.tap != null }

    /** The live route-vehicle marker for [tripId], or null if that vehicle isn't currently drawn. */
    fun vehicleMarkerForTripId(tripId: String): Marker? = vehicleMarkersByTripId[tripId]

    companion object {
        private const val ENDPOINT_BULB_BITMAP_PX = 40
        private const val ENDPOINT_BULB_REFERENCE_WIDTH_PX = 20f

        // gms polyline/marker dimensions are in screen pixels.
        private const val DEFAULT_ROUTE_WIDTH_PX = 10f
        private const val TRIP_BAND_WIDTH_PX = 44f

        // z-index used to show vehicle markers on top of stop markers (default marker z-index is 0).
        private const val VEHICLE_Z_INDEX = 1f

        // The uncertainty band draws above the static route line; the fast-estimate marker above the band.
        private const val TRIP_BAND_Z_INDEX = 2f
        private const val FAST_ESTIMATE_Z_INDEX = 4f

        /**
         * The parked trip's pin (#2053) tops the scale, and this is a *tappability* decision before it is
         * a drawing one: gms sends a tap to the topmost marker under the finger, so anything above the pin
         * takes taps meant for it. A stop sitting under the pin is the ordinary case — trips start at
         * stops — and left at the default z-index the stop (0.75) swallowed every tap on it.
         *
         * Above even the fast-estimate marker because of what it is: the one object on this map the rider
         * put there themselves, and the only way back to a trip they parked. Everything it can cover is
         * something they can still reach by moving the map a little; the pin is not.
         */
        private const val PINNED_TRIP_Z_INDEX = 5f

        // The ping ripple draws above the route line/band (gms always draws Circles beneath markers, so it
        // never covers the vehicle icon regardless of this value).
        private const val PING_Z_INDEX = 3f

        // The route-continuation badge (#1691) draws above vehicles so it's always reliably tappable.
        private const val ROUTE_BADGE_Z_INDEX = 1.5f

        // The stamp bitmap is twice as long as its chevron, halving repetition without shrinking it.
        private const val CHEVRON_REPEAT_SCALE = 2

        // The dash rhythms named by RouteLineDash, in screen pixels like every other gms polyline
        // dimension here. A TRAIL stride is twice a HINT's over the same gap, so an itinerary's walking
        // and cycling legs read as a broken path rather than as the route-continuation line's tight
        // preview dashes (#1691) — without the dashes running so long that a short leg draws as one
        // unbroken stroke.
        private const val HINT_DASH_LENGTH_PX = 24f
        private const val TRAIL_DASH_LENGTH_PX = HINT_DASH_LENGTH_PX * 2f
        private const val DASH_GAP_LENGTH_PX = 16f

        // The (arbitrary, constant) ease key for a TripEstimateMarker's single-marker easer.
        private const val ESTIMATE_EASE_KEY = "estimate"

        private const val MOST_RECENT_DATA_TITLE = "Most recent data"

        // Comfortably covers a busy route's live working set — a vehicle type's 8 heading octants across a
        // handful of route colors, times a few route types, plus the fast-estimate + dot icons
        // — so descriptors are reused as vehicles turn, not thrashed. Bounded so a long, varied session
        // can't grow it without limit (evicting a still-shown icon just re-wraps it on next request).
        //
        // Route labels share this cache, and since #2195 they are the larger half of it: every label on the
        // map now takes a zoom schedule, so a busy stop's dozen adjacency labels can each be asked for at
        // any of the nine sizes [RouteBadgeScaleProfile.scaleAt]'s sixteenths quantize the ramp to. Sized to
        // hold that alongside the vehicles rather than letting a zoom-out sweep evict the icons the ~20Hz
        // vehicle re-stamp depends on. The maplibre flavor gives its labels a cache of their own, so its
        // BADGE_ICON_CACHE_SIZE covers only the labels.
        private const val DESCRIPTOR_CACHE_SIZE = 384
    }
}

// Internal (not private) so the inner marker/overlay classes call it without a synthetic accessor
// (lint SyntheticAccessor); it's a trivial converter used only within this file.
internal fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
