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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.testing.FakePreferencesRepository

/** The tracked set as riders mutate it, and its survival across a process death. */
class TrackedTripStoreTest {

    private fun trip(stopId: String = "1_100", tripId: String = "trip_1") = TrackedTrip(
        key = TrackedTripKey(stopId, "1_40", "Downtown Seattle"),
        tripId = tripId,
        serviceDate = 1L,
        routeName = "40",
        stopName = "Pine St & 3rd Ave",
        plannedWaitSeconds = 600
    )

    @Test
    fun `tracking a trip records it`() {
        val store = TrackedTripStore(FakePreferencesRepository())

        store.track(trip())

        assertEquals(listOf(trip()), store.trips.value)
        assertTrue(store.isTracking(trip().instanceId))
    }

    @Test
    fun `the tracked set survives a process death`() {
        val preferences = FakePreferencesRepository()
        TrackedTripStore(preferences).track(trip())

        // A fresh store over the same storage is what a sticky service restart sees.
        assertEquals(listOf(trip()), TrackedTripStore(preferences).trips.value)
    }

    @Test
    fun `untracking the last trip clears the stored slot`() {
        val preferences = FakePreferencesRepository()
        val store = TrackedTripStore(preferences)
        store.track(trip())

        store.untrack(trip().key)

        assertEquals(emptyList<TrackedTrip>(), store.trips.value)
        assertEquals(emptyList<TrackedTrip>(), TrackedTripStore(preferences).trips.value)
    }

    @Test
    fun `the notification action untracks by the instance it was posted for`() {
        val store = TrackedTripStore(FakePreferencesRepository())
        store.track(trip(stopId = "1_100"))
        store.track(trip(stopId = "1_200"))

        store.untrackInstance(trip(stopId = "1_100").instanceId)

        assertEquals(listOf(trip(stopId = "1_200")), store.trips.value)
    }

    @Test
    fun `an instance the session has moved off is no longer tracked`() {
        // Re-tracking the same (stop, route, headsign) with a later bus repoints the session, so the
        // earlier pill must stop offering "stop tracking".
        val store = TrackedTripStore(FakePreferencesRepository())
        val earlier = trip(tripId = "trip_1")
        store.track(earlier)

        store.track(trip(tripId = "trip_2"))

        assertFalse(store.isTracking(earlier.instanceId))
        assertTrue(store.isTracking(trip(tripId = "trip_2").instanceId))
    }

    @Test
    fun `the instance overlay follows the tracked set`() = runTest {
        val store = TrackedTripStore(FakePreferencesRepository())
        store.track(trip())

        assertEquals(setOf(trip().instanceId), store.trackedInstances.first())
    }

    @Test
    fun `clearing removes everything`() {
        val store = TrackedTripStore(FakePreferencesRepository())
        store.track(trip(stopId = "1_100"))
        store.track(trip(stopId = "1_200"))

        store.clear()

        assertEquals(emptyList<TrackedTrip>(), store.trips.value)
    }
}
