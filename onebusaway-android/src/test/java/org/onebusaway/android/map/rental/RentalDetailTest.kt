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
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.ui.tripresults.RentalLink
import org.onebusaway.android.ui.tripresults.RentalVehicleKind

/** Unit tests for [rentalDetailOf] — what the rental detail window says about a marker (#2168). */
class RentalDetailTest {

    @Test
    fun `a docked station reports its occupancy`() {
        val detail = rentalDetailOf(
            RentalPlace(
                id = "dock_1",
                name = "Pine & 5th",
                kind = RentalKind.STATION,
                vehiclesAvailableCount = 4,
                docksAvailableCount = 6
            )
        )
        assertEquals("Pine & 5th", detail.title)
        assertEquals(4, detail.vehiclesAvailableCount)
        assertEquals(6, detail.docksAvailableCount)
    }

    /**
     * The defect #2168 names: OTP1 dressed a free-floating vehicle as a one-bike station, and the
     * window offered it "bikes available / spaces available". A vehicle now reports no occupancy at
     * all.
     */
    @Test
    fun `a free-floating vehicle reports no occupancy, whatever the wire carried`() {
        val detail = rentalDetailOf(
            RentalPlace(
                id = "veh_1",
                kind = RentalKind.VEHICLE,
                vehiclesAvailableCount = 1,
                docksAvailableCount = 0
            )
        )
        assertNull(detail.vehiclesAvailableCount)
        assertNull(detail.docksAvailableCount)
    }

    @Test
    fun `an electric scooter is named as one`() {
        val detail = rentalDetailOf(
            RentalPlace(
                id = "veh_1",
                kind = RentalKind.VEHICLE,
                formFactors = setOf(RentalFormFactor.SCOOTER),
                propulsion = RentalPropulsion.ELECTRIC
            )
        )
        assertEquals(RentalVehicleKind.ESCOOTER, detail.vehicle)
        assertNull(detail.title) // a vehicle publishes no useful name; its kind is its title
    }

    /** Out of service subsumes the rest — the operator has said all there is to say. */
    @Test
    fun `not-in-service stands alone`() {
        val detail = rentalDetailOf(
            RentalPlace(
                id = "dock_1",
                kind = RentalKind.STATION,
                operative = false,
                allowPickupNow = false,
                allowDropoffNow = false
            )
        )
        assertEquals(listOf(RentalNotice.NOT_IN_SERVICE), detail.notices)
    }

    @Test
    fun `an operative dock reports its current pickup and dropoff restrictions`() {
        val detail = rentalDetailOf(
            RentalPlace(
                id = "dock_1",
                kind = RentalKind.STATION,
                operative = true,
                allowPickupNow = false,
                allowDropoffNow = false
            )
        )
        assertEquals(listOf(RentalNotice.NO_PICKUP_NOW, RentalNotice.NO_DROPOFF_NOW), detail.notices)
    }

    /** A feed that simply doesn't publish these is not asserting the vehicle is unusable. */
    @Test
    fun `an unstated restriction raises no notice`() {
        val detail = rentalDetailOf(RentalPlace(id = "veh_1", kind = RentalKind.VEHICLE))
        assertEquals(emptyList<RentalNotice>(), detail.notices)
    }

    @Test
    fun `the GBFS charge ratio becomes a whole percentage`() {
        assertEquals(62, rentalDetailOf(RentalPlace(id = "a", fuelPercent = 0.62)).fuelPercent)
        assertEquals(100, rentalDetailOf(RentalPlace(id = "a", fuelPercent = 1.0)).fuelPercent)
        assertNull(rentalDetailOf(RentalPlace(id = "a")).fuelPercent)
    }

    /** The operator and its link come from the same builder the directions rows use. */
    @Test
    fun `a known network is named, with its deep link leading`() {
        val detail = rentalDetailOf(
            RentalPlace(
                id = "veh_1",
                kind = RentalKind.VEHICLE,
                networkId = "lime_seattle",
                androidUri = "limebike://vehicle/abc",
                webUri = "https://li.me/ride/abc"
            )
        )
        assertEquals("Lime", detail.pickup?.operator?.displayName)
        assertEquals(RentalLink.Deep("limebike://vehicle/abc", mayNeedTheirApp = true), detail.pickup?.link)
        // The fallback is never one that can fail for want of the operator's app.
        assertEquals(RentalLink.Deep("https://li.me/ride/abc", mayNeedTheirApp = false), detail.pickup?.fallback)
    }

    /** The OTP1 path names no network at all, so there is no operator and nowhere to send the rider. */
    @Test
    fun `a place naming no network has no pickup`() {
        assertNull(rentalDetailOf(RentalPlace(id = "bike_1", kind = RentalKind.STATION)).pickup)
    }
}
