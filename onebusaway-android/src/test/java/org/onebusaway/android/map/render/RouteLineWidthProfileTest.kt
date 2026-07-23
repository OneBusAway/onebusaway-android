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
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLineWidthProfileTest {

    @Test
    fun `route width ramps proportionally from half at zoom 11 to full at 16`() {
        assertEquals(0.5f, routeLineWidthScale(10f), 0f)
        assertEquals(0.5f, routeLineWidthScale(11f), 0f)
        assertEquals(0.75f, routeLineWidthScale(13.5f), 0f)
        assertEquals(1f, routeLineWidthScale(16f), 0f)
        assertEquals(1f, routeLineWidthScale(20f), 0f)
    }

    @Test
    fun `focused route profile owns thickness zoom stop and multiplier schedule`() {
        assertEquals(15f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.thicknessDp, 0f)
        assertEquals(11f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.rampStartZoom, 0f)
        assertEquals(16f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.fullThicknessZoom, 0f)
        assertEquals(0.5f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.multiplierAt(10f), 0f)
        assertEquals(0.75f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.multiplierAt(13.5f), 0f)
        assertEquals(1f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.multiplierAt(16f), 0f)
        assertEquals(7.5f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.thicknessAt(10f), 0f)
        assertEquals(11.25f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.thicknessAt(13.5f), 0f)
        assertEquals(15f, FOCUSED_ROUTE_LINE_WIDTH_PROFILE.thicknessAt(16f), 0f)
    }

    @Test
    fun `adjacent route profile is half the ordinary stroke, sharing its zoom ramp`() {
        assertEquals(5f, ADJACENT_ROUTE_LINE_WIDTH_PROFILE.thicknessDp, 0f)
        assertEquals(ROUTE_LINE_WIDTH_PROFILE.thicknessDp / 2f, ADJACENT_ROUTE_LINE_WIDTH_PROFILE.thicknessDp, 0f)
        assertEquals(11f, ADJACENT_ROUTE_LINE_WIDTH_PROFILE.rampStartZoom, 0f)
        assertEquals(16f, ADJACENT_ROUTE_LINE_WIDTH_PROFILE.fullThicknessZoom, 0f)
        assertEquals(2.5f, ADJACENT_ROUTE_LINE_WIDTH_PROFILE.thicknessAt(10f), 0f)
        assertEquals(5f, ADJACENT_ROUTE_LINE_WIDTH_PROFILE.thicknessAt(16f), 0f)
    }
}
