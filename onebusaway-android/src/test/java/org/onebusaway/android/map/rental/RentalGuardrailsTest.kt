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
package org.onebusaway.android.map.rental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the two guardrails that make an unlimited `vehicleRentalsByBbox` safe to draw: the
 * pre-request zoom gate and the post-filter density budget (#2168).
 */
class RentalGuardrailsTest {

    /** 20 km of north-south extent, expressed as the latitude span a camera reports. */
    private val gateLatSpan = RENTAL_MAX_VISIBLE_HEIGHT_METERS / visibleHeightMeters(1.0)

    @Test
    fun `a viewport at or inside the 20km gate is fetched for`() {
        assertTrue(isWithinRentalZoomGate(gateLatSpan))
        assertTrue(isWithinRentalZoomGate(gateLatSpan / 4))
        assertTrue(isWithinRentalZoomGate(0.0))
    }

    @Test
    fun `a viewport wider than the gate is refused before any request`() {
        assertFalse(isWithinRentalZoomGate(gateLatSpan * 1.01))
        // A region-wide view — the case the gate exists for.
        assertFalse(isWithinRentalZoomGate(2.0))
    }

    /** The gate is latitude-based precisely so it means the same distance everywhere. */
    @Test
    fun `visible height is a fixed number of metres per degree of latitude`() {
        assertEquals(visibleHeightMeters(1.0) * 2, visibleHeightMeters(2.0), 1e-6)
        assertTrue(visibleHeightMeters(1.0) in 111_000.0..111_500.0)
    }

    @Test
    fun `a set within budget is drawn whole`() {
        val places = List(RENTAL_DENSITY_BUDGET) { RentalPlace(id = "$it") }
        assertEquals(RentalDensity.Draw(places), rentalDensity(places))
    }

    /**
     * Over budget draws **nothing**, not an arbitrary subset: a truncated set silently misreports
     * where the nearest vehicle is, and the rider walks to the wrong one.
     */
    @Test
    fun `an over-budget set draws nothing and reports how many there were`() {
        val places = List(RENTAL_DENSITY_BUDGET + 1) { RentalPlace(id = "$it") }
        assertEquals(RentalDensity.TooMany(RENTAL_DENSITY_BUDGET + 1), rentalDensity(places))
    }
}
