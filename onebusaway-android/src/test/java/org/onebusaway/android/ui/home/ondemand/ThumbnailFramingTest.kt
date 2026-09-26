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
package org.onebusaway.android.ui.home.ondemand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class ThumbnailFramingTest {

    private val square = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))
    private val probeOutside = GeoPoint(44.9, -85.1)

    @Test
    fun `every vertex and the probe land inside the inset square, centred on the probe`() {
        val frame = thumbnailFrame(square + probeOutside, centre = probeOutside, sizePx = 56f, insetPx = 4f)
        for (point in square + probeOutside) {
            val (x, y) = frame.toPixels(point)
            assertTrue("$point at $x,$y", x in 4f..52f && y in 4f..52f)
        }
        assertEquals(28f to 28f, frame.toPixels(probeOutside))
    }

    @Test
    fun `north is up and east is right`() {
        val frame = thumbnailFrame(square, centre = GeoPoint(45.05, -85.1), sizePx = 100f, insetPx = 0f)
        val (_, northY) = frame.toPixels(GeoPoint(45.1, -85.1))
        val (eastX, _) = frame.toPixels(GeoPoint(45.05, -85.0))
        assertTrue(northY < 50f)
        assertTrue(eastX > 50f)
    }

    @Test
    fun `a single point does not divide by zero`() {
        val frame = thumbnailFrame(listOf(probeOutside), centre = probeOutside, sizePx = 56f, insetPx = 4f)
        assertEquals(28f to 28f, frame.toPixels(probeOutside))
    }

    @Test
    fun `the shapes centre is the bbox centre and null for none`() {
        val centre = requireNotNull(shapesCentre(listOf(ThumbnailShape(listOf(square), 0))))
        assertEquals(45.05, centre.latitude, 1e-9)
        assertEquals(-85.1, centre.longitude, 1e-9)
        assertNull(shapesCentre(emptyList()))
    }
}
