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
        assertEquals(68, diameter(zoom = 10f, stopFocused = false))
    }

    @Test
    fun `stop focus bitmap size follows the zoom ramp`() {
        assertEquals(20, diameter(zoom = 10f, stopFocused = true))
        assertEquals(44, diameter(zoom = 13.5f, stopFocused = true))
        assertEquals(68, diameter(zoom = 16f, stopFocused = true))
    }

    @Test
    fun `receding slightly shrinks adjacent stops`() {
        // Adjacent (non-selected) stops recede to 80% of the size they'd otherwise have.
        assertEquals(54, diameter(zoom = 16f, stopFocused = true, recedeAdjacent = true))
        assertEquals(16, diameter(zoom = 10f, stopFocused = true, recedeAdjacent = true))
    }

    private fun diameter(
        zoom: Float,
        stopFocused: Boolean,
        recedeAdjacent: Boolean = false
    ): Int = routeStopDiameterPx(zoom, stopFocused, recedeAdjacent, density = 3f)
}
