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
package org.onebusaway.android.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.ScreenOffset

/**
 * Unit tests for [nearestProjected] — the map-stop spotlight's pure target picker. The fake projection
 * treats latitude as screen-x and longitude as screen-y, so geo and screen coordinates coincide and the
 * "nearest to screen center" reasoning is obvious.
 */
class NearestProjectedTest {

    private val identity: (GeoPoint) -> ScreenOffset? =
        { ScreenOffset(it.latitude.toFloat(), it.longitude.toFloat()) }

    @Test
    fun `empty list returns null`() {
        assertNull(nearestProjected(emptyList<GeoPoint>(), { it }, 0f, 0f, identity))
    }

    @Test
    fun `picks the item nearest the center`() {
        val near = GeoPoint(10.0, 10.0)
        val far = GeoPoint(100.0, 100.0)
        val (item, offset) = nearestProjected(listOf(far, near), { it }, 12f, 12f, identity)!!
        assertEquals(near, item)
        assertEquals(ScreenOffset(10f, 10f), offset)
    }

    @Test
    fun `skips items that don't project on screen even if geographically nearer`() {
        val onScreen = GeoPoint(5.0, 5.0)
        val offScreen = GeoPoint(1.0, 1.0) // nearer the center, but projects to null
        val project: (GeoPoint) -> ScreenOffset? = { p ->
            if (p == offScreen) null else ScreenOffset(p.latitude.toFloat(), p.longitude.toFloat())
        }
        val result = nearestProjected(listOf(offScreen, onScreen), { it }, 0f, 0f, project)!!
        assertEquals(onScreen, result.first)
    }

    @Test
    fun `returns null when nothing projects on screen`() {
        val nullProject: (GeoPoint) -> ScreenOffset? = { null }
        assertNull(nearestProjected(listOf(GeoPoint(1.0, 1.0)), { it }, 0f, 0f, nullProject))
    }
}
