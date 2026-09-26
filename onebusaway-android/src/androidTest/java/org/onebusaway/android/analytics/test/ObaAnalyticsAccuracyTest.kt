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
package org.onebusaway.android.analytics.test

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.analytics.ObaAnalytics

/** Instrumented because [Location] can't be constructed in plain JVM unit tests. */
@RunWith(AndroidJUnit4::class)
class ObaAnalyticsAccuracyTest {

    @Test fun missingLocationHasNoAccuracy() {
        assertNull(ObaAnalytics.usableAccuracy(null))
    }

    @Test fun fixWithoutAccuracyIsTreatedAsMissing() {
        val location = Location("test").apply {
            latitude = 47.6
            longitude = -122.3
        }
        assertNull(ObaAnalytics.usableAccuracy(location))
        assertEquals(
            ObaAnalytics.ObaStopDistance.DISTANCE_8,
            ObaAnalytics.stopDistanceBucket(ObaAnalytics.usableAccuracy(location), distanceMeters = 10f)
        )
    }

    @Test fun fixWithAccuracyIsUsed() {
        val location = Location("test").apply { accuracy = 12f }
        assertEquals(12f, ObaAnalytics.usableAccuracy(location)!!, 0f)
    }
}
