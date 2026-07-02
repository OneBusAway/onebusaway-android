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
import android.location.Location
import android.util.Log
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.models.ObaShape
import org.onebusaway.android.util.PolylineDecoder
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.RoutePolyline
import org.opentripplanner.api.model.EncodedPolylineBean
import org.opentripplanner.api.model.Itinerary
import org.opentripplanner.api.model.Leg
import org.opentripplanner.routing.core.TraverseMode
import org.opentripplanner.api.model.VertexType

/**
 * The trip-plan directions use case (the legacy `DirectionsMapController`): draws an itinerary's legs
 * (each polyline in its own mode color) plus start/end pins, and frames the whole itinerary. A
 * synchronous driver over [MapHost] — it has no loader of its own (the itinerary is handed in), so it
 * just writes polylines + markers and dispatches the framing camera command.
 *
 * [start] draws an itinerary; [frameDirections] (re-appliable, since the [start]-time camera command
 * is lost before the adapter subscribes) fits it; [clear] removes its start/end pins. The leg
 * polylines are cleared generically by the owner (they share the render state's polyline list).
 */
class DirectionsMapController(private val host: MapHost) {

    private val directionsMarkerIds = HashSet<Int>()

    // The directions framing intent, kept so [frameDirections] can (re)apply it once the map is ready
    // (the one-shot camera command dispatched at start time is lost before the adapter subscribes).
    private var directionsHasRoute = false

    private var directionsStart: GeoPoint? = null

    /** Draw [itinerary]'s leg polylines + start/end pins and frame it. */
    fun start(itinerary: Itinerary) {
        val legs = itinerary.legs
        if (legs.isEmpty()) {
            return
        }
        val firstLeg = legs.first()
        val lastLeg = legs.last()
        val startLat = firstLeg.from.lat
        val startLon = firstLeg.from.lon
        val endLat = lastLeg.to.lat
        val endLon = lastLeg.to.lon

        // Build every leg's polyline (each in its own mode color), then append them in one write —
        // matching the legacy per-leg append but without rebuilding the polyline list n times.
        val legPolylines = legs.mapNotNull { leg ->
            val shape = LegShape(leg.legGeometry)
            if (shape.length > 0) {
                RoutePolyline(resolveLegColor(leg), shape.points.map { it.toGeoPoint() })
            } else {
                null
            }
        }
        if (legPolylines.isNotEmpty()) {
            host.renderState.setRoutePolylines(host.renderState.getRoutePolylines() + legPolylines)
        }

        directionsMarkerIds.add(host.addMarker(startLat, startLon, HUE_GREEN))
        directionsMarkerIds.add(host.addMarker(endLat, endLon, HUE_RED))

        directionsHasRoute = legPolylines.isNotEmpty()
        directionsStart = GeoPoint(startLat, startLon)
        frameDirections()
    }

    /**
     * Frames the current directions itinerary: fit the route shape, or (no route — start == end)
     * center on the start at the default zoom. Re-appliable so the owner can frame once the map is
     * ready (the frame dispatched at [start] time is lost before the adapter subscribes).
     */
    fun frameDirections() {
        if (directionsHasRoute) {
            host.dispatchCamera(CameraCommand.FitToItinerary)
        } else {
            directionsStart?.let {
                host.dispatchCamera(
                    CameraCommand.Recenter(it.latitude, it.longitude, animate = false, applyRouteBias = false)
                )
                host.dispatchCamera(CameraCommand.SetZoom(MapParams.DEFAULT_ZOOM.toFloat()))
            }
        }
    }

    /** Remove the start/end pins (the owner clears the leg polylines via the shared polyline list). */
    fun clear() {
        directionsMarkerIds.forEach { host.removeMarker(it) }
        directionsMarkerIds.clear()
    }

    private fun resolveLegColor(leg: Leg): Int {
        // Color for transit routes when planning a trip.
        if (TraverseMode.valueOf(leg.mode).isTransit) {
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

    /** An [ObaShape] over an OTP [EncodedPolylineBean] leg geometry (ported from DirectionsMapController). */
    private class LegShape(private val bean: EncodedPolylineBean) : ObaShape {
        override val length: Int get() = bean.length
        override val points: List<Location> get() = PolylineDecoder.decodeLine(bean.points, bean.length)
        override val rawPoints: String get() = bean.points
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
        fun bikeStationIdsFromItinerary(itinerary: Itinerary): List<String> {
            val ids = ArrayList<String>()
            for (leg in itinerary.legs) {
                if (TraverseMode.BICYCLE.toString() == leg.mode) {
                    if (VertexType.BIKESHARE == leg.from.vertexType) {
                        ids.add(leg.from.bikeShareId)
                    }
                    if (VertexType.BIKESHARE == leg.to.vertexType) {
                        ids.add(leg.to.bikeShareId)
                    }
                }
            }
            return ids
        }
    }
}
