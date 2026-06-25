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
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [nextVehicleDelay] — the resume-mid-period timing for the route vehicle poll,
 * ported from the legacy RouteMapController.onResume math. Timestamps are nanoseconds.
 */
class RouteVehicleDelayTest {

    private val base = 1_000_000_000L // arbitrary non-zero nanosecond "lastUpdated"

    private fun nanosAfter(lastUpdated: Long, elapsedMillis: Long): Long =
        lastUpdated + TimeUnit.MILLISECONDS.toNanos(elapsedMillis)

    @Test
    fun `never loaded waits a full period`() {
        assertEquals(VEHICLE_REFRESH_PERIOD_MS, nextVehicleDelay(lastUpdated = 0L, now = base))
    }

    @Test
    fun `mid-period waits only the remainder`() {
        val now = nanosAfter(base, elapsedMillis = 3000)
        assertEquals(VEHICLE_REFRESH_PERIOD_MS - 3000, nextVehicleDelay(base, now))
    }

    @Test
    fun `overdue refreshes almost immediately`() {
        val now = nanosAfter(base, elapsedMillis = VEHICLE_REFRESH_PERIOD_MS + 5000)
        assertEquals(100L, nextVehicleDelay(base, now))
    }

    @Test
    fun `just loaded waits the full period`() {
        assertEquals(VEHICLE_REFRESH_PERIOD_MS, nextVehicleDelay(base, now = base))
    }
}
