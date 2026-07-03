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
package org.onebusaway.android.widealerts

import com.google.transit.realtime.GtfsRealtime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure, `nowMs`-driven [GtfsAlertsHelper.isStartDateWithin24Hours]. "Now" is the
 * feed's server clock (#1612), so these pin the 24h cutoff, the boundary, and the future-start guard
 * against the server time passed in. ([GtfsAlertsHelper.isValidEntity] additionally reads the
 * alerts DB via a Context, so it isn't a pure JVM unit and is out of scope here.)
 */
class GtfsAlertsHelperTest {

    // A round server "now": epoch seconds 1_700_000_000 expressed in millis.
    private val nowSec = 1_700_000_000L
    private val nowMs = nowSec * 1000L
    private val hourSec = 3_600L
    private val dayMs = 24 * 60 * 60 * 1000L

    @Test
    fun `start one hour ago is within 24 hours`() {
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alertStartingAt(nowSec - hourSec), nowMs))
    }

    @Test
    fun `start 25 hours ago is not within 24 hours`() {
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alertStartingAt(nowSec - 25 * hourSec), nowMs))
    }

    @Test
    fun `start exactly 24 hours ago is still within the window`() {
        val alert = alertStartingAt(nowSec - 24 * hourSec)
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alert, nowMs))
        // One millisecond past the boundary is out.
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alert, nowMs + 1))
    }

    @Test
    fun `future-dated start is not within 24 hours`() {
        // Regression guard: a negative elapsed time must not slip past the upper bound.
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alertStartingAt(nowSec + hourSec), nowMs))
    }

    private fun alertStartingAt(startSec: Long): GtfsRealtime.Alert =
        GtfsRealtime.Alert.newBuilder()
            .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(startSec).build())
            .build()
}
