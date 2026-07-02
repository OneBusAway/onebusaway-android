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
package org.onebusaway.android.map.compose

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [serverNowMs], the pure projection behind the vehicle info-window's live age text.
 * It anchors "now" on the poll's server `currentTime` and advances it by elapsed **device** time, so
 * the age it feeds is measured in the server clock domain and is immune to device clock skew (#1612).
 */
class ServerNowMsTest {

    @Test
    fun `at the capture instant it equals the server time`() {
        val server = 1_700_000_000_000L
        val deviceStart = 5_000L
        assertEquals(server, serverNowMs(server, deviceStart, deviceNowMs = deviceStart))
    }

    @Test
    fun `it advances by the elapsed device time`() {
        val server = 1_700_000_000_000L
        val deviceStart = 5_000L
        // 12s of real device time elapsed since the response was observed.
        assertEquals(server + 12_000L, serverNowMs(server, deviceStart, deviceNowMs = deviceStart + 12_000L))
    }

    @Test
    fun `device clock offset from server does not leak into the result`() {
        // The device wall clock is skewed +10min relative to the server, but only the *delta* between
        // deviceStart and deviceNow matters — the absolute device value cancels out. Two devices with
        // very different clocks that observe the same server time and elapse the same real time report
        // the same server-domain "now".
        val server = 1_700_000_000_000L
        val skewedStartA = 9_000_000L
        val skewedStartB = 9_000_000L + 600_000L // +10min skew
        val elapsed = 30_000L
        assertEquals(
            serverNowMs(server, skewedStartA, skewedStartA + elapsed),
            serverNowMs(server, skewedStartB, skewedStartB + elapsed)
        )
    }
}
