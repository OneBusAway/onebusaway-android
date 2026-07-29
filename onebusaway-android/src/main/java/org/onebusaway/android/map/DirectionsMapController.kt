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
import org.onebusaway.android.directions.model.routeDisplayLabel
import org.onebusaway.android.map.layout.RouteBadgeLayoutInput
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.layoutRouteBadges
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.ObaShape
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.parseObaHexColor

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

    /** Draw [itinerary]'s leg polylines + start/end pins and frame it. */
    fun start(itinerary: TripItinerary) {
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

        // Every leg that has geometry to draw, decoded and styled once: the polylines below stroke it and
        // the route labels are anchored on it, so a label cannot end up a different colour from its own
        // line (a leg without geometry draws neither).
        val drawableLegs = legs.mapIndexedNotNull { legIndex, leg ->
            val geometry = leg.legGeometry ?: return@mapIndexedNotNull null
            val shape = LegShape(geometry)
            if (shape.length <= 0) return@mapIndexedNotNull null
            val style = itineraryLegStyle(leg.legKind(), parseObaHexColor(leg.routeColor))
            ItineraryDrawableLeg(legIndex, leg, shape.points, style)
        }
        // Build every leg's polyline (each in its own mode/route style), keeping each one's leg index so
        // a later focus can name legs rather than drawn positions.
        legLines = drawableLegs.map { drawable ->
            // Every leg's points run in travel order, so whether it stamps chevrons is the style's
            // call — a dashed on-street stroke declines them (see [itineraryLegStyle]).
            ItineraryLegLine(
                drawable.index,
                RoutePolyline(
                    drawable.style.color,
                    drawable.points,
                    widthProfile = drawable.style.widthProfile,
                    directional = drawable.style.directional,
                    dash = drawable.style.dash
                )
            )
        }
        host.renderState.setRouteBadges(itineraryRouteBadges(drawableLegs))
        // A freshly drawn itinerary is the overview: every leg at full weight until one is focused.
        focusedLegIndices = emptySet()
        publishLegs()

        if (startLat != null && startLon != null) {
            directionsMarkerIds.add(host.addMarker(startLat, startLon, HUE_GREEN))
        }
        if (endLat != null && endLon != null) {
            directionsMarkerIds.add(host.addMarker(endLat, endLon, HUE_RED))
        }

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

        /**
         * The bike-share stations an itinerary references, to filter the bike overlay to the trip's own
         * stations (ported from the legacy DirectionsMapController; passed to the bike loader).
         */
        fun bikeStationIdsFromItinerary(itinerary: TripItinerary): List<String> {
            val ids = ArrayList<String>()
            for (leg in itinerary.legs) {
                if (leg.mode == TripMode.BICYCLE) {
                    if (leg.from.vertexType == TripVertexType.BIKESHARE) {
                        leg.from.bikeShareId?.let { ids.add(it) }
                    }
                    if (leg.to.vertexType == TripVertexType.BIKESHARE) {
                        leg.to.bikeShareId?.let { ids.add(it) }
                    }
                }
            }
            return ids
        }
    }
}

/**
 * One itinerary leg that has geometry to draw: its index in the itinerary, the leg itself, its decoded
 * points, and the stroke they're drawn in — resolved once, so the line and its label share it.
 */
internal data class ItineraryDrawableLeg(
    val index: Int,
    val leg: TripLeg,
    val points: List<GeoPoint>,
    val style: ItineraryLegStyle
)

/**
 * One route label per ride in a drawn itinerary, so a line on the directions map says which route it
 * is without the rider having to match it to the drawer beside it. Non-interactive: they name legs of
 * the trip already being read, so a tap must not navigate away into a single route's map.
 *
 * Anchored by the shared [layoutRouteBadges] used by focused-stop adjacency, so labels sit at stable
 * distance midpoints and stagger apart when two ridden corridors overlap. Each label takes the colour
 * its own line is stroked with ([itineraryLegStyle]), so the two can't disagree.
 */
internal fun itineraryRouteBadges(legs: List<ItineraryDrawableLeg>): List<RouteBadge> {
    val rides = legs.asSequence()
        .filter { it.leg.mode?.isTransit == true }
        .mapNotNull { drawable ->
            // A route that names itself in no way at all has nothing to label the line with.
            val name = drawable.leg.routeDisplayLabel() ?: return@mapNotNull null
            // Grouped by the wire route id, or — for an OTP1 response, which names a route without
            // identifying it — by that name. So two legs of one route (a stay-aboard interline, or a
            // route the itinerary returns to) share a single label rather than each getting their own.
            RideLabel(drawable.leg.routeId ?: name, name, drawable.style.color, drawable.points)
        }
        .groupBy(RideLabel::identity)
    val placements = layoutRouteBadges(
        rides.map { (identity, ridden) ->
            // Direction is the adjacency view's axis, not an itinerary's: a ride is already one
            // direction along its route, so every leg of it shares one label.
            RouteBadgeLayoutInput(RouteDirectionKey(identity, null), ridden.map { RouteBadgePath(it.points) })
        }
    ).associateBy { it.route.routeId }
    return rides.mapNotNull { (identity, ridden) ->
        // A ride whose every leg is too short to measure gets no anchor, and so no label.
        val point = placements[identity]?.point ?: return@mapNotNull null
        RouteBadge(
            routeId = identity,
            routeShortName = ridden.first().name,
            color = ridden.first().color,
            point = point,
            directionId = null,
            interactive = false
        )
    }
}

private data class RideLabel(
    val identity: String,
    val name: String,
    val color: Int,
    val points: List<GeoPoint>
)
