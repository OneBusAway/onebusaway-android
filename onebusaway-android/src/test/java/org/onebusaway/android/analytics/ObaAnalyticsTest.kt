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
package org.onebusaway.android.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.analytics.ObaAnalytics.Companion.LOCATION_ACCURACY_THRESHOLD
import org.onebusaway.android.analytics.ObaAnalytics.Companion.stopDistanceBucket
import org.onebusaway.android.analytics.ObaAnalytics.ObaStopDistance

/**
 * Unit tests for [ObaAnalytics.stopDistanceBucket], the pure distance-bucket decision behind
 * [ObaAnalytics.reportViewStopEvent]. Takes plain accuracy/distance floats (not `android.location.Location`,
 * which isn't constructible in plain JVM unit tests) so the null/inaccurate/accurate cases are all
 * directly testable here, without Robolectric.
 */
class ObaAnalyticsTest {

    @Test
    fun `reports the farthest bucket when there is no location fix`() {
        assertEquals(
            ObaStopDistance.DISTANCE_8,
            stopDistanceBucket(accuracy = null, distanceMeters = 10f)
        )
    }

    @Test
    fun `reports the farthest bucket when the fix is less accurate than the threshold`() {
        assertEquals(
            ObaStopDistance.DISTANCE_8,
            stopDistanceBucket(accuracy = LOCATION_ACCURACY_THRESHOLD, distanceMeters = 10f)
        )
        assertEquals(
            ObaStopDistance.DISTANCE_8,
            stopDistanceBucket(accuracy = LOCATION_ACCURACY_THRESHOLD + 1f, distanceMeters = 10f)
        )
    }

    @Test
    fun `buckets by distance when the fix is accurate enough`() {
        assertEquals(
            ObaStopDistance.DISTANCE_1,
            stopDistanceBucket(accuracy = LOCATION_ACCURACY_THRESHOLD - 1f, distanceMeters = 10f)
        )
        assertEquals(
            ObaStopDistance.DISTANCE_7,
            stopDistanceBucket(accuracy = 1f, distanceMeters = 2000f)
        )
        assertEquals(
            ObaStopDistance.DISTANCE_8,
            stopDistanceBucket(accuracy = 1f, distanceMeters = 5000f)
        )
    }
}
