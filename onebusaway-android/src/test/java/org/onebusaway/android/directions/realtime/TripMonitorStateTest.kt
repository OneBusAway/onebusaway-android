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
package org.onebusaway.android.directions.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests for [parseMonitorState] — the version/validity gate on a persisted monitor bundle. */
class TripMonitorStateTest {

    private val tripIds = listOf("1:trip_5")
    private val start = 1_700_000_000_000L
    private val end = 1_700_000_600_000L

    private val version = TripPlanMonitor.MONITOR_STATE_VERSION

    @Test
    fun validBundleParsesToState() {
        val parse = parseMonitorState(version, tripIds, start, end)
        assertTrue(parse is MonitorStateParse.Valid)
        val valid = parse as MonitorStateParse.Valid
        assertEquals(tripIds, valid.description.tripIds)
        assertEquals(start, valid.departure?.epochMs)
    }

    @Test
    fun absentVersionIsTreatedAsCompatibleLegacyState() {
        // A bundle written before versioning (v0) — its raw ids still normalize and compare correctly.
        val parse = parseMonitorState(version = null, tripIds = tripIds, startDateMillis = start, endDateMillis = end)
        assertTrue(parse is MonitorStateParse.Valid)
    }

    @Test
    fun unknownStartDateBecomesNullDeparture() {
        val parse = parseMonitorState(version, tripIds, startDateMillis = 0L, endDateMillis = end)
        assertTrue(parse is MonitorStateParse.Valid)
        assertNull((parse as MonitorStateParse.Valid).departure)
    }

    @Test
    fun emptyTripIdsIsMissing() {
        assertEquals(
            MonitorStateParse.Missing,
            parseMonitorState(version, tripIds = emptyList(), startDateMillis = start, endDateMillis = end)
        )
    }

    @Test
    fun nullTripIdsIsMissing() {
        assertEquals(
            MonitorStateParse.Missing,
            parseMonitorState(version, tripIds = null, startDateMillis = start, endDateMillis = end)
        )
    }

    @Test
    fun absentEndDateIsMissing() {
        assertEquals(
            MonitorStateParse.Missing,
            parseMonitorState(version, tripIds, startDateMillis = start, endDateMillis = 0L)
        )
    }

    @Test
    fun newerVersionIsIncompatible() {
        // A bundle from a future build we can't interpret: stop silently rather than misfire.
        assertEquals(
            MonitorStateParse.Incompatible,
            parseMonitorState(version + 1, tripIds, start, end)
        )
    }
}
