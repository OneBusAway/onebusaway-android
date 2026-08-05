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
package org.onebusaway.android.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The tracked-set identity and ordering rules — the two lessons stolen from onebusaway-ios. */
class TrackedRouteTest {

    private fun route(
        stopId: String = "1_100",
        routeId: String = "1_40",
        headsign: String = "Downtown Seattle",
        routeName: String = "40"
    ) = TrackedRoute(
        key = TrackedRouteKey(stopId, routeId, headsign),
        routeName = routeName,
        stopName = "Pine St & 3rd Ave",
        stopLat = 47.61,
        stopLon = -122.33
    )

    @Test
    fun `tracking the same stop route and headsign twice keeps one session`() {
        // The onebusaway-ios #1215 bug: two surfaces naming the same row produced two cards, because
        // each minted its own handle. Content identity collapses them however they were reached.
        val fromTheStopPage = route()
        val fromAStarredStop = route()

        val tracked = listOf(fromTheStopPage).withTracked(fromAStarredStop)

        assertEquals(1, tracked.size)
    }

    @Test
    fun `the same route in the other direction is a separate row`() {
        assertEquals(
            2,
            listOf(route(headsign = "Downtown Seattle"))
                .withTracked(route(headsign = "Ballard"))
                .size
        )
    }

    @Test
    fun `the same route at another stop is a separate row`() {
        assertEquals(
            2,
            listOf(route(stopId = "1_100")).withTracked(route(stopId = "1_200")).size
        )
    }

    @Test
    fun `the most recently tracked row takes first place`() {
        val first = route(stopId = "1_100")
        val second = route(stopId = "1_200")

        assertEquals(listOf(second, first), listOf(first).withTracked(second))
    }

    @Test
    fun `re-tracking promotes rather than no-oping`() {
        // onebusaway-ios #1243: asking again for a row you are already tracking means "show me this
        // one", so it has to move to the prominent slot rather than quietly doing nothing.
        val a = route(stopId = "1_100")
        val b = route(stopId = "1_200")

        assertEquals(listOf(a, b), listOf(b, a).withTracked(a))
    }

    @Test
    fun `tracking past the limit drops the oldest session`() {
        val routes = (1..MAX_TRACKED_ROUTES).map { route(stopId = "stop_$it") }
        val oldest = routes.first()
        val newest = route(stopId = "stop_new")

        val tracked = routes.reversed().withTracked(newest)

        assertEquals(MAX_TRACKED_ROUTES, tracked.size)
        assertEquals(newest, tracked.first())
        assertEquals(false, tracked.contains(oldest))
    }

    @Test
    fun `untracking removes only the named row`() {
        val a = route(stopId = "1_100")
        val b = route(stopId = "1_200")

        assertEquals(listOf(b), listOf(a, b).withoutKey(a.key))
    }

    @Test
    fun `each row has its own notification identity`() {
        assertNotEquals(route(stopId = "1_100").id, route(stopId = "1_200").id)
        assertNotEquals(route(headsign = "Downtown Seattle").id, route(headsign = "Ballard").id)
    }

    @Test
    fun `a row with no stop cannot be tracked`() {
        // Nothing to re-resolve against a later response, so the countdown could never find it again.
        assertThrows(IllegalArgumentException::class.java) { route(stopId = "") }
    }

    @Test
    fun `a row with no route cannot be tracked`() {
        assertThrows(IllegalArgumentException::class.java) { route(routeId = "") }
    }

    @Test
    fun `the persisted list round-trips`() {
        val routes = listOf(route(stopId = "1_100"), route(stopId = "1_200"))

        assertEquals(routes, TrackedRoutesJson.decode(TrackedRoutesJson.encode(routes)))
    }

    @Test
    fun `an unreadable payload decodes to null rather than throwing`() {
        assertNull(TrackedRoutesJson.decode("{not json"))
    }

    @Test
    fun `a payload naming a row with no stop is unreadable, not silently accepted`() {
        // The model's own require() is part of what "readable" means: a stored row that could never
        // be re-resolved is a corrupt payload, not a tracked route.
        assertNull(TrackedRoutesJson.decode(ROW_WITH_NO_STOP))
    }

    private companion object {
        const val ROW_WITH_NO_STOP =
            """[{"key":{"stopId":"","routeId":"1_40","headsign":"X"},"routeName":"40",""" +
                """"stopName":"Pine","stopLat":47.61,"stopLon":-122.33}]"""
    }
}
