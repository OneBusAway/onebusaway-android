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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalsAdjacencyTest {

    @Test
    fun `upcoming trips preserve every served direction per route`() {
        val result = focusedRouteDirections(
            listOf("route" to 0, "route" to 0, "route" to 1, "other" to 1)
        )

        assertEquals(
            linkedMapOf("route" to setOf(0, 1), "other" to setOf(1)),
            result,
        )
    }

    @Test
    fun `missing trip metadata makes that route direction unknown`() {
        val result = focusedRouteDirections(
            listOf("route" to 0, "route" to null, "route" to 1)
        )

        assertEquals(mapOf("route" to emptySet<Int>()), result)
    }

    @Test
    fun `no upcoming trips falls back to every route serving the focused stop`() {
        val result = focusedRouteDirections(
            entries = emptyList(),
            fallbackRouteIds = listOf("route", "other"),
        )

        assertEquals(
            linkedMapOf("route" to emptySet<Int>(), "other" to emptySet()),
            result,
        )
    }
}
