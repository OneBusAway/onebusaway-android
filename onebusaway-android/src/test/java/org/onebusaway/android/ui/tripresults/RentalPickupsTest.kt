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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.RentalEndpointKind
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

        // The honest fallback: no invented brand, no colour asserting one, just what OTP said.
        val unknown = RentalOperators.of("some_city_bikes")
        assertEquals("some_city_bikes", unknown.displayName)
        assertNull(unknown.brandColor)
    }

    @Test
    fun `the catalog matches network ids exactly, never by prefix`() {
        // `lime_seattle` is Lime; nothing says a network id merely *starting* "lime" is, and a wrong
        // brand on a rider's screen is worse than a plain id.
        assertNull(RentalOperators.known("lime_portland"))
        assertNull(RentalOperators.known("lime"))
        assertNull(RentalOperators.known("limelight_bikes"))
        // ...so it presents as its own id, on no brand's colour.
        assertEquals("lime_portland", RentalOperators.of("lime_portland").displayName)
        assertNull(RentalOperators.of("lime_portland").brandColor)
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
        assertEquals(RentalLink.Deep("lime://vehicle/abc", mayNeedTheirApp = true), pickup.link)
        // The rider without the Lime app installed: the web URI names the same vehicle and a browser
        // always opens it, so it is the fallback rather than the operator's app.
        assertEquals(RentalLink.Deep("https://lime.example/abc", mayNeedTheirApp = false), pickup.fallback)
    }

    @Test
    fun `a deep link with no web one beside it falls back past every other custom scheme`() {
        // The fallback filter's floor: an Android URI the device can't answer must not fall back to
        // another URI it can't answer — including the one the app synthesizes, which sits right behind
        // this one and would fail for exactly the same reason.
        val pickup = rentalPickup(rental(networkId = "lime_seattle", androidUri = "lime://vehicle/abc"))!!
        assertEquals(RentalLink.Deep("lime://vehicle/abc", mayNeedTheirApp = true), pickup.link)
        assertEquals(RentalLink.OperatorApp("com.limebike"), pickup.fallback)
    }

    @Test
    fun `a network publishing only a web uri leaves the rider a link that cannot fail`() {
        // The uncatalogued case, where the primary is all there is: it must be the http(s) one, since
        // a chip whose only action is a scheme nothing answers does nothing at all when tapped.
        val pickup = rentalPickup(rental(networkId = "some_city_bikes", webUri = "https://bikes.example/v/7"))!!
        assertEquals(RentalLink.Deep("https://bikes.example/v/7", mayNeedTheirApp = false), pickup.link)
        assertNull(pickup.fallback)
    }

    @Test
    fun `without rental uris the app builds the operator's own link to the vehicle`() {
        // Every vehicle the live Puget Sound deployment serves is this case: no rentalUris at all. The
        // shape is Lime's own, from the cities where it publishes the field (see RentalOperators) —
        // pinned here so a change to it has to be a deliberate one.
        val pickup = rentalPickup(rental(networkId = "lime_seattle"))!!
        val link = pickup.link as RentalLink.Synthesized
        assertEquals("limebike", link.template.scheme)
        assertEquals("map", link.template.host)
        assertEquals("selected_vehicle_id", link.template.vehicleIdParam)
        assertEquals("generated_at", link.template.timestampParam)
        // The network prefix OTP qualifies the id with is not part of the operator's own id for it.
        assertEquals("abc", link.vehicleId)
        // It can fail for want of the app, so it keeps the fallback the plain app launch would have
        // been — which is also why aiming it costs nothing: a rider without Lime still lands on the
        // Play listing rather than on a marketing page.
        assertEquals(RentalLink.OperatorApp("com.limebike"), pickup.fallback)
    }

    @Test
    fun `an operator that publishes a link keeps it, however good the one we could build`() {
        // Ordering: feed-published first, synthesized second. Both name the vehicle, but only one of
        // them is the operator saying what they meant.
        val pickup = rentalPickup(rental(networkId = "lime_seattle", androidUri = "limebike://map?x=1"))!!
        assertEquals(RentalLink.Deep("limebike://map?x=1", mayNeedTheirApp = true), pickup.link)
    }

    @Test
    fun `a catalogued operator with no sourced link shape opens its app, not a guess`() {
        // Bird's own GBFS publishes no Android URI that takes a vehicle id, so nothing is invented for
        // it: the row opens the app it already knows about (#2158).
        val pickup = rentalPickup(rental(networkId = "bird-seattle-washington"))!!
        assertEquals(RentalLink.OperatorApp("co.bird.android"), pickup.link)
        assertEquals(RentalLink.Web("https://www.bird.co/"), pickup.fallback)
    }

    @Test
    fun `a dock gets no vehicle link, however well the operator is known`() {
        // The id of a station endpoint is a station id; no `selected_vehicle_id` will ever match it, so
        // the row falls through to the app launch rather than naming a vehicle that isn't one.
        val pickup = rentalPickup(
            rental(networkId = "lime_seattle", kind = RentalEndpointKind.STATION, stationName = "Pine St")
        )!!
        assertEquals(RentalLink.OperatorApp("com.limebike"), pickup.link)
    }

    @Test
    fun `an id with nothing under its network prefix builds no link`() {
        // An empty `selected_vehicle_id` would be a link that names no vehicle while claiming to.
        assertEquals(
            RentalLink.OperatorApp("com.limebike"),
            rentalPickup(rental(networkId = "lime_seattle", id = "lime_seattle:"))!!.link
        )
        assertEquals(
            RentalLink.OperatorApp("com.limebike"),
            rentalPickup(rental(networkId = "lime_seattle", id = null))!!.link
        )
        // An unqualified id is the id: nothing to strip, and it is what the operator would know it by.
        assertEquals(
            "solo",
            (rentalPickup(rental(networkId = "lime_seattle", id = "solo"))!!.link as RentalLink.Synthesized).vehicleId
        )
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
        val docked = rentalPickup(
            rental(
                networkId = "some_city_bikes",
                kind = RentalEndpointKind.STATION,
                stationName = "Pine St & 3rd Ave"
            )
        )!!
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
            rentedVehicle = true,
            from = place(rental(networkId = "lime_seattle")),
            to = place(
                rental(networkId = "some_city_bikes", kind = RentalEndpointKind.STATION, stationName = "Dock")
            )
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
            rentedVehicle = true,
            from = TripPlace(name = "Origin"),
            to = place(
                rental(networkId = "some_city_bikes", kind = RentalEndpointKind.STATION, stationName = "Dock")
            )
        )
        assertEquals("Dock", leg.rentalPickup()?.stationName)
    }

    @Test
    fun `a plain walk or an own-bike leg has no rental`() {
        assertNull(TripLeg(mode = TripMode.WALK, from = TripPlace(name = "Origin")).rentalPickup())
        assertNull(TripLeg(mode = TripMode.BICYCLE, from = TripPlace(name = "Home")).rentalPickup())
        // Not even one that ends where a bike happens to be parked: OTP says whether the rider hired
        // one, and on this leg it said no (#2159).
        assertNull(
            TripLeg(
                mode = TripMode.BICYCLE,
                from = TripPlace(name = "Home"),
                to = place(rental(networkId = "lime_seattle"))
            ).rentalPickup()
        )
    }

    /**
     * The deliberate degradation of reading OTP's flag instead of the endpoints (#2159): the leg is a
     * rental — it gets the bikeshare glyph — but everything the row *says* about the vehicle lives on
     * the endpoint places, so with none there is no operator to chip and no unlock to offer.
     */
    @Test
    fun `a rental leg with no rental endpoint draws the bikeshare glyph and no chip`() {
        val leg = TripLeg(
            mode = TripMode.BICYCLE,
            rentedVehicle = true,
            from = TripPlace(name = "Origin"),
            to = TripPlace(name = "Destination")
        )
        assertEquals(StreetMode.BIKESHARE, leg.streetMode())
        assertNull(leg.rentalPickup())
    }

    @Test
    fun `the walk to the bike is not itself a rental row`() {
        // Its `to` *is* the rental vehicle — OTP ends the approach walk there — but the rider is told
        // whose bike it is on the row where they ride it, once, not on both legs that touch it. OTP
        // agrees: on the live deployment the approach walk comes back `rentedBike: false` even though
        // it ends at the hired vehicle (#2159), which is exactly the leg it is.
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
        rangeMeters: Int? = null,
        id: String? = "$networkId:abc",
        kind: RentalEndpointKind = RentalEndpointKind.VEHICLE
    ) = TripVehicleRental(
        id = id,
        kind = kind,
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
