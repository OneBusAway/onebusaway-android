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

/** The zoom schedule a route label's drawn size follows (#2102). */
class RouteBadgeScaleProfileTest {

    @Test
    fun `an itinerary label grows from half to full between zoom 11 and 16, flat outside`() {
        assertEquals(0.5f, ITINERARY_ROUTE_BADGE_SCALE_PROFILE.scaleAt(8f), 0f)
        assertEquals(0.5f, ITINERARY_ROUTE_BADGE_SCALE_PROFILE.scaleAt(11f), 0f)
        assertEquals(0.75f, ITINERARY_ROUTE_BADGE_SCALE_PROFILE.scaleAt(13.5f), 0f)
        assertEquals(1f, ITINERARY_ROUTE_BADGE_SCALE_PROFILE.scaleAt(16f), 0f)
        assertEquals(1f, ITINERARY_ROUTE_BADGE_SCALE_PROFILE.scaleAt(20f), 0f)
    }

    @Test
    fun `a label rides the same schedule as the line it names`() {
        // The point of the schedule is that it's the map's existing one, not a second set of zoom stops
        // invented for labels — same interval, same magnitude, and crucially the same direction, so a label
        // and the line under it recede together instead of one swelling as the other thins. Asserting equal
        // *outputs* rather than equal parameters is what makes this survive a tuning pass on either side:
        // it fails only when the two actually diverge, which is the thing worth knowing. The quantization
        // below is coarser than the tolerance here, so these anchors land on exact steps.
        for (zoom in listOf(8f, 11f, 13.5f, 16f, 20f)) {
            assertEquals(
                ITINERARY_RIDE_WIDTH_PROFILE.multiplierAt(zoom),
                ITINERARY_ROUTE_BADGE_SCALE_PROFILE.scaleAt(zoom),
                0f
            )
        }
    }

    @Test
    fun `the scale is quantized, so a settle inside the ramp rarely mints a new bitmap`() {
        // A label's scale sizes a rasterized bitmap and keys the icon cache, so it is deliberately discrete
        // where a line's width is continuous. What matters is the count: the whole ramp resolves to a
        // handful of values, not one per camera idle. Sampled far more finely than a user could pinch.
        val scales = generateSequence(DETAIL_RAMP_START_ZOOM) { it + 0.01f }
            .takeWhile { it <= DETAIL_RAMP_END_ZOOM }
            .map(ITINERARY_ROUTE_BADGE_SCALE_PROFILE::scaleAt)
            .toSet()

        assertTrue("500 sampled zooms should collapse to a few scales, got ${scales.size}", scales.size <= 12)
        // And the ends are exact, so a label at either flat end draws at its stated size rather than a
        // rounded neighbour of it.
        assertTrue(ROUTE_DETAIL_DISTANT_SCALE in scales)
        assertTrue(1f in scales)
    }

    @Test
    fun `every other label holds one size at every zoom`() {
        // The default a RouteBadge takes, so adjacency labels (#1827) and the continuation badge (#1691) are
        // untouched by all this — and a renderer's re-stamp on camera settle can skip them entirely.
        for (zoom in 1..21) {
            assertEquals(1f, FIXED_ROUTE_BADGE_SCALE_PROFILE.scaleAt(zoom.toFloat()), 0f)
        }
    }
}
