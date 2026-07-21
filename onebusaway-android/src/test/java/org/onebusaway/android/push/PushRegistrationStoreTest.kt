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
package org.onebusaway.android.push

import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.time.WallTime

/**
 * Unit tests for [PushRegistrationStore]'s clock edges — the [PushRegistrationStore.sinceLastSent]
 * contract that [decidePushRegistration]'s keep-alive relies on. The reconcile → apply → persist loop
 * as a whole is exercised in [PushRegistrationManagerTest]; these pin the store-level invariants that
 * are invisible there because the decision layer happens to tolerate their violation today.
 */
class PushRegistrationStoreTest {

    private val registration = PushRegistration(
        regionId = 1L,
        sidecarBaseUrl = "https://sidecar.example.org",
        token = "token-a",
        locale = "en-US",
        description = null
    )

    /** Mutable device clock, so a test can move it in either direction. */
    private var now = WallTime(1_000_000_000L)

    private val store = PushRegistrationStore(FakePreferencesRepository()) { now }

    @Test
    fun `a backwards clock jump reads as unknown rather than a negative elapsed time`() {
        store.record(registration)

        // The rider (or a bad network-time update) sets the device clock back: a negative elapsed time
        // would keep the keep-alive comparison false until the clock caught back up — for a large jump,
        // long enough for the server's 180-day prune to drop the device.
        now -= 2.hours
        assertNull("negative elapsed must read as unknown (= stale)", store.sinceLastSent())

        // Once the clock is past the write again, elapsed time reads normally.
        now += 3.hours
        assertEquals(1.hours, store.sinceLastSent())
    }

    @Test
    fun `clearing the record drops its sent-at timestamp with it`() {
        store.record(registration)
        now += 1.hours

        store.clear()

        assertNull(store.last())
        // The store must not report an elapsed-since-sent for a registration it says doesn't exist —
        // otherwise a later record's freshness could be read against a dead record's stamp.
        assertNull(store.sinceLastSent())
    }
}
