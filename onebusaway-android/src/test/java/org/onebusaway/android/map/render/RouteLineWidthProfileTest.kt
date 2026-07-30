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

    @Test
    fun `the directions map spends width on three kinds of line, and nothing else`() {
        // Width says what a line *is* — a ride, an on-street leg, or context around them — and selection is
        // said with a case instead (#2082). So these three are the whole vocabulary, and the approach shares
        // the context weight rather than adding a fourth near-duplicate of it.
        assertEquals(15f, ITINERARY_RIDE_WIDTH_PROFILE.thicknessDp, 0f)
        assertEquals(9f, ITINERARY_STREET_WIDTH_PROFILE.thicknessDp, 0f)
        assertEquals(5f, ITINERARY_CONTEXT_WIDTH_PROFILE.thicknessDp, 0f)
        assertEquals(ITINERARY_CONTEXT_WIDTH_PROFILE.thicknessDp, ITINERARY_APPROACH_WIDTH_PROFILE.thicknessDp, 0f)
    }

    @Test
    fun `a case reads as an outline at every zoom, so it stays off the width ramp`() {
        // The case is a fixed dp inset on each side, deliberately not scaled by the line's zoom multiplier: a
        // halo that thinned with its line would stop separating it from the basemap exactly when zoomed out.
        assertEquals(2.5f, ROUTE_LINE_CASE_DP, 0f)
    }
}
