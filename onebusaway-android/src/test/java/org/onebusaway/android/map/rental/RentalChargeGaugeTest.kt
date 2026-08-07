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
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.directions.model.RentalFormFactor

/**
 * Unit tests for [rentalChargeFraction] — the marker's charge ring, including the signed-off
 * range/assumed-max fallback documented in `RentalChargeGauge.kt`.
 *
 * The point of most of these is to pin the *boundaries* of that heuristic: which vehicles it applies
 * to, and — more importantly — which it deliberately declines to guess about.
 */
class RentalChargeGaugeTest {

    private fun vehicle(
        formFactor: RentalFormFactor? = null,
        rangeMeters: Int? = null,
        fuelPercent: Double? = null
    ) = RentalPlace(
        id = "v",
        kind = RentalKind.VEHICLE,
        formFactors = setOfNotNull(formFactor),
        rangeMeters = rangeMeters,
        fuelPercent = fuelPercent
    )

    /** The exact field wins whenever it exists, so a feed publishing it retires the heuristic itself. */
    @Test
    fun `a published percent is used as-is, ignoring range`() {
        val place = vehicle(RentalFormFactor.SCOOTER, rangeMeters = 40_000, fuelPercent = 0.25)
        assertEquals(0.25f, rentalChargeFraction(place)!!, 1e-6f)
    }

    @Test
    fun `a scooter with no percent falls back to range over its assumed max`() {
        assertEquals(0.5f, rentalChargeFraction(vehicle(RentalFormFactor.SCOOTER, 19_000))!!, 1e-6f)
        assertEquals(1f, rentalChargeFraction(vehicle(RentalFormFactor.SCOOTER, 38_000))!!, 1e-6f)
    }

    @Test
    fun `an e-bike uses its own larger assumed max`() {
        assertEquals(0.5f, rentalChargeFraction(vehicle(RentalFormFactor.BICYCLE, 31_000))!!, 1e-6f)
    }

    /**
     * Both denominators are fitted to p99 (38.2 → 38 km, 61.5 → 62 km), not to the observed maximum,
     * so the real fleet *does* contain vehicles past them — 82.5 km was the e-bike outlier that made
     * an 85 km denominator stop the ring filling at all. Those must clamp rather than overflow.
     */
    @Test
    fun `a range beyond the assumed max clamps to a full ring`() {
        assertEquals(1f, rentalChargeFraction(vehicle(RentalFormFactor.BICYCLE, 82_500))!!, 1e-6f)
        assertEquals(1f, rentalChargeFraction(vehicle(RentalFormFactor.SCOOTER, 38_705))!!, 1e-6f)
    }

    /** A charged vehicle should read at the top of the ring — the calibration this fixes. */
    @Test
    fun `a vehicle at its fleet's p99 range reads as essentially full`() {
        assertEquals(0.99f, rentalChargeFraction(vehicle(RentalFormFactor.BICYCLE, 61_500))!!, 0.01f)
        assertEquals(1.00f, rentalChargeFraction(vehicle(RentalFormFactor.SCOOTER, 38_238))!!, 0.01f)
    }

    /**
     * The heuristic covers only the two form factors actually measured. A moped or a car gets no
     * gauge rather than borrowing a scooter's denominator — extending a measured constant by analogy
     * would be a second, unsigned guess.
     */
    @Test
    fun `an unmeasured form factor gets no gauge`() {
        assertNull(rentalChargeFraction(vehicle(RentalFormFactor.MOPED, 20_000)))
        assertNull(rentalChargeFraction(vehicle(RentalFormFactor.CAR, 20_000)))
        assertNull(rentalChargeFraction(vehicle(RentalFormFactor.OTHER, 20_000)))
    }

    @Test
    fun `a vehicle stating no range and no percent gets no gauge`() {
        assertNull(rentalChargeFraction(vehicle(RentalFormFactor.SCOOTER)))
    }

    /** A dock counting several vehicle types has no single battery to show. */
    @Test
    fun `a mixed-type place gets no gauge`() {
        val dock = RentalPlace(
            id = "d",
            kind = RentalKind.STATION,
            formFactors = setOf(RentalFormFactor.BICYCLE, RentalFormFactor.SCOOTER),
            rangeMeters = 20_000
        )
        assertNull(rentalChargeFraction(dock))
    }

    /** Every OTP1 place states no form factor at all, so that whole path draws plain rings. */
    @Test
    fun `a place stating no form factor gets no gauge`() {
        assertNull(rentalChargeFraction(vehicle(formFactor = null, rangeMeters = 20_000)))
    }

    // --- The red / amber / green bands the ring is drawn in ---

    @Test
    fun `the bands run red, amber, green`() {
        assertEquals(RentalChargeBand.LOW, rentalChargeBand(0f))
        assertEquals(RentalChargeBand.LOW, rentalChargeBand(0.149f))
        assertEquals(RentalChargeBand.MEDIUM, rentalChargeBand(0.15f))
        assertEquals(RentalChargeBand.MEDIUM, rentalChargeBand(0.549f))
        assertEquals(RentalChargeBand.HIGH, rentalChargeBand(0.55f))
        assertEquals(RentalChargeBand.HIGH, rentalChargeBand(1f))
    }

    /**
     * The specified bands left 35–55% belonging to none of them; amber covers it, so every fraction
     * lands somewhere. A gauge with a hole in it would draw nothing for the vehicles that fall in it.
     */
    @Test
    fun `the bands are contiguous, with amber covering the unspecified middle`() {
        assertEquals(RentalChargeBand.MEDIUM, rentalChargeBand(0.35f))
        assertEquals(RentalChargeBand.MEDIUM, rentalChargeBand(0.45f))
        var f = 0f
        while (f <= 1f) {
            // Total by construction — the assertion is that this simply cannot throw or return null.
            rentalChargeBand(f)
            f += 0.01f
        }
    }
}
