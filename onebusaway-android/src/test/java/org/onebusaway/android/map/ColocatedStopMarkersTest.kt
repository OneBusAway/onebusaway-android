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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.GeoPoint

class ColocatedStopMarkersTest {
    private val metro = StopReference(
        id = "1_590",
        name = "3rd Ave & Pine St",
        direction = "NW",
        lat = 47.611137,
        lon = -122.338951,
        routeIds = listOf("5", "40", "62")
    )
    private val express = metro.copy(id = "3_2479", routeIds = listOf("597"))

    @Test
    fun `one marker exposes both route sets regardless of response order`() {
        val a = marker(metro)
        val b = marker(express)
        for (stops in listOf(listOf(a, b), listOf(b, a))) {
            val result = mergeColocatedStopMarkers(stops, null).single()
            assertEquals(metro.id, result.id)
            assertEquals(listOf("5", "40", "62", "597"), result.routes.map { it.shortName })
            assertSame(a.stop, result.stop)
        }
    }

    @Test
    fun `a saved sibling is the marker target so its favorite can still be managed`() {
        val stops = listOf(marker(metro), marker(express).copy(favorite = true))
        val result = mergeColocatedStopMarkers(stops, null).single()
        assertEquals(express.id, result.id)
        assertTrue(result.favorite)
        assertEquals(4, result.routes.size)
    }

    @Test
    fun `a restored focus on either feed ID remains visible and keeps its identity`() {
        val stops = listOf(marker(metro), marker(express))
        for (id in listOf(metro.id, express.id)) {
            assertEquals(id, mergeColocatedStopMarkers(stops, id).single().id)
        }
        assertEquals(2, stops.size)
    }

    @Test
    fun `nearby bays opposite directions stations and ambiguous stops stay separate`() {
        val separate = listOf(
            metro.copy(id = "adjacent", lat = metro.lat + 0.000001),
            metro.copy(id = "opposite", direction = "SE"),
            metro.copy(id = "other-bay", name = "3rd Ave & Pine St - Bay B"),
            metro.copy(id = "station", locationType = 1),
            metro.copy(id = "unknown", direction = null),
            metro.copy(id = "unknown2", direction = null),
            metro.copy(id = "unnamed", name = null),
            metro.copy(id = "unnamed2", name = null)
        )
        val stops = (listOf(metro) + separate).map(::marker)
        assertEquals(stops, mergeColocatedStopMarkers(stops, null))
    }

    @Test
    fun `route presentations combine memberships only where the original and projected points match`() {
        val metroRoute = RouteDirectionKey("5", 0)
        val expressRoute = RouteDirectionKey("597", 0)
        val a = marker(metro).copy(presentedRoutes = setOf(metroRoute), routes = emptyList())
        val b = marker(express).copy(presentedRoutes = setOf(expressRoute), routes = emptyList())
        assertEquals(setOf(metroRoute, expressRoute), mergeColocatedStopMarkers(listOf(a, b), null).single().presentedRoutes)
        assertEquals(2, mergeColocatedStopMarkers(listOf(a, b.copy(point = GeoPoint(47.0, -122.0))), null).size)
        val differentStop = marker(metro.copy(id = "different", lat = 47.0)).copy(point = a.point)
        assertEquals(2, mergeColocatedStopMarkers(listOf(a, differentStop), null).size)
    }

    private fun marker(stop: StopReference) = StopMarker(
        id = stop.id,
        point = GeoPoint(stop.lat, stop.lon),
        direction = stop.direction.orEmpty(),
        routeType = ObaRoute.TYPE_BUS,
        stop = DtoStop(stop),
        routes = stop.routeIds.map { StopRoute(it, null) }
    )
}
