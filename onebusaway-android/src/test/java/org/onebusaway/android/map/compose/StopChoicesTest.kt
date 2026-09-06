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
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
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
    fun `different displayed points remain ordinary single-stop taps`() {
        val a = marker("a", "1")
        val b = marker("b", "2").copy(point = GeoPoint(point.latitude + 0.000001, point.longitude))
        assertEquals(listOf(a), stopChoicesAt(a, listOf(a, b)))
    }

    @Test
    fun `a tap still resolves when the rendered list has just refreshed`() {
        val a = marker("a", "1")
        assertEquals(listOf(a), stopChoicesAt(a, emptyList()))
    }
}
