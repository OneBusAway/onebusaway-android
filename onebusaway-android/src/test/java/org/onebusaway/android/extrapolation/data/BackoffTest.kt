/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exponential backoff that paces the repository's polling Flows ([nextPollDelayMs]). */
class BackoffTest {

    private val interval = 10_000L

    @Test
    fun `no failures returns the base interval`() {
        assertEquals(interval, nextPollDelayMs(0, interval))
    }

    @Test
    fun `first failure retries well inside the interval`() {
        assertEquals(1_000L, nextPollDelayMs(1, interval))
    }

    @Test
    fun `consecutive failures keep doubling`() {
        assertEquals(2_000L, nextPollDelayMs(2, interval))
        assertEquals(4_000L, nextPollDelayMs(3, interval))
        assertEquals(8_000L, nextPollDelayMs(4, interval))
        assertEquals(16_000L, nextPollDelayMs(5, interval))
    }

    @Test
    fun `failures are capped at eight times the interval`() {
        assertEquals(64_000L, nextPollDelayMs(7, interval))
        assertEquals(80_000L, nextPollDelayMs(8, interval))
        assertEquals(80_000L, nextPollDelayMs(9, interval))
    }

    @Test
    fun `a long outage stays at the cap without overflowing`() {
        assertEquals(80_000L, nextPollDelayMs(1_000, interval))
        assertEquals(80_000L, nextPollDelayMs(Int.MAX_VALUE, interval))
    }

    @Test
    fun `a 429 retries after twice the interval, not promptly`() {
        assertEquals(20_000L, nextPollDelayMs(1, interval, rateLimited = true))
    }

    @Test
    fun `consecutive 429s keep doubling up to the same cap`() {
        assertEquals(40_000L, nextPollDelayMs(2, interval, rateLimited = true))
        assertEquals(80_000L, nextPollDelayMs(3, interval, rateLimited = true))
        assertEquals(80_000L, nextPollDelayMs(4, interval, rateLimited = true))
        assertEquals(80_000L, nextPollDelayMs(Int.MAX_VALUE, interval, rateLimited = true))
    }

    @Test
    fun `a 429 never shortens the wait it would otherwise have`() {
        for (failures in 1..12) {
            assertTrue(nextPollDelayMs(failures, interval, rateLimited = true) >= nextPollDelayMs(failures, interval))
        }
    }
}
