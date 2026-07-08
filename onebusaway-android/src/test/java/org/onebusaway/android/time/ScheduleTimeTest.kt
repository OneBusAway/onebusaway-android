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
package org.onebusaway.android.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Guards the civil/schedule-time types: [ScheduleTime] is recurring (no clock domain) and only reaches
 * the timeline through [ScheduleTime.resolve], which is the single place the seconds→millis and
 * service-day-anchor conventions live. Same-domain subtraction yields the scheduled interval as a
 * [kotlin.time.Duration]. The absence of a `ScheduleTime`↔clock-instant comparison is enforced by the
 * compiler, so its proof is that the app compiles.
 */
class ScheduleTimeTest {

    @Test
    fun `subtraction yields the scheduled interval`() {
        // 08:20:00 minus 08:15:00 into the service day = 5 minutes of scheduled run time.
        assertEquals(5.minutes, ScheduleTime(30_000L) - ScheduleTime(29_700L))
    }

    @Test
    fun `Comparable orders schedule times`() {
        assertTrue(ScheduleTime(29_700L) < ScheduleTime(30_000L))
        assertEquals(ScheduleTime(30_000L), ScheduleTime(30_000L))
    }

    @Test
    fun `resolve lands on the server clock at day-anchor plus offset`() {
        val day = ServiceDate(1_700_000_000_000L)
        // 3600 s into the service day = one hour of millis after the anchor.
        assertEquals(ServerTime(1_700_000_000_000L + 3_600_000L), ScheduleTime(3_600L).resolve(day))
    }

    @Test
    fun `the device-midnight fallback wraps its value without transforming it`() {
        // The named factory only exists to make the off-contract provenance greppable; it must not
        // re-derive or shift the anchor, so its result equals the plain constructor's.
        assertEquals(ServiceDate(1_700_000_000_000L), ServiceDate.approximateFromDeviceMidnight(1_700_000_000_000L))
    }

    @Test
    fun `resolve composes with the ServerTime group action`() {
        // The TripDetailsRepository path: resolve a scheduled stop, then shift by schedule deviation.
        val day = ServiceDate(1_700_000_000_000L)
        val scheduled = ScheduleTime(3_600L).resolve(day)
        val withDeviation = scheduled + 90.seconds // 90 s late
        assertEquals(90.seconds, withDeviation - scheduled)
        assertEquals(ServerTime(1_700_000_000_000L + 3_600_000L + 90_000L), withDeviation)
    }
}
