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
package org.onebusaway.android.map.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class MapRenderStateRouteStopScaleTest {

    @Test
    fun `route mode and focused-stop adjacency share the route stop zoom scale`() {
        val state = MapRenderState()
        val route = RoutePolyline(
            color = 1,
            points = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0))
        )

        state.setRoutePolylines(listOf(route), routeModeScalesStopsWithZoom = true)
        assertTrue(state.snapshot.value.routeStopsScaleWithZoom)

        state.setRoutePolylines(listOf(route))
        assertFalse(state.snapshot.value.routeStopsScaleWithZoom)

        state.setFocusedStopId("stop")
        assertTrue(state.snapshot.value.routeStopsScaleWithZoom)
    }

    @Test
    fun `adjacent stops recede only in stop focus, not route mode or plain map`() {
        val state = MapRenderState()
        val route = RoutePolyline(
            color = 1,
            points = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0))
        )

        // Plain map: no focused stop, no route mode.
        state.setRoutePolylines(listOf(route))
        assertFalse(state.snapshot.value.stopFocusRecedesAdjacent)

        // Stop focus with no route selected: adjacent circles recede.
        state.setFocusedStopId("stop")
        assertTrue(state.snapshot.value.stopFocusRecedesAdjacent)

        // Route mode (with a focused stop): adjacency no longer recedes — one route is emphasized instead.
        state.setRoutePolylines(listOf(route), routeModeScalesStopsWithZoom = true)
        assertFalse(state.snapshot.value.stopFocusRecedesAdjacent)
    }
}
