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
import org.onebusaway.android.time.ElapsedTime

/**
 * Unit tests for [nextVehicleDelay] — the resume-mid-period timing for the route vehicle poll,
 * ported from the legacy RouteMapController.onResume math. Both readings are monotonic-clock
 * [ElapsedTime]s, so "never loaded" is the absent reading rather than a zero sentinel.
 */
class RouteVehicleDelayTest {

    private val base = ElapsedTime(1_000_000L) // arbitrary monotonic "lastUpdated"

    private fun after(lastUpdated: ElapsedTime, elapsedMillis: Long) = ElapsedTime(lastUpdated.ms + elapsedMillis)

    @Test
    fun `never loaded waits a full period`() {
        assertEquals(VEHICLE_REFRESH_PERIOD_MS, nextVehicleDelay(lastUpdated = null, now = base))
    }

    @Test
    fun `mid-period waits only the remainder`() {
        val now = after(base, elapsedMillis = 3000)
        assertEquals(VEHICLE_REFRESH_PERIOD_MS - 3000, nextVehicleDelay(base, now))
    }

    @Test
    fun `overdue refreshes almost immediately`() {
        val now = after(base, elapsedMillis = VEHICLE_REFRESH_PERIOD_MS + 5000)
        assertEquals(100L, nextVehicleDelay(base, now))
    }

    @Test
    fun `just loaded waits the full period`() {
        assertEquals(VEHICLE_REFRESH_PERIOD_MS, nextVehicleDelay(base, now = base))
    }
}
