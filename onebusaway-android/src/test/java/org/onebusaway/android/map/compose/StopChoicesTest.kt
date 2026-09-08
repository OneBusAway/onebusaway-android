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
package org.onebusaway.android.map.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.util.GeoPoint

class StopChoicesTest {
    private val point = GeoPoint(47.611137, -122.338951)
    private fun marker(id: String, code: String, name: String = "3rd Ave & Pine St", direction: String = "NW") = StopMarker(
        id,
        point,
        direction,
        3,
        ObaStopElement(id = id, code = code, name = name, direction = direction)
    )

    @Test
    fun `a tap on either overlapping marker offers each original stop and its own routes`() {
        val metro = marker("1_590", "590").copy(routes = listOf(StopRoute("5", null), StopRoute("40", null)))
        val express = marker("3_2479", "4720").copy(routes = listOf(StopRoute("597", null)))
        for (tapped in listOf(metro, express)) {
            val choices = stopChoicesAt(tapped, listOf(express, metro))
            assertEquals(listOf(metro, express), choices)
            assertSame(metro, choices[0])
            assertSame(express, choices[1])
        }
    }

    @Test
    fun `different names and directions still get a choice if their markers coincide`() {
        val a = marker("a", "1")
        val b = marker("b", "2", name = "Other platform", direction = "SE")
        assertEquals(listOf(a, b), stopChoicesAt(a, listOf(a, b)))
    }

    @Test
    fun `without a projection different points retain the native single-stop selection`() {
        val a = marker("a", "1")
        val b = marker("b", "2").copy(point = GeoPoint(point.latitude + 0.000001, point.longitude))
        assertEquals(listOf(a), stopChoicesAt(a, listOf(a, b)))
    }

    @Test
    fun `a tap still resolves when the rendered list has just refreshed`() {
        val a = marker("a", "1")
        assertEquals(listOf(a), stopChoicesAt(a, emptyList()))
    }

    @Test
    fun `a map tap captures separated stops inside its screen touch target`() {
        val a = marker("a", "1")
        val b = marker("b", "2").copy(point = GeoPoint(point.latitude + 0.000003, point.longitude))
        val outside = marker("c", "3").copy(point = GeoPoint(point.latitude + 0.000006, point.longitude))
        val screens = mapOf(a.point to ScreenOffset(92f, 211f), b.point to ScreenOffset(111f, 188f), outside.point to ScreenOffset(125f, 200f))
        val choices = stopChoicesAt(null, listOf(outside, b, a), ScreenOffset(100f, 200f), MapProjector(screens::get), 24f)
        assertEquals(listOf(a, b), choices)
        assertSame(a, choices[0])
        assertSame(b, choices[1])
    }

    @Test
    fun `circular target keeps its cardinal reach but excludes the square corners`() {
        val stop = marker("a", "1")
        val tap = ScreenOffset(100f, 200f)
        val inside = listOf(24f to 0f, -24f to 0f, 0f to 24f, 0f to -24f, 14f to 19f)
        val outside = listOf(18f to 18f, -18f to 18f, 18f to -18f, -18f to -18f, 24.1f to 0f)
        for ((offsets, expected) in listOf(inside to listOf(stop), outside to emptyList())) {
            for ((dx, dy) in offsets) {
                val projector = MapProjector { ScreenOffset(tap.x + dx, tap.y + dy) }
                assertEquals("Offset ($dx, $dy)", expected, stopChoicesAt(null, listOf(stop), tap, projector, 24f))
            }
        }
    }

    @Test
    fun `zooming in can separate the same geographic stops into individual targets`() {
        val a = marker("a", "1")
        val b = marker("b", "2").copy(point = GeoPoint(point.latitude + 0.000003, point.longitude))
        val tap = ScreenOffset(100f, 200f)
        val zoomedOut = MapProjector { if (it == a.point) tap else ScreenOffset(112f, 200f) }
        val zoomedIn = MapProjector { if (it == a.point) tap else ScreenOffset(148f, 200f) }
        assertEquals(listOf(a, b), stopChoicesAt(a, listOf(a, b), tap, zoomedOut, 24f))
        assertEquals(listOf(a), stopChoicesAt(a, listOf(a, b), tap, zoomedIn, 24f))
    }

    @Test
    fun `the target is centered on the finger rather than the SDK's winning marker`() {
        val native = marker("a", "1")
        val underFinger = marker("b", "2").copy(point = GeoPoint(1.0, 1.0))
        val nearNative = marker("c", "3").copy(point = GeoPoint(2.0, 2.0))
        val screens = mapOf(native.point to ScreenOffset(0f, 0f), underFinger.point to ScreenOffset(40f, 0f), nearNative.point to ScreenOffset(-10f, 0f))
        assertEquals(
            listOf(native, underFinger),
            stopChoicesAt(native, listOf(nearNative, underFinger, native), ScreenOffset(30f, 0f), MapProjector(screens::get), 24f)
        )
    }

    @Test
    fun `an empty map tap or an unavailable projection does not invent a selection`() {
        val a = marker("a", "1")
        assertEquals(emptyList<StopMarker>(), stopChoicesAt(null, listOf(a), ScreenOffset(0f, 0f), MapProjector { null }, 24f))
        assertEquals(emptyList<StopMarker>(), stopChoicesAt(null, listOf(a), ScreenOffset(0f, 0f), MapProjector { ScreenOffset(100f, 100f) }, 24f))
    }

    @Test
    fun `a provider layer picks the nearest stop inside the circular target`() {
        val near = marker("a", "1")
        val far = marker("b", "2").copy(point = GeoPoint(1.0, 1.0))
        val outside = marker("c", "3").copy(point = GeoPoint(2.0, 2.0))
        val tap = ScreenOffset(100f, 200f)
        val screens = mapOf(near.point to ScreenOffset(110f, 205f), far.point to ScreenOffset(84f, 190f), outside.point to ScreenOffset(118f, 218f))
        assertSame(near, nearestStopWithin(tap, listOf(outside, far, near), MapProjector(screens::get), 24f))
    }

    @Test
    fun `a provider layer accepts the full 24 dp reach but not the square corners`() {
        val stop = marker("a", "1")
        val tap = ScreenOffset(100f, 200f)
        for ((dx, dy) in listOf(24f to 0f, 0f to -24f, 14f to 19f)) {
            assertSame("Offset ($dx, $dy)", stop, nearestStopWithin(tap, listOf(stop), MapProjector { ScreenOffset(tap.x + dx, tap.y + dy) }, 24f))
        }
        for ((dx, dy) in listOf(18f to 18f, -18f to -18f, 24.1f to 0f)) {
            assertNull("Offset ($dx, $dy)", nearestStopWithin(tap, listOf(stop), MapProjector { ScreenOffset(tap.x + dx, tap.y + dy) }, 24f))
        }
        assertNull(nearestStopWithin(tap, listOf(stop), MapProjector { null }, 24f))
    }
}
