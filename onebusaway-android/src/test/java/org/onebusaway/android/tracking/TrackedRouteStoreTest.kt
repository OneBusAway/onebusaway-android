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
import org.onebusaway.android.time.WallTime

/** The tracked set as riders mutate it, and its survival across a process death. */
class TrackedRouteStoreTest {

    private fun route(stopId: String = "1_100", headsign: String = "Downtown Seattle") = TrackedRoute(
        key = TrackedRouteKey(stopId, "1_40", headsign),
        routeName = "40",
        stopName = "Pine St & 3rd Ave",
        stopLat = 47.61,
        stopLon = -122.33
    )

    /** The same row as the store holds it: [TrackedRouteStore.track] stamps when the session began. */
    private fun tracked(stopId: String = "1_100", headsign: String = "Downtown Seattle") = route(stopId, headsign).copy(startedAtMs = NOW.epochMs)

    @Test
    fun `tracking a row records it`() {
        val store = TrackedRouteStore(FakePreferencesRepository())

        store.track(route(), NOW)

        assertEquals(listOf(tracked()), store.routes.value)
        assertTrue(store.isTracking(route().key))
    }

    @Test
    fun `the tracked set survives a process death`() {
        val preferences = FakePreferencesRepository()
        TrackedRouteStore(preferences).track(route(), NOW)

        // A fresh store over the same storage is what a sticky service restart sees.
        assertEquals(listOf(tracked()), TrackedRouteStore(preferences).routes.value)
    }

    @Test
    fun `untracking the last row clears the stored slot`() {
        val preferences = FakePreferencesRepository()
        val store = TrackedRouteStore(preferences)
        store.track(route(), NOW)

        store.untrack(route().key)

        assertEquals(emptyList<TrackedRoute>(), store.routes.value)
        assertEquals(emptyList<TrackedRoute>(), TrackedRouteStore(preferences).routes.value)
    }

    @Test
    fun `the notification action untracks the row its card was posted for`() {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(stopId = "1_100"), NOW)
        store.track(route(stopId = "1_200"), NOW)

        store.untrackById(route(stopId = "1_100").id)

        assertEquals(listOf(tracked(stopId = "1_200")), store.routes.value)
    }

    @Test
    fun `an untracked row is no longer reported as tracked`() {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(headsign = "Downtown Seattle"), NOW)
        store.track(route(headsign = "Ballard"), NOW)

        store.untrack(route(headsign = "Ballard").key)

        assertTrue(store.isTracking(route(headsign = "Downtown Seattle").key))
        assertFalse(store.isTracking(route(headsign = "Ballard").key))
    }

    @Test
    fun `the key overlay follows the tracked set`() = runTest {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(), NOW)

        assertEquals(setOf(route().key), store.trackedKeys.first())
    }

    @Test
    fun `clearing removes everything`() {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(stopId = "1_100"), NOW)
        store.track(route(stopId = "1_200"), NOW)

        store.clear()

        assertEquals(emptyList<TrackedRoute>(), store.routes.value)
    }

    @Test
    fun `tracking stamps when the session began`() {
        val store = TrackedRouteStore(FakePreferencesRepository())

        store.track(route(), NOW)

        assertEquals(NOW, store.routes.value.single().startedAt)
    }

    @Test
    fun `re-tracking a live row re-dates it`() {
        // Asking again is asking for the whole session again, not the tail of the old one — otherwise
        // a row re-tracked all afternoon would still retire on the first tap's clock.
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(), NOW)

        val later = WallTime(NOW.epochMs + 60_000)
        store.track(route(), later)

        assertEquals(later, store.routes.value.single().startedAt)
    }

    private companion object {
        val NOW = WallTime(1_700_000_000_000L)
    }
}
