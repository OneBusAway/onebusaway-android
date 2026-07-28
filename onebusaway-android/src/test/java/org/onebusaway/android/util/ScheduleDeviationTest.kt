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
package org.onebusaway.android.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.util.ScheduleDeviation.Status

/**
 * The on-time band and the two color tiers (#2043).
 *
 * Before this, "on time" meant the predicted and scheduled minute-past-epoch were *exactly equal*,
 * which on live data almost never happened — a bus 20 s late rendered late. The band is OBA iOS's
 * (`ArrivalDeparture.swift:305`), adopted for parity, so these boundaries are the contract that keeps
 * the two apps agreeing; they are also the reason the threshold is sanctioned rather than invented.
 */
class ScheduleDeviationTest {

    private fun status(deviation: Duration, isRealtime: Boolean = true) = ScheduleDeviation.status(isRealtime, deviation)

    @Test
    fun `a vehicle just outside the band is early or late`() {
        assertEquals("91 s ahead is early", Status.EARLY, status((-91).seconds))
        assertEquals("91 s behind is late", Status.DELAYED, status(91.seconds))
    }

    @Test
    fun `a vehicle just inside the band is on time`() {
        assertEquals("89 s ahead is on time", Status.ON_TIME, status((-89).seconds))
        assertEquals("89 s behind is on time", Status.ON_TIME, status(89.seconds))
    }

    /**
     * iOS buckets with `minutesDiff < -1.5` then `< 1.5`, making the band half-open: exactly 90 s
     * early is on time, exactly 90 s late is not. Pinned so a later refactor to a symmetric
     * comparison doesn't silently drift off parity at the edge.
     */
    @Test
    fun `the band edges match the iOS half-open comparison`() {
        assertEquals("exactly 90 s ahead is still on time", Status.ON_TIME, status((-90).seconds))
        assertEquals("exactly 90 s behind is late", Status.DELAYED, status(90.seconds))
    }

    @Test
    fun `a vehicle exactly on schedule is on time`() {
        assertEquals(Status.ON_TIME, status(Duration.ZERO))
    }

    @Test
    fun `large deviations stay in their buckets`() {
        assertEquals(Status.EARLY, status((-2).hours))
        assertEquals(Status.DELAYED, status(2.hours))
    }

    /** The `predicted == false` case: there is nothing measured, so the deviation must be ignored. */
    @Test
    fun `without real-time the state is scheduled regardless of deviation`() {
        assertEquals(Status.SCHEDULED, status(Duration.ZERO, isRealtime = false))
        assertEquals(Status.SCHEDULED, status(30.minutes, isRealtime = false))
        assertEquals(Status.SCHEDULED, status((-30).minutes, isRealtime = false))
    }

    /**
     * The wording rounds rather than truncates, which is what keeps it consistent with the band: the
     * edges sit at 1.5 minutes, so anything bucketed early/late words as at least "2 min" and can
     * never read as a magnitude that falls inside the on-time window.
     */
    @Test
    fun `the worded magnitude never contradicts the band`() {
        for (seconds in longArrayOf(90, 91, 120, 200)) {
            val minutes = ScheduleDeviation.roundedMinutes(seconds.seconds)
            assertEquals("$seconds s must bucket as late", Status.DELAYED, status(seconds.seconds))
            assertTrue("$seconds s worded as $minutes min would read as on time", minutes >= 2)
        }
        assertEquals("just inside the band still rounds to 1", 1L, ScheduleDeviation.roundedMinutes(89.seconds))
    }

    @Test
    fun `the color helpers agree with the bucketing`() {
        assertEquals(R.color.stop_info_early, ScheduleDeviation.statusColor(true, (-91).seconds))
        assertEquals(R.color.stop_info_ontime, ScheduleDeviation.statusColor(true, 89.seconds))
        assertEquals(R.color.stop_info_delayed, ScheduleDeviation.statusColor(true, 91.seconds))
        assertEquals(R.color.stop_info_scheduled_time, ScheduleDeviation.statusColor(false, 91.seconds))

        assertEquals(R.color.stop_info_early_fill, ScheduleDeviation.fillColor(true, (-91).seconds))
        assertEquals(R.color.stop_info_ontime_fill, ScheduleDeviation.fillColor(true, 89.seconds))
        assertEquals(R.color.stop_info_delayed_fill, ScheduleDeviation.fillColor(true, 91.seconds))
        assertEquals(R.color.stop_info_scheduled_fill, ScheduleDeviation.fillColor(false, 91.seconds))
    }

    /**
     * The four states must be four *distinct* colors in both tiers. This is what broke on the agencyY
     * flavor, where on-time resolved through the brand color and collided with late — a rebrand could
     * make two states indistinguishable without any code change.
     */
    @Test
    fun `every state has its own color in both tiers`() {
        val foreground = Status.entries.map { it.colorRes }
        val fill = Status.entries.map { it.fillColorRes }

        assertEquals("foreground tier has a distinct color per state", Status.entries.size, foreground.toSet().size)
        assertEquals("on-fill tier has a distinct color per state", Status.entries.size, fill.toSet().size)
        for (state in Status.entries) {
            assertNotEquals(
                "${state.name} must not use the same resource for text and fill",
                state.colorRes,
                state.fillColorRes
            )
        }
    }
}
