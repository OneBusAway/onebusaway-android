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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** JVM unit tests for [TripMonitorWindow] — the monitor's start/stop timing boundaries. */
class TripMonitorWindowTest {

    private val window = 1.hours
    private val nowMs = 100.hours.inWholeMilliseconds // arbitrary fixed "now"
    private val now = WallTime(nowMs)

    // -- shouldStartNow: begin polling once inside the pre-departure window --------------------

    @Test
    fun shouldStartNow_farBeforeWindow_false() {
        val departure = WallTime(nowMs + 3.hours.inWholeMilliseconds)
        assertFalse(TripMonitorWindow.shouldStartNow(departure, now, window))
    }

    @Test
    fun shouldStartNow_insideWindow_true() {
        val departure = WallTime(nowMs + 30.minutes.inWholeMilliseconds)
        assertTrue(TripMonitorWindow.shouldStartNow(departure, now, window))
    }

    @Test
    fun shouldStartNow_departurePassed_true() {
        val departure = WallTime(nowMs - 5.minutes.inWholeMilliseconds)
        assertTrue(TripMonitorWindow.shouldStartNow(departure, now, window))
    }

    // -- hasDeparted: stop once travel has begun ------------------------------------------------

    @Test
    fun hasDeparted_beforeDeparture_false() {
        val departure = ServerTime(nowMs + 10.minutes.inWholeMilliseconds)
        assertFalse(TripMonitorWindow.hasDeparted(departure, now))
    }

    @Test
    fun hasDeparted_afterDeparture_true() {
        val departure = ServerTime(nowMs - 1.seconds.inWholeMilliseconds)
        assertTrue(TripMonitorWindow.hasDeparted(departure, now))
    }

    @Test
    fun hasDeparted_unknownDeparture_false() {
        // null = departure time couldn't be parsed; don't bound on it (fall back to the trip-end guard).
        assertFalse(TripMonitorWindow.hasDeparted(null, now))
    }
}
