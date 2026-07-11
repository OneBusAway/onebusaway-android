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
import org.junit.Test

/**
 * Unit tests for [liveServerTime], the pure projection behind [rememberLiveServerTime] — a live "now"
 * in the server clock domain used to count ETA pills down between polls (issue #1781). It anchors on
 * a poll's server-clock reading and advances it by elapsed **device** time, so the result is immune to
 * device clock skew (#1612), mirroring `serverNowMs` in `VehicleInfoWindow`.
 */
class ServerTimeTickerTest {

    @Test
    fun `at the capture instant it equals the server time`() {
        val server = ServerTime(1_700_000_000_000L)
        val deviceStart = ElapsedTime(5_000L)
        assertEquals(server, liveServerTime(server, deviceStart, nowElapsed = deviceStart))
    }

    @Test
    fun `it advances by the elapsed device time`() {
        val server = ServerTime(1_700_000_000_000L)
        val deviceStart = ElapsedTime(5_000L)
        // 12s of real device time elapsed since the response was observed.
        assertEquals(
            ServerTime(server.epochMs + 12_000L),
            liveServerTime(server, deviceStart, nowElapsed = ElapsedTime(deviceStart.ms + 12_000L))
        )
    }

    @Test
    fun `a device-now before the anchor is clamped, never yielding a past 'now'`() {
        // The synchronous remember(serverTime) anchor can be captured up to ~1s ahead of the
        // 1s-lagged ticker; the clamp must keep the result at serverTime, not before it.
        val server = ServerTime(1_700_000_000_000L)
        val deviceStart = ElapsedTime(5_000L)
        assertEquals(server, liveServerTime(server, deviceStart, nowElapsed = ElapsedTime(deviceStart.ms - 800L)))
    }

    @Test
    fun `device clock offset from server does not leak into the result`() {
        // Two devices with very different elapsed-clock offsets that observe the same server time and
        // let the same real time pass report the same server-domain "now" — only the delta matters.
        val server = ServerTime(1_700_000_000_000L)
        val startA = ElapsedTime(9_000_000L)
        val startB = ElapsedTime(9_000_000L + 600_000L)
        val elapsed = 30_000L
        assertEquals(
            liveServerTime(server, startA, ElapsedTime(startA.ms + elapsed)),
            liveServerTime(server, startB, ElapsedTime(startB.ms + elapsed))
        )
    }
}
