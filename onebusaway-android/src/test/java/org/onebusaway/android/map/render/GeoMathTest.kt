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
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [haversineMeters] — the pure great-circle distance. */
class GeoMathTest {

    @Test
    fun zeroForSamePoint() {
        assertEquals(0.0, haversineMeters(GeoPoint(47.6, -122.3), GeoPoint(47.6, -122.3)), 1e-9)
    }

    @Test
    fun oneDegreeOfLatitude_isAboutOneEleventhOfEarthCircumference() {
        // 1° of latitude ≈ 111.2 km on a 6_371_010 m sphere.
        val d = haversineMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        assertEquals(111_195.0, d, 50.0)
    }
}
