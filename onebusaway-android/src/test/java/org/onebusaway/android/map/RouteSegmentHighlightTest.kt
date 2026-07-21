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
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.FOCUSED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_PROFILE
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
    fun routePolylinesWithSegment_deemphasizesBase_andAddsNormalWidthOverlay() {
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
        // Base is thinned to context (and loses its arrows).
        assertEquals(DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE, result.first().widthProfile)
        // The ridden span rides on top at normal width, in the route colour, directional.
        val overlay = result.last()
        assertEquals(ROUTE_LINE_WIDTH_PROFILE, overlay.widthProfile)
        assertEquals(0xFF00FF00.toInt(), overlay.color)
        assertEquals(true, overlay.directional)
    }
}
