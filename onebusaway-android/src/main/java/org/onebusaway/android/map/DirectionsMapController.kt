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

import android.graphics.Color
import android.util.Log
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.directions.model.decodedPoints
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.models.ObaShape
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.map.render.RoutePolyline

/**
 * The trip-plan directions use case (the legacy `DirectionsMapController`): draws an itinerary's legs
 * (each polyline in its own mode color) plus start/end pins, and frames the whole itinerary. A
 * synchronous driver over [MapHost] — it has no loader of its own (the itinerary is handed in), so it
 * just writes polylines + markers and dispatches the framing camera command.
 *
 * [start] draws an itinerary; [frameDirections] (re-appliable, since the [start]-time camera command
 * is lost before the adapter subscribes) fits it; [clear] removes its start/end pins. The leg
 * polylines are cleared generically by the owner (they share the render state's polyline list).
 * [setEndpoints] additionally draws standalone From/To pins as the endpoints resolve, before an
 * itinerary exists (superseded by the itinerary's own start/end pins once [start] runs).
 */
class DirectionsMapController(private val host: MapHost) {

    private val directionsMarkerIds = HashSet<Int>()

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

        // Build every leg's polyline (each in its own mode color), then append them in one write —
        // matching the legacy per-leg append but without rebuilding the polyline list n times.
        val legPolylines = legs.mapNotNull { leg ->
            val geometry = leg.legGeometry ?: return@mapNotNull null
            val shape = LegShape(geometry)
            if (shape.length > 0) {
                // An itinerary leg is traversed one way; keep its travel-direction chevrons.
                RoutePolyline(
                    resolveLegColor(leg),
                    shape.points,
                    directional = true,
                )
            } else {
                null
            }
        }
        if (legPolylines.isNotEmpty()) {
            host.renderState.setRoutePolylines(host.renderState.getRoutePolylines() + legPolylines)
        }

        if (startLat != null && startLon != null) {
            directionsMarkerIds.add(host.addMarker(startLat, startLon, HUE_GREEN))
        }
        if (endLat != null && endLon != null) {
            directionsMarkerIds.add(host.addMarker(endLat, endLon, HUE_RED))
        }

        directionsHasRoute = legPolylines.isNotEmpty()
        directionsStart = if (startLat != null && startLon != null) GeoPoint(startLat, startLon) else null
        frameDirections()
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

    /** Remove the start/end pins (the owner clears the leg polylines via the shared polyline list). */
    fun clear() {
        directionsMarkerIds.forEach { host.removeMarker(it) }
        directionsMarkerIds.clear()
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

    private fun resolveLegColor(leg: TripLeg): Int {
        // Color for transit routes when planning a trip.
        if (leg.mode?.isTransit == true) {
            return OTPConstants.OTP_TRANSIT_COLOR
        }
        // Use the route's custom color if available.
        leg.routeColor?.let { hex ->
            try {
                return java.lang.Long.decode("0xFF$hex").toInt()
            } catch (ex: Exception) {
                Log.e(TAG, "Error parsing color=$hex: ${ex.message}")
            }
        }
        // Defaults to grey, which represents walking.
        return Color.GRAY
    }

    /** An [ObaShape] over a [TripLegGeometry] (ported from the legacy DirectionsMapController). */
    private class LegShape(private val geometry: TripLegGeometry) : ObaShape {
        override val length: Int get() = geometry.length
        override val points: List<GeoPoint> get() = geometry.decodedPoints()
        override val rawPoints: String get() = geometry.points.orEmpty()
    }

    companion object {
        private const val TAG = "DirectionsMapController"

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
