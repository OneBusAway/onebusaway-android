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
import org.junit.Test

/** The exponential backoff that paces the repository's polling Flows ([nextPollDelayMs]). */
class BackoffTest {

    private val interval = 10_000L

    @Test
    fun `success returns the base interval`() {
        assertEquals(interval, nextPollDelayMs(interval, true, interval))
    }

    @Test
    fun `success after a long backoff resets to the interval`() {
        assertEquals(interval, nextPollDelayMs(80_000L, true, interval))
    }

    @Test
    fun `first failure doubles the interval`() {
        assertEquals(20_000L, nextPollDelayMs(interval, false, interval))
    }

    @Test
    fun `consecutive failures keep doubling`() {
        assertEquals(40_000L, nextPollDelayMs(20_000L, false, interval))
        assertEquals(80_000L, nextPollDelayMs(40_000L, false, interval))
    }

    @Test
    fun `failures are capped at eight times the interval`() {
        assertEquals(80_000L, nextPollDelayMs(80_000L, false, interval))
        assertEquals(80_000L, nextPollDelayMs(100_000L, false, interval))
    }

    @Test
    fun `failure never returns below the interval even from a small seed`() {
        assertEquals(interval, nextPollDelayMs(0L, false, interval))
    }
}
