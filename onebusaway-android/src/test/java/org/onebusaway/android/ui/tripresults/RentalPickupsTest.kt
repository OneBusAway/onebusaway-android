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
package org.onebusaway.android.ui.tripresults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripVehicleRental
import org.onebusaway.android.directions.model.TripVertexType

/**
 * What a bikeshare directions row is allowed to say about the vehicle it sends a rider to (#2150):
 * which operator names it, which vehicle word it earns, and — the part with real consequences — which
 * link the "unlock it" tap follows.
 */
class RentalPickupsTest {

    @Test
    fun `a catalogued network is named and coloured, an unknown one wears its id`() {
        val lime = RentalOperators.of("lime_seattle")
        assertEquals("Lime", lime.displayName)
        assertEquals(0xFF00DD00.toInt(), lime.brandColor)
        assertTrue(lime.isKnown)

        // The honest fallback: no invented brand, no colour asserting one, just what OTP said.
        val unknown = RentalOperators.of("some_city_bikes")
        assertEquals("some_city_bikes", unknown.displayName)
        assertNull(unknown.brandColor)
        assertFalse(unknown.isKnown)
    }

    @Test
    fun `the catalog matches network ids exactly, never by prefix`() {
        // `lime_seattle` is Lime; nothing says a network id merely *starting* "lime" is, and a wrong
        // brand on a rider's screen is worse than a plain id.
        assertNull(RentalOperators.known("lime_portland"))
        assertNull(RentalOperators.known("lime"))
        assertNull(RentalOperators.known("limelight_bikes"))
        assertFalse(RentalOperators.of("lime_portland").isKnown)
    }

    @Test
    fun `propulsion decides the vehicle word, form factor decides the vehicle`() {
        assertEquals(RentalVehicleKind.EBIKE, kindOf(RentalFormFactor.BICYCLE, RentalPropulsion.ELECTRIC_ASSIST))
        assertEquals(RentalVehicleKind.BIKE, kindOf(RentalFormFactor.BICYCLE, RentalPropulsion.HUMAN))
        assertEquals(RentalVehicleKind.ESCOOTER, kindOf(RentalFormFactor.SCOOTER, RentalPropulsion.ELECTRIC))
        // GBFS splits kick scooters three ways; the rider is renting a scooter in all of them.
        assertEquals(
            RentalVehicleKind.ESCOOTER,
            kindOf(RentalFormFactor.SCOOTER_STANDING, RentalPropulsion.ELECTRIC)
        )
        assertEquals(RentalVehicleKind.SCOOTER, kindOf(RentalFormFactor.SCOOTER_SEATED, RentalPropulsion.HUMAN))
        assertEquals(
            RentalVehicleKind.ELECTRIC_CARGO_BIKE,
            kindOf(RentalFormFactor.CARGO_BICYCLE, RentalPropulsion.ELECTRIC)
        )
        // A combustion moped is a moped; only ELECTRIC/ELECTRIC_ASSIST earn the "e-" word, and only
        // where the app has one to say.
        assertEquals(RentalVehicleKind.MOPED, kindOf(RentalFormFactor.MOPED, RentalPropulsion.COMBUSTION))
        // OTHER is the feed saying it *isn't* one of these — so the row says nothing about it.
        assertNull(kindOf(RentalFormFactor.OTHER, RentalPropulsion.ELECTRIC))
        assertNull(kindOf(null, null))
    }

    @Test
    fun `an operator deep link wins, and keeps a fallback for a device that cannot follow it`() {
        val pickup = rentalPickup(
            rental(
                networkId = "lime_seattle",
                androidUri = "lime://vehicle/abc",
                webUri = "https://lime.example/abc"
            )
        )!!
        assertEquals(RentalLink.Deep("lime://vehicle/abc"), pickup.link)
        // The rider without the Lime app installed: a second custom-scheme URI would fail the same
        // way, so the fallback is the operator's app (store page if absent), never another deep link.
        assertEquals(RentalLink.OperatorApp("com.limebike"), pickup.fallback)
    }

    @Test
    fun `without rental uris a catalogued operator still has somewhere to send the rider`() {
        // Every vehicle the live Puget Sound deployment serves is this case: no rentalUris at all.
        val pickup = rentalPickup(rental(networkId = "lime_seattle"))!!
        assertEquals(RentalLink.OperatorApp("com.limebike"), pickup.link)
        assertEquals(RentalLink.Web("https://www.li.me/"), pickup.fallback)
    }

    @Test
    fun `an uncatalogued network offers only what its own feed published`() {
        val withUrl = rentalPickup(rental(networkId = "some_city_bikes", networkUrl = "https://bikes.example"))!!
        assertEquals(RentalLink.Web("https://bikes.example"), withUrl.link)
        assertNull(withUrl.fallback)

        // Nothing published and nothing catalogued: no button rather than one going somewhere
        // approximate.
        assertNull(rentalPickup(rental(networkId = "some_city_bikes"))!!.link)
    }

    @Test
    fun `a station pickup names its dock`() {
        val docked = rentalPickup(rental(networkId = "some_city_bikes", stationName = "Pine St & 3rd Ave"))!!
        assertEquals("Pine St & 3rd Ave", docked.stationName)
        // Nothing to walk to on a free-floating vehicle, and the row says nothing rather than
        // inventing a place.
        assertNull(rentalPickup(rental(networkId = "lime_seattle"))!!.stationName)
    }

    @Test
    fun `a rental the wire said nothing about draws no rental block`() {
        // The OTP1 path: an id and nothing else. A chip naming an unknown network, with no link under
        // it and no vehicle beside it, would be worse than the plain bike row the leg already had.
        assertNull(rentalPickup(TripVehicleRental(id = "bs_9")))
        assertNull(rentalPickup(TripVehicleRental(id = "bs_9", networkId = "  ")))
        // Everything the row draws hangs off the operator, so even a named dock isn't enough on its
        // own — and OTP2, which always states the network, never produces one.
        assertNull(rentalPickup(TripVehicleRental(id = "bs_9", stationName = "Dock")))
        assertNull(rentalPickup(null))
    }

    @Test
    fun `the pickup endpoint wins over the drop-off one`() {
        val leg = TripLeg(
            mode = TripMode.BICYCLE,
            from = place(rental(networkId = "lime_seattle")),
            to = place(rental(networkId = "some_city_bikes", stationName = "Dock"))
        )
        // Where the rider *gets* the bike decides whose it is and whether they're hunting for a dock.
        assertEquals("Lime", leg.rentalPickup()?.operator?.displayName)
        assertNull(leg.rentalPickup()?.stationName)
    }

    @Test
    fun `a leg that returns a dockless bike to a station still reports the station`() {
        // OTP puts the station on `to` when the ride ends at a dock and started at a loose vehicle
        // with no rental record of its own; the row then has the dock to name.
        val leg = TripLeg(
            mode = TripMode.BICYCLE,
            from = TripPlace(name = "Origin"),
            to = place(rental(networkId = "some_city_bikes", stationName = "Dock"))
        )
        assertEquals("Dock", leg.rentalPickup()?.stationName)
    }

    @Test
    fun `a plain walk or an own-bike leg has no rental`() {
        assertNull(TripLeg(mode = TripMode.WALK, from = TripPlace(name = "Origin")).rentalPickup())
        assertNull(TripLeg(mode = TripMode.BICYCLE, from = TripPlace(name = "Home")).rentalPickup())
    }

    @Test
    fun `the walk to the bike is not itself a rental row`() {
        // Its `to` *is* the rental vehicle — OTP ends the approach walk there — but the rider is told
        // whose bike it is on the row where they ride it, once, not on both legs that touch it.
        val walk = TripLeg(
            mode = TripMode.WALK,
            from = TripPlace(name = "Origin"),
            to = place(rental(networkId = "lime_seattle"))
        )
        assertNull(walk.rentalPickup())
    }

    @Test
    fun `range and vehicle carry through to the row`() {
        val pickup = rentalPickup(
            rental(
                networkId = "lime_seattle",
                formFactor = RentalFormFactor.BICYCLE,
                propulsion = RentalPropulsion.ELECTRIC_ASSIST,
                rangeMeters = 43356
            )
        )!!
        assertEquals(RentalVehicleKind.EBIKE, pickup.vehicle)
        assertEquals(43356, pickup.rangeMeters)
    }

    private fun kindOf(formFactor: RentalFormFactor?, propulsion: RentalPropulsion?): RentalVehicleKind? = rentalPickup(
        rental(networkId = "some_city_bikes", formFactor = formFactor, propulsion = propulsion)
    )?.vehicle

    private fun rental(
        networkId: String? = null,
        networkUrl: String? = null,
        androidUri: String? = null,
        webUri: String? = null,
        stationName: String? = null,
        formFactor: RentalFormFactor? = null,
        propulsion: RentalPropulsion? = null,
        rangeMeters: Int? = null
    ) = TripVehicleRental(
        id = "$networkId:abc",
        stationName = stationName,
        networkId = networkId,
        networkUrl = networkUrl,
        androidUri = androidUri,
        webUri = webUri,
        formFactor = formFactor,
        propulsion = propulsion,
        rangeMeters = rangeMeters
    )

    private fun place(rental: TripVehicleRental) = TripPlace(vertexType = TripVertexType.BIKESHARE, rental = rental)
}
