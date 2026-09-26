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
        val target = requireNotNull(onDemandZoomOutTarget(listOf(box(0.2, GeoPoint(45.2, -85.0))), probe, SQUARE_VIEWPORT))
        assertEquals(0.24, target.latSpan, 1e-9)
        assertNear(GeoPoint(45.2, -85.0), target.center)
    }

    @Test
    fun `a tiny zone is shown at twice the street gate about the probe`() {
        val target = requireNotNull(onDemandZoomOutTarget(listOf(box(0.005)), probe, SQUARE_VIEWPORT))
        assertEquals(ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS, visibleHeightMeters(target.latSpan), 1.0)
        assertEquals(probe, target.center)
    }

    @Test
    fun `a huge zone is clamped to nine tenths of the outer window about the probe`() {
        val target = requireNotNull(onDemandZoomOutTarget(listOf(box(2.0, GeoPoint(46.0, -85.0))), probe, SQUARE_VIEWPORT))
        assertEquals(ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS, visibleHeightMeters(target.latSpan), 1.0)
        assertEquals(probe, target.center)
    }

    @Test
    fun `a zone too wide for a portrait viewport is clamped on the latitude the map would show`() {
        val wideButShort = GeoPoint(44.9, -85.8) to GeoPoint(45.3, -84.4)
        val target = requireNotNull(onDemandZoomOutTarget(listOf(wideButShort), probe, PORTRAIT_VIEWPORT))
        assertEquals(ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS, visibleHeightMeters(target.latSpan), 1.0)
        assertEquals(probe, target.center)
    }

    @Test
    fun `a moderately wide zone is shown by its width in a portrait viewport`() {
        val target = requireNotNull(onDemandZoomOutTarget(listOf(GeoPoint(45.0, -85.1) to GeoPoint(45.05, -84.9)), probe, PORTRAIT_VIEWPORT))
        assertEquals(0.2 * 1.2 / PORTRAIT_VIEWPORT, target.latSpan, 1e-9)
        assertNear(GeoPoint(45.025, -85.0), target.center)
    }

    @Test
    fun `the union of several boxes is centred on the union`() {
        val target = requireNotNull(onDemandZoomOutTarget(listOf(box(0.2, GeoPoint(45.0, -85.0)), box(0.2, GeoPoint(45.2, -85.2))), probe, SQUARE_VIEWPORT))
        assertEquals(0.4 * 1.2, target.latSpan, 1e-9)
        assertEquals(45.1, target.center.latitude, 1e-9)
    }

    @Test
    fun `no boxes gives nothing to show`() {
        assertNull(onDemandZoomOutTarget(emptyList(), probe, SQUARE_VIEWPORT))
    }

    private fun assertNear(expected: GeoPoint, actual: GeoPoint) {
        assertEquals(expected.latitude, actual.latitude, 1e-9)
        assertEquals(expected.longitude, actual.longitude, 1e-9)
    }

    private companion object {
        const val SQUARE_VIEWPORT = 1.0
        const val PORTRAIT_VIEWPORT = 0.5
    }
}
