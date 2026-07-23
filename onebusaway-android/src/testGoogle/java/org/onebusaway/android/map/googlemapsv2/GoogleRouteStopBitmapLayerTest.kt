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
package org.onebusaway.android.map.googlemapsv2

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleRouteStopBitmapLayerTest {
    @Test
    fun `ordinary route mode keeps the normal fixed bitmap size`() {
        assertEquals(60, diameter(zoom = 10f, stopFocused = false, selected = false))
        assertEquals(108, diameter(zoom = 18f, stopFocused = false, selected = true))
    }

    @Test
    fun `stop focus bitmap size follows the zoom ramp`() {
        assertEquals(18, diameter(zoom = 10f, stopFocused = true, selected = false))
        assertEquals(32, diameter(zoom = 11f, stopFocused = true, selected = true))
        assertEquals(39, diameter(zoom = 13.5f, stopFocused = true, selected = false))
        assertEquals(70, diameter(zoom = 13.5f, stopFocused = true, selected = true))
        assertEquals(60, diameter(zoom = 16f, stopFocused = true, selected = false))
        assertEquals(108, diameter(zoom = 18f, stopFocused = true, selected = true))
    }

    @Test
    fun `receding halves adjacent stops but never the focused stop`() {
        // Adjacent (non-selected) stops recede to half the size they'd otherwise have.
        assertEquals(30, diameter(zoom = 16f, stopFocused = true, selected = false, recedeAdjacent = true))
        assertEquals(9, diameter(zoom = 10f, stopFocused = true, selected = false, recedeAdjacent = true))
        // The focused stop keeps its emphasized size — it's the one being highlighted.
        assertEquals(108, diameter(zoom = 18f, stopFocused = true, selected = true, recedeAdjacent = true))
    }

    private fun diameter(
        zoom: Float,
        stopFocused: Boolean,
        selected: Boolean,
        recedeAdjacent: Boolean = false
    ): Int = routeStopDiameterPx(zoom, stopFocused, selected, recedeAdjacent, density = 3f)
}
