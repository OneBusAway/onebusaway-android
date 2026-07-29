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
package org.onebusaway.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the test-only [encodePolyline] against the production [PolylineDecoder]. Every shape fixture
 * built for the reminder tests passes through this pair, so a bug here would silently weaken those
 * tests rather than fail them.
 */
class PolylineTestEncodingTest {

    @Test
    fun roundTripsThroughTheProductionDecoder() {
        val points = listOf(
            GeoPoint(38.5, -120.2),
            GeoPoint(40.7, -120.95),
            GeoPoint(43.252, -126.453)
        )

        val decoded = PolylineDecoder.decode(encodePolyline(points), points.size)

        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected.latitude, actual.latitude, 1e-5)
            assertEquals(expected.longitude, actual.longitude, 1e-5)
        }
    }

    @Test
    fun matchesTheAlgorithmsPublishedExample() {
        // The worked example from Google's encoded-polyline documentation.
        val points = listOf(
            GeoPoint(38.5, -120.2),
            GeoPoint(40.7, -120.95),
            GeoPoint(43.252, -126.453)
        )

        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", encodePolyline(points))
    }

    @Test
    fun encodesAnEmptyListAsAnEmptyString() {
        assertEquals("", encodePolyline(emptyList()))
    }

    @Test
    fun roundTripsSmallNegativeDeltas() {
        val points = listOf(GeoPoint(0.0, 0.0), GeoPoint(-0.00001, -0.00002))

        val decoded = PolylineDecoder.decode(encodePolyline(points), points.size)

        assertEquals(-0.00001, decoded[1].latitude, 1e-9)
        assertEquals(-0.00002, decoded[1].longitude, 1e-9)
    }
}
