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
package org.onebusaway.android.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.toRentalPlaces
import org.onebusaway.android.api.graphql.RentalsByBboxQuery
import org.onebusaway.android.api.graphql.fragment.RentalNetworkFields
import org.onebusaway.android.api.graphql.fragment.RentalUriFields
import org.onebusaway.android.api.graphql.type.FormFactor
import org.onebusaway.android.api.graphql.type.PropulsionType
import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.rentalLayersOf

/**
 * Covers the OTP2 `vehicleRentalsByBbox` response mapping (`RentalPlaceAdapters.kt`) onto the
 * app-owned [org.onebusaway.android.map.rental.RentalPlace] the rental map layer reads (#2168).
 *
 * Builds Apollo-generated `RentalsByBboxQuery.Data` values directly — no JSON fixture or HTTP layer,
 * for the reason [Otp2PlanDecodeTest] gives: the wire→generated-type step is Apollo's own generated
 * code, not this app's concern.
 */
class Otp2RentalsDecodeTest {

    @Test
    fun `a free-floating vehicle maps to a VEHICLE with its type, charge and operator`() {
        val places = data(vehicle()).toRentalPlaces()

        assertEquals(1, places.size)
        val vehicle = places.single()
        assertEquals("lime_seattle:abc", vehicle.id)
        assertEquals(RentalKind.VEHICLE, vehicle.kind)
        assertEquals(47.61, vehicle.latitude, 1e-6)
        assertEquals(-122.33, vehicle.longitude, 1e-6)
        assertEquals(setOf(RentalFormFactor.SCOOTER), vehicle.formFactors)
        assertEquals(RentalPropulsion.ELECTRIC, vehicle.propulsion)
        assertEquals(8_000, vehicle.rangeMeters)
        assertEquals(0.62, vehicle.fuelPercent!!, 1e-9)
        assertEquals("lime_seattle", vehicle.networkId)
        assertEquals("limebike://vehicle/abc", vehicle.androidUri)
        assertEquals(true, vehicle.allowPickupNow)
        assertEquals(true, vehicle.operative)
        // A free-floating vehicle is one vehicle, never a dock with one in it.
        assertNull(vehicle.vehiclesAvailableCount)
        assertNull(vehicle.docksAvailableCount)
        assertEquals(setOf(RentalLayer.SCOOTERS), rentalLayersOf(vehicle))
    }

    @Test
    fun `a station maps to a STATION with its occupancy and per-type layers`() {
        val places = data(station()).toRentalPlaces()

        val dock = places.single()
        assertEquals("bike_seattle:dock1", dock.id)
        assertEquals("Pine & 5th", dock.name)
        assertEquals(RentalKind.STATION, dock.kind)
        assertEquals(7, dock.vehiclesAvailableCount)
        assertEquals(5, dock.docksAvailableCount)
        assertEquals(true, dock.allowDropoffNow)
        assertEquals(setOf(RentalFormFactor.BICYCLE), dock.formFactors)
        assertEquals(setOf(RentalLayer.BIKES), rentalLayersOf(dock))
    }

    /**
     * A type the dock is currently *out* of doesn't put it on that layer: a rider filtering the map to
     * scooters is looking for one to ride.
     */
    @Test
    fun `a type with a zero count does not claim the dock for its layer`() {
        val dock = data(
            station(
                byType = listOf(
                    byType(2, FormFactor.BICYCLE),
                    byType(0, FormFactor.SCOOTER)
                )
            )
        ).toRentalPlaces().single()

        assertEquals(setOf(RentalFormFactor.BICYCLE), dock.formFactors)
        assertEquals(setOf(RentalLayer.BIKES), rentalLayersOf(dock))
    }

    /**
     * The GBFS id is what the app keys a place by, so a feed that publishes it blank — or omits it —
     * falls back to Apollo's relay `id` rather than leaving the place unidentifiable. The two ids are
     * different spaces, which is why the deep link is never synthesized from either (#2158).
     */
    @Test
    fun `a vehicle with no usable vehicleId falls back to the relay id`() {
        assertEquals("relay-1", data(vehicle(vehicleId = "")).toRentalPlaces().single().id)
        assertEquals("relay-1", data(vehicle(vehicleId = null)).toRentalPlaces().single().id)
    }

    /** A place with no coordinates cannot be drawn anywhere, so it is dropped rather than placed at 0,0. */
    @Test
    fun `an entity with no position is dropped`() {
        assertEquals(emptyList<Any>(), data(vehicle(lat = null)).toRentalPlaces())
    }

    /**
     * A URI with no scheme is one nothing on the device can open, so it never becomes a link — the
     * same wire→domain rule the directions rows apply (see `absoluteUriOrNull`).
     */
    @Test
    fun `a scheme-less rental URI is dropped rather than offered`() {
        val vehicle = data(vehicle(androidUri = "li.me/ride/abc")).toRentalPlaces().single()
        assertNull(vehicle.androidUri)
    }

    // --- fixtures ---

    private fun data(vararg entities: RentalsByBboxQuery.VehicleRentalsByBbox) = RentalsByBboxQuery.Data(vehicleRentalsByBbox = entities.toList())

    private fun vehicle(
        lat: Double? = 47.61,
        androidUri: String? = "limebike://vehicle/abc",
        vehicleId: String? = "lime_seattle:abc"
    ) = RentalsByBboxQuery.VehicleRentalsByBbox(
        __typename = "RentalVehicle",
        onRentalVehicle = RentalsByBboxQuery.OnRentalVehicle(
            id = "relay-1",
            vehicleId = vehicleId,
            lat = lat,
            lon = -122.33,
            allowPickupNow = true,
            operative = true,
            rentalNetwork = RentalsByBboxQuery.RentalNetwork(
                __typename = "VehicleRentalNetwork",
                rentalNetworkFields = RentalNetworkFields(networkId = "lime_seattle", url = "https://li.me/")
            ),
            rentalUris = RentalsByBboxQuery.RentalUris(
                __typename = "VehicleRentalUris",
                rentalUriFields = RentalUriFields(android = androidUri, web = "https://li.me/ride/abc")
            ),
            vehicleType = RentalsByBboxQuery.VehicleType(
                formFactor = FormFactor.SCOOTER,
                propulsionType = PropulsionType.ELECTRIC
            ),
            fuel = RentalsByBboxQuery.Fuel(range = 8_000, percent = 0.62)
        ),
        onVehicleRentalStation = null
    )

    private fun byType(count: Int, formFactor: FormFactor) = RentalsByBboxQuery.ByType(
        count = count,
        vehicleType = RentalsByBboxQuery.VehicleType1(formFactor = formFactor, propulsionType = null)
    )

    private fun station(
        byType: List<RentalsByBboxQuery.ByType> = listOf(byType(7, FormFactor.BICYCLE))
    ) = RentalsByBboxQuery.VehicleRentalsByBbox(
        __typename = "VehicleRentalStation",
        onRentalVehicle = null,
        onVehicleRentalStation = RentalsByBboxQuery.OnVehicleRentalStation(
            id = "relay-2",
            stationId = "bike_seattle:dock1",
            name = "Pine & 5th",
            lat = 47.6,
            lon = -122.34,
            allowPickupNow = true,
            allowDropoffNow = true,
            operative = true,
            rentalNetwork = RentalsByBboxQuery.RentalNetwork1(
                __typename = "VehicleRentalNetwork",
                rentalNetworkFields = RentalNetworkFields(networkId = "bike_seattle", url = null)
            ),
            rentalUris = null,
            availableVehicles = RentalsByBboxQuery.AvailableVehicles(total = 7, byType = byType),
            availableSpaces = RentalsByBboxQuery.AvailableSpaces(total = 5)
        )
    )
}
