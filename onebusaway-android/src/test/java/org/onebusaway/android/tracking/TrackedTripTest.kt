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
class TrackedTripTest {

    private fun trip(
        stopId: String = "1_100",
        routeId: String = "1_40",
        headsign: String = "Downtown Seattle",
        tripId: String = "trip_1",
        serviceDate: Long = 1L,
        routeName: String = "40",
        plannedWaitSeconds: Int = 600
    ) = TrackedTrip(
        key = TrackedTripKey(stopId, routeId, headsign),
        tripId = tripId,
        serviceDate = serviceDate,
        routeName = routeName,
        stopName = "Pine St & 3rd Ave",
        plannedWaitSeconds = plannedWaitSeconds
    )

    @Test
    fun `tracking the same stop route and headsign twice keeps one session`() {
        // The onebusaway-ios #1215 bug: two surfaces naming the same bus produced two cards, because
        // each minted its own handle. Content identity collapses them however they were reached.
        val fromTheStopPage = trip()
        val fromABookmark = trip()

        val tracked = listOf(fromTheStopPage).withTracked(fromABookmark)

        assertEquals(1, tracked.size)
    }

    @Test
    fun `re-tracking a live session repoints it at the newly named instance`() {
        val theEarlierBus = trip(tripId = "trip_1")
        val theLaterBus = trip(tripId = "trip_2")

        val tracked = listOf(theEarlierBus).withTracked(theLaterBus)

        assertEquals(listOf(theLaterBus), tracked)
    }

    @Test
    fun `the most recently tracked trip takes first place`() {
        val first = trip(stopId = "1_100")
        val second = trip(stopId = "1_200")

        val tracked = listOf(first).withTracked(second)

        assertEquals(listOf(second, first), tracked)
    }

    @Test
    fun `re-tracking promotes rather than no-oping`() {
        // onebusaway-ios #1243: asking again for a bus you are already tracking means "show me this
        // one", so it has to move to the prominent slot rather than quietly doing nothing.
        val a = trip(stopId = "1_100")
        val b = trip(stopId = "1_200")
        val tracked = listOf(b, a)

        assertEquals(listOf(a, b), tracked.withTracked(a))
    }

    @Test
    fun `tracking past the limit drops the oldest session`() {
        val trips = (1..MAX_TRACKED_TRIPS).map { trip(stopId = "stop_$it") }
        val oldest = trips.first()
        val newest = trip(stopId = "stop_new")

        val tracked = trips.reversed().withTracked(newest)

        assertEquals(MAX_TRACKED_TRIPS, tracked.size)
        assertEquals(newest, tracked.first())
        assertEquals(false, tracked.contains(oldest))
    }

    @Test
    fun `untracking removes only the named session`() {
        val a = trip(stopId = "1_100")
        val b = trip(stopId = "1_200")

        assertEquals(listOf(b), listOf(a, b).withoutKey(a.key))
    }

    @Test
    fun `the same bus tracked at two stops is two instances`() {
        assertNotEquals(
            trip(stopId = "1_100").instanceId,
            trip(stopId = "1_200").instanceId
        )
    }

    @Test
    fun `two runs of the same trip on different service days are two instances`() {
        assertNotEquals(
            trip(serviceDate = 1L).instanceId,
            trip(serviceDate = 2L).instanceId
        )
    }

    @Test
    fun `a trip with no id cannot be tracked`() {
        // Nothing to re-resolve against a later response, so the countdown could never find it again.
        assertThrows(IllegalArgumentException::class.java) { trip(tripId = "") }
    }

    @Test
    fun `the persisted list round-trips`() {
        val trips = listOf(trip(stopId = "1_100"), trip(stopId = "1_200"))

        assertEquals(trips, TrackedTripsJson.decode(TrackedTripsJson.encode(trips)))
    }

    @Test
    fun `an unreadable payload decodes to null rather than throwing`() {
        assertNull(TrackedTripsJson.decode("{not json"))
    }

    @Test
    fun `a payload naming a trip with no id is unreadable, not silently accepted`() {
        // The model's own require() is part of what "readable" means: a stored row that could never
        // be re-resolved is a corrupt payload, not a tracked trip.
        assertNull(TrackedTripsJson.decode(TRIP_WITH_NO_ID))
    }

    private companion object {
        const val TRIP_WITH_NO_ID =
            """[{"key":{"stopId":"1_100","routeId":"1_40","headsign":"X"},"tripId":"",""" +
                """"serviceDate":1,"routeName":"40","stopName":"Pine","plannedWaitSeconds":60}]"""
    }
}
