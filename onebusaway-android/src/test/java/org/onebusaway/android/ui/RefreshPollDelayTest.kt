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
package org.onebusaway.android.ui

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a periodically refreshing screen (the arrivals board, trip details) polls next: every 60s, or
 * once as soon as a rate limit lifts (#2410, #2416).
 */
class RefreshPollDelayTest {

    @Test
    fun rightAfterAResponseItWaitsThePeriod() {
        assertEquals(60.seconds, nextRefreshPollDelay(Duration.ZERO, earlyRetryAfter = null))
    }

    @Test
    fun theWaitIsMeasuredFromTheResponse() {
        // Resumed 20 s after the last response: only the rest of the period is left.
        assertEquals(40.seconds, nextRefreshPollDelay(20.seconds, earlyRetryAfter = null))
    }

    @Test
    fun anEarlyRetryWaitsOnlyUntilTheRateLimitLifts() {
        assertEquals(7_879.milliseconds, nextRefreshPollDelay(Duration.ZERO, earlyRetryAfter = 7_879.milliseconds))
    }

    @Test
    fun anEarlyRetrySurvivesAPauseAndResume() {
        // Switched away for 2 s after the refused refresh: still retries when the limit lifts, not 58 s later.
        assertEquals(5_879.milliseconds, nextRefreshPollDelay(2.seconds, earlyRetryAfter = 7_879.milliseconds))
    }

    @Test
    fun anOverdueWaitPollsAtOnce() {
        assertEquals(Duration.ZERO, nextRefreshPollDelay(70.seconds, earlyRetryAfter = null))
        assertEquals(Duration.ZERO, nextRefreshPollDelay(9.seconds, earlyRetryAfter = 7_879.milliseconds))
    }

    @Test
    fun theWaitNeverExceedsThePeriod() {
        // No reading can make a screen wait longer than its period, whatever the inputs.
        assertEquals(60.seconds, nextRefreshPollDelay((-120).seconds, earlyRetryAfter = 8.seconds))
        assertEquals(60.seconds, nextRefreshPollDelay(Duration.ZERO, earlyRetryAfter = 90.seconds))
    }
}
