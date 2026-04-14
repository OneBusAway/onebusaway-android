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
package org.onebusaway.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaversineDistanceTest {

    @Test
    fun `same point returns 0`() {
        assertEquals(0.0, LocationUtils.haversineDistance(47.6, -122.3, 47.6, -122.3), 0.0)
    }

    @Test
    fun `is symmetric`() {
        val d1 = LocationUtils.haversineDistance(47.6, -122.3, 48.0, -122.0)
        val d2 = LocationUtils.haversineDistance(48.0, -122.0, 47.6, -122.3)
        assertEquals(d1, d2, 1e-9)
    }

    @Test
    fun `one degree latitude on equator is approximately 111km`() {
        val d = LocationUtils.haversineDistance(0.0, 0.0, 1.0, 0.0)
        // 1 degree latitude ≈ 111.19 km using R = 6371.01 km
        assertEquals(111_195.0, d, 100.0)
    }

    @Test
    fun `known distance Seattle to Portland`() {
        // Seattle (47.6062, -122.3321) to Portland (45.5152, -122.6784)
        // Great-circle ≈ 233 km
        val d = LocationUtils.haversineDistance(47.6062, -122.3321, 45.5152, -122.6784)
        assertEquals(233_000.0, d, 2000.0)
    }

    @Test
    fun `short distance within a city block`() {
        // Two points about 100m apart in downtown Seattle
        val d = LocationUtils.haversineDistance(47.6062, -122.3321, 47.6071, -122.3321)
        assertEquals(100.0, d, 5.0)
    }

    @Test
    fun `antipodal points give half circumference`() {
        // North pole to south pole = pi * R
        val d = LocationUtils.haversineDistance(90.0, 0.0, -90.0, 0.0)
        val expected = Math.PI * LocationUtils.EARTH_RADIUS_METERS
        assertEquals(expected, d, 1.0)
    }

    @Test
    fun `result is always non-negative`() {
        val d = LocationUtils.haversineDistance(-33.8688, 151.2093, 51.5074, -0.1278)
        assertTrue(d > 0)
    }
}
