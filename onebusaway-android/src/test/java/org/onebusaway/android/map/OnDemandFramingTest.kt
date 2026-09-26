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
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.map.rental.visibleHeightMeters
import org.onebusaway.android.util.GeoPoint

class OnDemandFramingTest {

    private val probe = GeoPoint(45.05, -85.1)

    private fun box(heightDeg: Double, centre: GeoPoint = probe) = GeoPoint(centre.latitude - heightDeg / 2, centre.longitude - heightDeg / 2) to GeoPoint(centre.latitude + heightDeg / 2, centre.longitude + heightDeg / 2)

    @Test
    fun `a normal zone is padded by twenty percent about its own centre`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(0.2, GeoPoint(45.2, -85.0))), probe))
        assertEquals(0.24, ne.latitude - sw.latitude, 1e-9)
        assertEquals(45.2, (sw.latitude + ne.latitude) / 2, 1e-9)
        assertEquals(-85.0, (sw.longitude + ne.longitude) / 2, 1e-9)
    }

    @Test
    fun `a tiny zone is framed at twice the street gate about the probe`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(0.005)), probe))
        assertEquals(ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS, visibleHeightMeters(ne.latitude - sw.latitude), 1.0)
        assertEquals(probe.latitude, (sw.latitude + ne.latitude) / 2, 1e-9)
    }

    @Test
    fun `a huge zone is clamped to nine tenths of the outer window about the probe`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(2.0, GeoPoint(46.0, -85.0))), probe))
        assertEquals(ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS, visibleHeightMeters(ne.latitude - sw.latitude), 1.0)
        assertEquals(probe.longitude, (sw.longitude + ne.longitude) / 2, 1e-9)
    }

    @Test
    fun `the union of several boxes is framed`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(0.2, GeoPoint(45.0, -85.0)), box(0.2, GeoPoint(45.2, -85.2))), probe))
        assertEquals(45.0 - 0.1 - 0.04, sw.latitude, 1e-9)
        assertEquals(45.2 + 0.1 + 0.04, ne.latitude, 1e-9)
    }

    @Test
    fun `no boxes gives nothing to frame`() {
        assertNull(onDemandZoomOutCorners(emptyList(), probe))
    }
}
