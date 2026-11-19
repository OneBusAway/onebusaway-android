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
package org.onebusaway.android.ui.tripdetails

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDetailsPollDelayTest {

    @Test
    fun `waits the full period after a normal refresh`() {
        assertEquals(60.seconds, nextTripDetailsPollDelay(Duration.ZERO, earlyRetryAfter = null))
    }

    @Test
    fun `a first load polls immediately`() {
        assertEquals(Duration.ZERO, nextTripDetailsPollDelay(70.seconds, earlyRetryAfter = null))
    }

    @Test
    fun `waits only until the rate limit lifts after a refused first load`() {
        assertEquals(7_879.milliseconds, nextTripDetailsPollDelay(Duration.ZERO, earlyRetryAfter = 7_879.milliseconds))
    }

    @Test
    fun `a resume counts the time already waited`() {
        assertEquals(5_879.milliseconds, nextTripDetailsPollDelay(2.seconds, earlyRetryAfter = 7_879.milliseconds))
        assertEquals(Duration.ZERO, nextTripDetailsPollDelay(9.seconds, earlyRetryAfter = 7_879.milliseconds))
    }
}
