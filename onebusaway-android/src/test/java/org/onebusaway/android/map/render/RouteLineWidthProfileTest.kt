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
import org.junit.Assert.assertTrue
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
    fun `directions widths descend with how committed the geometry is`() {
        // Width says what a line *is*, and selection is said with a case instead (#2082). What "is" means here
        // is how committed the rider is to that geometry, so the ordering is the semantics: the ride they're
        // reading, the on-street legs joining it, the rest of their journey, and last the approach they never
        // travel. Pinning the order rather than only the values keeps a future tuning pass from inverting a
        // pair and quietly saying something else.
        val descending = listOf(
            ITINERARY_RIDE_WIDTH_PROFILE,
            ITINERARY_STREET_WIDTH_PROFILE,
            ITINERARY_CONTEXT_WIDTH_PROFILE,
            ITINERARY_APPROACH_WIDTH_PROFILE
        ).map { it.thicknessDp }

        assertEquals(listOf(15f, 9f, 5f, 3.5f), descending)
        assertEquals(descending.sortedDescending(), descending)
    }

    @Test
    fun `a case reads as an outline at every zoom, so it stays off the width ramp`() {
        // The case is a fixed dp inset on each side, deliberately not scaled by the line's zoom multiplier: a
        // halo that thinned with its line would stop separating it from the basemap exactly when zoomed out.
        assertEquals(2.25f, RouteLineCase.SELECTION.widthDp, 0f)
        // The approach's own weight: the selection colour on a line too thin to carry the selection width.
        assertEquals(1f, RouteLineCase.APPROACH.widthDp, 0f)
        // The lighter edge every directions ride wears; selection stays legible against it by being three
        // times the weight, which is what lets it still mean "selected".
        assertEquals(0.75f, RouteLineCase.OUTLINE.widthDp, 0f)
        // Ascending, so that a heavier case always means a stronger claim on the rider's attention.
        assertEquals(
            RouteLineCase.entries.map { it.widthDp }.sorted(),
            RouteLineCase.entries.map { it.widthDp }
        )
    }

    @Test
    fun `every case is finer than the thinnest line that asks for it`() {
        // The rule that makes a case read as its line's *edge* rather than as a second line under it — and
        // the reason APPROACH exists at all. A selection case adds 4.5dp, which is more width than the
        // approach line (3.5dp) has; taking the full selection weight there drew the thinnest line on the map
        // as a band with a coloured core. So the approach asks for a lighter case in the same colour, and the
        // rule holds everywhere again.
        //
        // Checked per case against what can actually ask for it, rather than against the thinnest profile on
        // the map, since that is what the claim means. A later width tune that drops one of these lines under
        // its own case surfaces here.
        listOf(
            // Every directions ride wears an outline, and a focused on-street leg keeps it too.
            RouteLineCase.OUTLINE to ITINERARY_RIDE_WIDTH_PROFILE,
            RouteLineCase.OUTLINE to ITINERARY_STREET_WIDTH_PROFILE,
            // Only the approach.
            RouteLineCase.APPROACH to ITINERARY_APPROACH_WIDTH_PROFILE,
            // The ridden span, and a focused itinerary leg of either kind.
            RouteLineCase.SELECTION to ITINERARY_RIDE_WIDTH_PROFILE,
            RouteLineCase.SELECTION to ITINERARY_STREET_WIDTH_PROFILE
        ).forEach { (case, profile) ->
            assertTrue(
                "$case adds ${case.extraWidthDp}dp to a line only ${profile.thicknessDp}dp wide",
                case.extraWidthDp < profile.thicknessDp
            )
        }
    }
}
