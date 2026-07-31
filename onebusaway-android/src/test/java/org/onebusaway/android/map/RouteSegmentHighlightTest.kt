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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.FOCUSED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_APPROACH_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_CONTEXT_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.util.GeoPoint

/** JVM tests for the pure trip-plan-leg segment highlighting helpers ([onSegment], [routePolylinesWithSegment]). */
class RouteSegmentHighlightTest {

    // A straight segment running north along a meridian.
    private val segment = listOf(GeoPoint(47.60, -122.33), GeoPoint(47.62, -122.33))

    private fun stop(id: String, lat: Double, lon: Double) = ObaStopElement(id = id, lat = lat, lon = lon)

    @Test
    fun onSegment_keepsStopsOnThePath_dropsFarOnes() {
        val stops = listOf(
            stop("on", 47.61, -122.3300), // right on the line
            stop("near", 47.61, -122.3302), // ~15 m off — within tolerance
            stop("off", 47.61, -122.3200) // ~750 m off — excluded
        )
        assertEquals(listOf("on", "near"), stops.onSegment(segment).map { it.id })
    }

    @Test
    fun onSegment_noSegment_keepsEveryStop() {
        val stops = listOf(stop("a", 47.6, -122.3), stop("b", 40.0, -120.0))
        assertEquals(stops, stops.onSegment(emptyList()))
    }

    @Test
    fun routePolylinesWithSegment_noSegment_returnsBaseUnchanged() {
        val base = listOf(RoutePolyline(color = 0xFF0000FF.toInt(), points = segment))
        assertEquals(base, routePolylinesWithSegment(base, emptyList(), routeColor = 0xFF00FF00.toInt()))
    }

    @Test
    fun routePolylinesWithSegment_casesTheApproach_andKeepsTheRideAtItsItineraryWeight() {
        val base = listOf(
            RoutePolyline(
                color = null,
                points = segment,
                widthProfile = FOCUSED_ROUTE_LINE_WIDTH_PROFILE,
                directional = true
            )
        )
        val result = routePolylinesWithSegment(base, segment, routeColor = 0xFF00FF00.toInt())

        assertEquals(2, result.size)
        // The approach steps down to its own thinnest itinerary weight, loses its arrows, and is solid rather
        // than the faint dashed line that used to compete with the legs beside it (#2082).
        val approach = result.first()
        assertEquals(ITINERARY_APPROACH_WIDTH_PROFILE, approach.widthProfile)
        assertEquals(RouteLineDash.NONE, approach.dash)
        assertFalse(approach.directional)
        // The ridden span rides on top at the weight it had as an itinerary leg, in the route colour,
        // directional — so drilling in doesn't make the ride itself thinner than it just was.
        val overlay = result.last()
        assertEquals(ITINERARY_RIDE_WIDTH_PROFILE, overlay.widthProfile)
        assertEquals(0xFF00FF00.toInt(), overlay.color)
        assertEquals(true, overlay.directional)
        // Both halves of the selected route carry a case, so the approach and the ride read as one line
        // rather than as two things that happen to meet.
        assertEquals(RouteLineCase.SELECTION, approach.case)
        assertEquals(RouteLineCase.SELECTION, overlay.case)
    }

    @Test
    fun routePolylinesWithSegment_layersApproach_thenJourneyContext_thenSelectedRide() {
        val base = listOf(RoutePolyline(color = 1, points = segment, directional = true))
        val journey = listOf(
            RoutePolyline(
                color = 2,
                points = segment.reversed(),
                widthProfile = ITINERARY_CONTEXT_WIDTH_PROFILE,
                dash = RouteLineDash.TRAIL
            )
        )

        val result = routePolylinesWithSegment(base, segment, routeColor = 3, itineraryContext = journey)

        assertEquals(listOf(1, 2, 3), result.map { it.color })
        assertEquals(ITINERARY_APPROACH_WIDTH_PROFILE, result[0].widthProfile)
        assertEquals(RouteLineDash.NONE, result[0].dash)
        assertEquals(ITINERARY_CONTEXT_WIDTH_PROFILE, result[1].widthProfile)
        assertEquals(RouteLineDash.TRAIL, result[1].dash)
        assertEquals(ITINERARY_RIDE_WIDTH_PROFILE, result[2].widthProfile)
        // Only the selected route is cased: the rest of the rider's journey is context, not selection.
        assertNotEquals(RouteLineCase.SELECTION, result[1].case)
        assertEquals(RouteLineCase.SELECTION, result[0].case)
        assertEquals(RouteLineCase.SELECTION, result[2].case)
    }

    @Test
    fun upstreamTo_keepsOnlyTheApproachThroughTheBoardingPoint() {
        val first = RoutePolyline(color = 1, points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.60, -122.33)))
        val second = RoutePolyline(color = 1, points = listOf(GeoPoint(47.60, -122.33), GeoPoint(47.64, -122.33)))

        val upstream = listOf(first, second).upstreamTo(GeoPoint(47.62, -122.33))

        assertEquals(2, upstream.size)
        assertEquals(first, upstream.first())
        assertEquals(47.62, upstream.last().points.last().latitude, 0.000001)
        assertFalse(upstream.last().points.any { it.latitude > 47.62 })
    }

    @Test
    fun containsRoutePoint_acceptsApproachingAndOnLegVehicles_andRejectsDownstreamVehicle() {
        val route = listOf(
            RoutePolyline(
                color = 1,
                points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.66, -122.33))
            )
        )
        val throughSelectedLeg = route.boundedThrough(GeoPoint(47.64, -122.33))

        assertTrue(throughSelectedLeg.containsRoutePoint(GeoPoint(47.60, -122.33))) // upstream
        assertTrue(throughSelectedLeg.containsRoutePoint(GeoPoint(47.63, -122.33))) // selected leg
        // Only ~11 m beyond alighting: spatial tolerance alone must not leak this downstream vehicle.
        assertFalse(throughSelectedLeg.containsRoutePoint(GeoPoint(47.6401, -122.33)))
    }

    @Test
    fun explicitlyFocusedEtaTripSurvivesAPlannedLegGeometryMismatch() {
        val eligible = listOf(
            RoutePolyline(
                color = 1,
                points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.64, -122.33))
            )
        ).boundedThrough(GeoPoint(47.62, -122.33))
        val offPath = GeoPoint(47.70, -122.40)

        assertFalse(focusedRideKeepsVehicle("other-trip", "tapped-trip", eligible, offPath))
        assertTrue(focusedRideKeepsVehicle("tapped-trip", "tapped-trip", eligible, offPath))
    }
}
