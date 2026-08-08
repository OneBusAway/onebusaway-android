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

import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.directions.model.decodedPoints
import org.onebusaway.android.directions.model.substitutableRoutes
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.ObaShape
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.parseObaHexColor

/**
 * Which of a drawn itinerary's two terminus pins to draw. A terminus the rider set to their current
 * location goes unpinned: the map's location layer already marks that exact point with the blue dot, so
 * a pin on top of it is redundant (#2111). Both pins are drawn unless a terminus says otherwise.
 */
data class ItineraryPins(val start: Boolean = true, val end: Boolean = true)

/**
 * The trip-plan directions use case (the legacy `DirectionsMapController`): draws an itinerary's legs
 * (each polyline styled by [itineraryLegStyle], each ride labelled by [itineraryRouteBadges]) plus
 * start/end pins, and frames the whole itinerary. A
 * synchronous driver over [MapHost] — it has no loader of its own (the itinerary is handed in), so it
 * just writes polylines + markers and dispatches the framing camera command.
 *
 * [start] draws an itinerary; [frameDirections] (re-appliable, since the [start]-time camera command
 * is lost before the adapter subscribes) fits it; [focusLegs] recedes all but the leg the rider is
 * reading; [clear] removes its start/end pins and forgets the itinerary. In directions mode the drawn
 * itinerary *is* the render state's whole polyline list (the owner clears it before each [start]), so
 * republishing here replaces it wholesale.
 * [setEndpoints] additionally draws standalone From/To pins as the endpoints resolve, before an
 * itinerary exists (superseded by the itinerary's own start/end pins once [start] runs).
 */
class DirectionsMapController(private val host: MapHost) {

    private val directionsMarkerIds = HashSet<Int>()

    // The drawn itinerary, retained so a leg focus can recompose it without rebuilding from the
    // itinerary, and so a leg's route sub-focus can take it as context (see [contextPolylines]).
    private var legLines: List<ItineraryLegLine> = emptyList()

    // Which legs the rider is currently reading; empty is the itinerary overview (every leg full weight).
    private var focusedLegIndices: Set<Int> = emptySet()

    // The directions framing intent, kept so [frameDirections] can (re)apply it once the map is ready
    // (the one-shot camera command dispatched at start time is lost before the adapter subscribes).
    private var directionsHasRoute = false

    private var directionsStart: GeoPoint? = null

    // The standalone From (green) / To (red) endpoint pins, shown as each endpoint resolves — before,
    // or without, a full itinerary. Tracked apart from the itinerary's own [directionsMarkerIds] pins so
    // they can be diffed and cleared independently; the itinerary's pins supersede them once [start]
    // runs (the owner calls [clearEndpoints]). See [setEndpoints].
    private var fromEndpoint: EndpointMarker? = null

    private var toEndpoint: EndpointMarker? = null

    private class EndpointMarker(val point: GeoPoint, val id: Int)

    /**
     * Draw [itinerary]'s leg polylines + start/end pins and frame it, stroking every leg through
     * [palette] — the directions view's own, which is the theme-aware colour the drawer badges this leg
     * with (see [directionsRouteLinePalette]). Passed per draw rather than held, so the palette that
     * resolved the current theme is the one this itinerary was drawn with. [pins] withholds a terminus
     * pin the trip's own endpoint made redundant (see [ItineraryPins]); framing is unaffected, since a
     * withheld pin doesn't move where the trip starts.
     */
    fun start(itinerary: TripItinerary, palette: RouteLinePalette, pins: ItineraryPins) {
        val legs = itinerary.legs
        if (legs.isEmpty()) {
            return
        }
        val firstLeg = legs.first()
        val lastLeg = legs.last()
        // A place is required on every leg, but its coordinates aren't (e.g. a vertex with no
        // geographic identity) — degrade to skipping the pin/start-framing for that endpoint rather
        // than crashing map rendering; the leg polylines below don't depend on either.
        val startPlace = firstLeg.from
        val endPlace = lastLeg.to
        val startLat = startPlace.lat
        val startLon = startPlace.lon
        val endLat = endPlace.lat
        val endLon = endPlace.lon

        // The routes the *drawer* offers for each leg, not the raw interchangeable set: `substitutableRoutes`
        // is already narrowed by what the rest of the plan needs of this leg (a leg of a stay-aboard
        // interline admits no substitute at all, #2000 × #2010), so the label on a line and the badge in the
        // drawer beside it name one ride the same way.
        //
        // Its alignment to the legs is stated rather than defended against, in the same terms
        // [ModeSymbols.forLegs] states it for the same list: a short list read defensively would quietly
        // label a ride with fewer routes than it can be taken on — the rider is told to wait for the 1 Line
        // when the 2 Line would also do — and nothing downstream could tell that from a leg that genuinely
        // has no alternatives. Misalignment is a bug in the analysis, so say so where it is read.
        val substitutes = itinerary.substitutableRoutes()
        require(substitutes.size == legs.size) {
            "substitutable routes must be index-aligned to legs (${substitutes.size} vs ${legs.size})"
        }

        // Every leg that has geometry to draw, decoded and styled once: the polylines below stroke it and
        // the route labels are anchored on it, so a label cannot end up a different colour from its own
        // line (a leg without geometry draws neither).
        val drawableLegs = legs.mapIndexedNotNull { legIndex, leg ->
            val geometry = leg.legGeometry ?: return@mapIndexedNotNull null
            val shape = LegShape(geometry)
            if (shape.length <= 0) return@mapIndexedNotNull null
            val style = itineraryLegStyle(leg.legKind(), parseObaHexColor(leg.routeColor), palette)
            // The wire hex is parsed here, on the leg and on every route offered in its place, so the
            // styling itself stays free of `android.graphics` (see the [ItineraryLegStyle] file header).
            val interchangeable = substitutes[legIndex].map { route ->
                ItinerarySubstitute(route, parseObaHexColor(route.routeColor))
            }
            ItineraryDrawableLeg(legIndex, leg, shape.points, style, interchangeable)
        }
        // Every leg's points run in travel order, but no itinerary leg stamps chevrons any more — see
        // [itineraryLegStyle], which also decides the hairline case a ride wears.
        val caps = itineraryLegCaps(legs)
        legLines = drawableLegs.map { (legIndex, _, points, style) ->
            val legCaps = caps[legIndex]
            ItineraryLegLine(
                legIndex,
                RoutePolyline(
                    style.color,
                    points,
                    widthProfile = style.widthProfile,
                    dash = style.dash,
                    case = style.case,
                    // A cutover (#2127) and a bulb are alternatives, not additions — the cut goes precisely
                    // where the ride runs on and the bulb is therefore withheld, which is what this `when`
                    // says and what a pair of booleans left each renderer to decide for itself.
                    startMark = when {
                        legCaps.startSeam -> RouteLineMark.INTERLINE_CUT
                        style.roundCaps && legCaps.start -> RouteLineMark.BULB
                        else -> RouteLineMark.NONE
                    },
                    endMark = if (style.roundCaps && legCaps.end) RouteLineMark.BULB else RouteLineMark.NONE
                )
            )
        }
        // A freshly drawn itinerary is the overview: every leg at full weight until one is focused.
        focusedLegIndices = emptySet()
        publishLegs()

        if (pins.start && startLat != null && startLon != null) {
            directionsMarkerIds.add(host.addMarker(startLat, startLon, HUE_GREEN))
        }
        if (pins.end && endLat != null && endLon != null) {
            directionsMarkerIds.add(host.addMarker(endLat, endLon, HUE_RED))
        }
        // Published after the pins, since the static layer is redrawn wholesale on each emission that
        // reaches it: badges written first would be built again for every pin added after them.
        host.renderState.setRouteBadges(itineraryRouteBadges(drawableLegs, palette))

        directionsHasRoute = legLines.isNotEmpty()
        directionsStart = if (startLat != null && startLon != null) GeoPoint(startLat, startLon) else null
        frameDirections()
    }

    /**
     * Recede all but [legIndices] — the leg (or folded interline chain) the rider has just focused —
     * leaving the rest of the trip on the map as faint context instead of erasing it (#2048). An empty
     * set restores the overview. A no-op when no itinerary is drawn.
     */
    fun focusLegs(legIndices: Set<Int>) {
        if (legLines.isEmpty() || focusedLegIndices == legIndices) return
        focusedLegIndices = legIndices
        publishLegs()
    }

    /**
     * The whole drawn itinerary reduced to journey context, for a transit leg's route sub-focus (#2048).
     * It remains stronger than the unused remainder of that route, since these are legs the rider will
     * actually travel. Every leg is included — even the focused one, whose ridden segment the route view
     * redraws at full weight over its context copy — so this needs no notion of the current focus.
     */
    fun contextPolylines(): List<RoutePolyline> = legLines.map { it.line }.asItineraryContext()

    private fun publishLegs() {
        host.renderState.setRoutePolylines(legLines.withLegFocus(focusedLegIndices))
    }

    /**
     * Frames the current directions itinerary: fit the route shape, or (no route — start == end)
     * center on the start at the default zoom. Both cases route through [MapHost] framing helpers
     * ([MapHost.frameItinerary] / [MapHost.frameStart]), which dispatch now if the map adapter is
     * attached and otherwise defer until it subscribes — so a frame issued before the adapter subscribes
     * (the map is drawn behind the results sheet the instant a plan completes) isn't dropped.
     */
    fun frameDirections() {
        if (directionsHasRoute) {
            host.frameItinerary()
        } else {
            directionsStart?.let { host.frameStart(it.latitude, it.longitude) }
        }
    }

    /**
     * Forget the drawn itinerary: remove the start/end pins and drop the retained legs (the owner clears
     * the leg polylines themselves via the shared polyline list). Called on every transition that leaves
     * the trip behind — a leg's route sub-focus deliberately doesn't, so [contextPolylines] survives into it.
     */
    fun clear() {
        directionsMarkerIds.forEach { host.removeMarker(it) }
        directionsMarkerIds.clear()
        legLines = emptyList()
        focusedLegIndices = emptySet()
        host.renderState.setRouteBadges(emptyList())
    }

    /**
     * Draw or update the standalone From (green) / To (red) endpoint pins as the user's endpoints
     * resolve, before an itinerary exists. Diffs against the current pins so an unchanged endpoint keeps
     * its marker (no flicker) and a null endpoint drops its pin. Reuses the same green/red hues as the
     * itinerary's start/end pins.
     */
    fun setEndpoints(from: GeoPoint?, to: GeoPoint?) {
        fromEndpoint = reconcileEndpoint(fromEndpoint, from, HUE_GREEN)
        toEndpoint = reconcileEndpoint(toEndpoint, to, HUE_RED)
    }

    /** Remove both endpoint pins (leaving directions, or the itinerary's own pins took over). */
    fun clearEndpoints() = setEndpoints(from = null, to = null)

    private fun reconcileEndpoint(current: EndpointMarker?, point: GeoPoint?, hue: Float): EndpointMarker? {
        if (current?.point == point) return current
        current?.let { host.removeMarker(it.id) }
        return point?.let { EndpointMarker(it, host.addMarker(it.latitude, it.longitude, hue)) }
    }

    /** An [ObaShape] over a [TripLegGeometry] (ported from the legacy DirectionsMapController). */
    private class LegShape(private val geometry: TripLegGeometry) : ObaShape {
        override val length: Int get() = geometry.length
        override val points: List<GeoPoint> get() = geometry.decodedPoints()
        override val rawPoints: String get() = geometry.points.orEmpty()
    }

    companion object {
        // BitmapDescriptorFactory hues for the directions start/end pins (green/red), kept as literals
        // since the map package can't depend on the Google Maps classes.
        private const val HUE_GREEN = 120.0f

        private const val HUE_RED = 0.0f
    }
}
