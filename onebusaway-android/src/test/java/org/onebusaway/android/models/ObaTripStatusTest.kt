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
package org.onebusaway.android.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.testing.testTripStatus

/** [ObaTripStatus.isLocationRealtime] — real-time iff predicted AND some real-time location exists. */
class ObaTripStatusTest {

    @Test
    fun predictedWithLastKnownLocation_isRealtime() {
        assertTrue(testTripStatus(predicted = true, hasLastKnownLocation = true).isLocationRealtime)
    }

    /** The #1621 fix: a predicted, actively-running vehicle with only `position` (feed omits the raw
     *  last-known fix) is real-time — the marker is drawn from `position`, so it mustn't read scheduled. */
    @Test
    fun predictedWithPositionOnly_isRealtime() {
        assertTrue(testTripStatus(predicted = true, hasPosition = true).isLocationRealtime)
    }

    @Test
    fun predictedWithoutAnyLocation_isNotRealtime() {
        assertFalse(testTripStatus(predicted = true).isLocationRealtime)
    }

    @Test
    fun notPredicted_isNotRealtime_evenWithLocation() {
        assertFalse(testTripStatus(predicted = false, hasLastKnownLocation = true).isLocationRealtime)
        assertFalse(testTripStatus(predicted = false, hasPosition = true).isLocationRealtime)
    }
}
