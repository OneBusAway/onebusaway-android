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

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [liveServerTime], the pure projection behind [rememberLiveServerTime] — a live "now"
 * in the server clock domain used to count ETA pills down between polls (issue #1781). It anchors on
 * a poll's server-clock reading and advances it by elapsed **device** time, so the result is immune to
 * device clock skew (#1612), mirroring `serverNowMs` in `VehicleInfoWindow`.
 *
 * Also covers the two shared rules every ETA surface reads off that clock: [etaMinutes]/[untilNextMinute],
 * the number and when it turns over, and [isEtaNow], the cutoff at which the number gives way to "NOW".
 */
class ServerTimeTickerTest {

    @Test
    fun `the next minute is when the printed ETA changes, and never zero away`() {
        // The contract a surface that redraws on a schedule depends on: wake here and the number is
        // exactly one lower; wake later and it stayed a minute high in between.
        val onTheMinute = ServerTime(1_700_000_040_000L)
        val elevenPast = ServerTime(onTheMinute.epochMs + 11_000L)
        val due = ServerTime(onTheMinute.epochMs + 5 * 60_000L)

        assertEquals(49.seconds, untilNextMinute(elevenPast))
        // On the boundary a whole minute remains — the minute that has only just started.
        assertEquals(1.minutes, untilNextMinute(onTheMinute))
        // And it really is the instant the printed number changes.
        assertEquals(5L, etaMinutes(due, elevenPast))
        assertEquals(4L, etaMinutes(due, elevenPast + untilNextMinute(elevenPast)))
    }

    @Test
    fun `NOW covers the whole window a departure is still being boarded in`() {
        // Issue #2177: this used to be an exact-zero test in the ETA pill, so NOW was reachable for at
        // most the one minute a departure spent at zero and a bus that had just pulled out printed
        // "-1min" — while the trip-tracking notification, on the same departure, said "Now".
        assertFalse(isEtaNow(1))
        assertTrue(isEtaNow(0))
        assertTrue(isEtaNow(-1))
        assertTrue(isEtaNow(-2))
        // Past the window it is a countdown again: the arrivals response reaches further back than
        // this, and a bus that left three minutes ago is gone rather than here.
        assertFalse(isEtaNow(-3))
    }

    @Test
    fun `the window's far edge is the last minute the tracking card holds a departure`() {
        // The notification drops a departure once it is past TRACKING_LINGER, and renders every one it
        // keeps as "Now" — so the two only stay consistent if nothing it can still hold falls outside
        // the NOW window. Stated as a test because the shade disagreeing with the arrivals row it was
        // copied from is exactly what #2177 was.
        assertTrue(isEtaNow(-NOW_WINDOW.inWholeMinutes))
        assertFalse(isEtaNow(-NOW_WINDOW.inWholeMinutes - 1))
    }

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
