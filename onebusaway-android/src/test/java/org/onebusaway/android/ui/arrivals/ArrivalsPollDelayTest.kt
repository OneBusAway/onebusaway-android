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
package org.onebusaway.android.ui.arrivals

import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the arrivals board polls next: every 60s, or as soon as a rate limit lifts after a failed refresh (#2410). */
class ArrivalsPollDelayTest {

    @Test
    fun aFreshRefreshWaitsThePeriod() {
        assertEquals(60_000L, nextArrivalsPollDelayMs(refreshedFresh = true, rateLimitedFor = null))
    }

    @Test
    fun aFailedRefreshWithNoRateLimitWaitsThePeriod() {
        // Offline, or a server error: nothing says asking sooner would help.
        assertEquals(60_000L, nextArrivalsPollDelayMs(refreshedFresh = false, rateLimitedFor = null))
    }

    @Test
    fun aFailedRefreshUnderARateLimitRetriesWhenItLifts() {
        assertEquals(7_879L, nextArrivalsPollDelayMs(refreshedFresh = false, rateLimitedFor = 7_879.milliseconds))
    }

    @Test
    fun aFreshRefreshIgnoresARateLimitSomeoneElseMet() {
        assertEquals(60_000L, nextArrivalsPollDelayMs(refreshedFresh = true, rateLimitedFor = 7_879.milliseconds))
    }
}
