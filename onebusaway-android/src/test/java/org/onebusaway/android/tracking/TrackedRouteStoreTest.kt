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
class TrackedRouteStoreTest {

    private fun route(stopId: String = "1_100", headsign: String = "Downtown Seattle") = TrackedRoute(
        key = TrackedRouteKey(stopId, "1_40", headsign),
        routeName = "40",
        stopName = "Pine St & 3rd Ave"
    )

    @Test
    fun `tracking a row records it`() {
        val store = TrackedRouteStore(FakePreferencesRepository())

        store.track(route())

        assertEquals(listOf(route()), store.routes.value)
        assertTrue(store.isTracking(route().key))
    }

    @Test
    fun `the tracked set survives a process death`() {
        val preferences = FakePreferencesRepository()
        TrackedRouteStore(preferences).track(route())

        // A fresh store over the same storage is what a sticky service restart sees.
        assertEquals(listOf(route()), TrackedRouteStore(preferences).routes.value)
    }

    @Test
    fun `untracking the last row clears the stored slot`() {
        val preferences = FakePreferencesRepository()
        val store = TrackedRouteStore(preferences)
        store.track(route())

        store.untrack(route().key)

        assertEquals(emptyList<TrackedRoute>(), store.routes.value)
        assertEquals(emptyList<TrackedRoute>(), TrackedRouteStore(preferences).routes.value)
    }

    @Test
    fun `the notification action untracks the row its card was posted for`() {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(stopId = "1_100"))
        store.track(route(stopId = "1_200"))

        store.untrackById(route(stopId = "1_100").id)

        assertEquals(listOf(route(stopId = "1_200")), store.routes.value)
    }

    @Test
    fun `an untracked row is no longer reported as tracked`() {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(headsign = "Downtown Seattle"))
        store.track(route(headsign = "Ballard"))

        store.untrack(route(headsign = "Ballard").key)

        assertTrue(store.isTracking(route(headsign = "Downtown Seattle").key))
        assertFalse(store.isTracking(route(headsign = "Ballard").key))
    }

    @Test
    fun `the key overlay follows the tracked set`() = runTest {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route())

        assertEquals(setOf(route().key), store.trackedKeys.first())
    }

    @Test
    fun `clearing removes everything`() {
        val store = TrackedRouteStore(FakePreferencesRepository())
        store.track(route(stopId = "1_100"))
        store.track(route(stopId = "1_200"))

        store.clear()

        assertEquals(emptyList<TrackedRoute>(), store.routes.value)
    }
}
