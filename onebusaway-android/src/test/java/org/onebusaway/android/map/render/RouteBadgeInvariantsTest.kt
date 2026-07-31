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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.GeoPoint

/**
 * The invariants [RouteBadge] and [RouteBadgeTap] state about what a map label may be (#2083, #2101): it
 * names at least one route, a *route-navigating* one names exactly one, and a ride focus names at least
 * one leg. All producer-side, so these pin the contract a badge builder has to keep rather than behaviour
 * a rider sees.
 */
class RouteBadgeInvariantsTest {

    private val point = GeoPoint(47.6, -122.3)

    private val oneLine = BadgedRoute("1 Line", 0xFF1050C0.toInt())

    private val twoLine = BadgedRoute("2 Line", 0xFF107030.toInt())

    @Test
    fun `a label with nothing to read is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RouteBadge(routes = emptyList(), point = point) }
    }

    @Test
    fun `a route-navigating label naming several routes is rejected`() {
        // Where would the tap lead? A label that stacks routes may only lead somewhere that is about the
        // stack as a whole — which a ride focus is, and a single route's map isn't.
        assertThrows(IllegalArgumentException::class.java) {
            RouteBadge(
                routes = listOf(oneLine, twoLine),
                point = point,
                tap = RouteBadgeTap.ShowRoute(RouteDirectionKey("route-1", 0))
            )
        }
    }

    @Test
    fun `an inert label may name several routes, in the order given`() {
        val badge = RouteBadge(routes = listOf(oneLine, twoLine), point = point)

        assertEquals(listOf(oneLine, twoLine), badge.routes)
        assertEquals(null, badge.tap)
    }

    @Test
    fun `a route-navigating label names its one route`() {
        val tap = RouteBadgeTap.ShowRoute(RouteDirectionKey("route-1", 0))
        val badge = RouteBadge(routes = listOf(oneLine), point = point, tap = tap)

        assertEquals(tap, badge.tap)
        assertEquals("1 Line", badge.tappedRouteShortName)
    }

    @Test
    fun `a ride focus may name several routes - an interchangeable ride is still one ride`() {
        // The stacked-name rule is about the *destination*, not the label: the rider boards whichever of
        // these comes first, and either way it is the one ride this label sits on (#2010).
        val tap = RouteBadgeTap.FocusItineraryRide(setOf(2))
        val badge = RouteBadge(routes = listOf(oneLine, twoLine), point = point, tap = tap)

        assertEquals(tap, badge.tap)
    }

    @Test
    fun `a ride focus naming no leg is rejected`() {
        // It would read as a dead label rather than as the badge-builder bug it is.
        assertThrows(IllegalArgumentException::class.java) { RouteBadgeTap.FocusItineraryRide(emptySet()) }
    }
}
