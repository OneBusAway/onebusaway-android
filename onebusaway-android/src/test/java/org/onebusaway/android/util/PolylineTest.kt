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

class PolylineTest {

    @Test
    fun nearestProjection_reportsPointAndDistanceAlongLine() {
        val line = Polyline(
            listOf(
                GeoPoint(47.60, -122.33),
                GeoPoint(47.62, -122.33),
                GeoPoint(47.64, -122.33)
            )
        )

        val projection = requireNotNull(line.nearestProjection(47.63, -122.32))

        assertEquals(47.63, projection.point.latitude, 0.000001)
        assertEquals(-122.33, projection.point.longitude, 0.000001)
        assertEquals(
            haversineDistance(47.60, -122.33, 47.63, -122.33),
            projection.distanceAlong,
            0.1
        )
        assertEquals(
            haversineDistance(47.63, -122.32, 47.63, -122.33),
            projection.distanceToPoint,
            0.1
        )
    }
}
