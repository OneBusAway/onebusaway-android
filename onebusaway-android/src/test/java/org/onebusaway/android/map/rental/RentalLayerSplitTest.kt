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
import org.junit.Test
import org.onebusaway.android.directions.model.RentalFormFactor

/**
 * Unit tests for the bikes/scooters layer split ([rentalLayersOf]) — how the one rental fetch is
 * divided between the two layers, and which places belong to neither (#2168).
 */
class RentalLayerSplitTest {

    private fun place(vararg factors: RentalFormFactor, kind: RentalKind = RentalKind.VEHICLE) = RentalPlace(id = "x", kind = kind, formFactors = factors.toSet())

    @Test
    fun `bicycle-family form factors are bikes`() {
        assertEquals(setOf(RentalLayer.BIKES), rentalLayersOf(place(RentalFormFactor.BICYCLE)))
        assertEquals(setOf(RentalLayer.BIKES), rentalLayersOf(place(RentalFormFactor.CARGO_BICYCLE)))
    }

    @Test
    fun `every kick-scooter spelling and a moped are scooters`() {
        for (factor in listOf(
            RentalFormFactor.SCOOTER,
            RentalFormFactor.SCOOTER_SEATED,
            RentalFormFactor.SCOOTER_STANDING,
            RentalFormFactor.MOPED
        )) {
            assertEquals(setOf(RentalLayer.SCOOTERS), rentalLayersOf(place(factor)))
        }
    }

    /**
     * The OTP1 path — and any dock whose feed publishes no per-type counts — states no form factor at
     * all, and draws on bikes: the only layer that path has ever had.
     */
    @Test
    fun `a place stating no form factor draws on the bikes layer`() {
        assertEquals(setOf(RentalLayer.BIKES), rentalLayersOf(place(kind = RentalKind.STATION)))
    }

    /** A dock holding both appears whichever layer the rider has on. */
    @Test
    fun `a mixed dock belongs to both layers`() {
        assertEquals(
            setOf(RentalLayer.BIKES, RentalLayer.SCOOTERS),
            rentalLayersOf(place(RentalFormFactor.BICYCLE, RentalFormFactor.SCOOTER, kind = RentalKind.STATION))
        )
    }

    /** A car or an `OTHER` is neither a bike nor a scooter, and the app offers no third toggle. */
    @Test
    fun `a form factor with no layer draws on neither`() {
        assertEquals(emptySet<RentalLayer>(), rentalLayersOf(place(RentalFormFactor.CAR)))
        assertEquals(emptySet<RentalLayer>(), rentalLayersOf(place(RentalFormFactor.OTHER)))
    }
}
