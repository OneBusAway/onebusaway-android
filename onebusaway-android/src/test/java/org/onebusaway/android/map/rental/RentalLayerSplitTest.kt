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

    // --- rentalMarkerLayer: which single layer a marker wears ---

    /**
     * A mixed dock must wear the kind the rider asked to see. Picking blind from the place's own set
     * drew it as a bike whenever bikes sorted first, even with only Scooters enabled.
     */
    @Test
    fun `a mixed dock wears whichever layer is enabled`() {
        val mixed = place(RentalFormFactor.BICYCLE, RentalFormFactor.SCOOTER, kind = RentalKind.STATION)
        assertEquals(RentalLayer.SCOOTERS, rentalMarkerLayer(mixed, setOf(RentalLayer.SCOOTERS)))
        assertEquals(RentalLayer.BIKES, rentalMarkerLayer(mixed, setOf(RentalLayer.BIKES)))
    }

    /**
     * With both on, a mixed dock settles on bikes — the first [RentalLayer] — whichever order its own
     * form factors arrived in. A dock's set is the feed's `byType` order, so resolving on the place's
     * first element instead would repaint the marker when a poll returned the same types reordered.
     */
    @Test
    fun `a mixed dock settles on bikes when both layers are enabled, whatever order its types arrived in`() {
        val both = setOf(RentalLayer.BIKES, RentalLayer.SCOOTERS)
        val bikeFirst = place(RentalFormFactor.BICYCLE, RentalFormFactor.SCOOTER, kind = RentalKind.STATION)
        val scooterFirst = place(RentalFormFactor.SCOOTER, RentalFormFactor.BICYCLE, kind = RentalKind.STATION)
        assertEquals(RentalLayer.BIKES, rentalMarkerLayer(bikeFirst, both))
        assertEquals(RentalLayer.BIKES, rentalMarkerLayer(scooterFirst, both))
    }

    @Test
    fun `a single-kind place wears its own layer whatever is enabled`() {
        val scooter = place(RentalFormFactor.SCOOTER)
        assertEquals(RentalLayer.SCOOTERS, rentalMarkerLayer(scooter, setOf(RentalLayer.SCOOTERS)))
        // The controller filters this out before it can be drawn; the fallback keeps the function total.
        assertEquals(RentalLayer.SCOOTERS, rentalMarkerLayer(scooter, emptySet()))
    }
}
