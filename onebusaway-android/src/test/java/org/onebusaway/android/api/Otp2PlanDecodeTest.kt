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

import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.api.adapters.toTripItineraries
import org.onebusaway.android.api.graphql.PlanQuery
import org.onebusaway.android.api.graphql.fragment.PlaceFields
import org.onebusaway.android.api.graphql.type.AbsoluteDirection
import org.onebusaway.android.api.graphql.type.Mode
import org.onebusaway.android.api.graphql.type.RelativeDirection
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripRelativeDirection
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.time.ServerTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the OTP 2.x GraphQL `planConnection` response mapping
 * ([org.onebusaway.android.api.adapters.toTripItineraries], `Otp2PlanAdapters.kt`) onto the *same*
 * app-owned domain model [OtpPlanDecodeTest] covers for OTP1 REST — the GraphQL sibling adapter added
 * by #1780. Builds Apollo-generated `PlanQuery.Data` values directly (no JSON fixture / HTTP layer
 * needed — that's Apollo's own generated response-adapter code, not this app's concern) and asserts
 * against [org.onebusaway.android.directions.model.TripItinerary]/`TripLeg`/….
 */
class Otp2PlanDecodeTest {

    @Test
    fun decodesAndMapsPlan() {
        val walkLeg = PlanQuery.Leg(
            mode = Mode.WALK,
            duration = 120.0,
            distance = 123.4,
            realTime = null,
            start = PlanQuery.Start(scheduledTime = "2026-07-11T10:00:00-07:00", estimated = null),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:02:00-07:00", estimated = null),
            from = from(place(name = "Origin", lat = 47.6, lon = -122.3)),
            to = to(
                place(
                    name = "Stop A",
                    lat = 47.61,
                    lon = -122.31,
                    stop = PlaceFields.Stop(gtfsId = "1_1001", code = "1001"),
                )
            ),
            route = null,
            trip = null,
            legGeometry = PlanQuery.LegGeometry(points = "abc_def", length = 2),
            steps = listOf(
                PlanQuery.Step(
                    relativeDirection = RelativeDirection.LEFT,
                    absoluteDirection = AbsoluteDirection.NORTH,
                    streetName = "Main St",
                    distance = 50.0,
                    exit = null,
                    stayOn = false,
                    lat = 47.6,
                    lon = -122.3,
                ),
            ),
        )
        val busLeg = PlanQuery.Leg(
            mode = Mode.BUS,
            duration = 600.0,
            distance = null,
            realTime = true,
            start = PlanQuery.Start(
                scheduledTime = "2026-07-11T10:02:00-07:00",
                estimated = PlanQuery.Estimated(time = "2026-07-11T10:02:30-07:00", delay = "PT30S"),
            ),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:12:00-07:00", estimated = null),
            from = from(
                place(
                    name = "Stop A",
                    lat = 47.61,
                    lon = -122.31,
                    rentalVehicle = PlaceFields.RentalVehicle(vehicleId = "bs_9", name = "Bike 9"),
                )
            ),
            to = to(place(name = "Stop B", lat = 47.62, lon = -122.32)),
            route = PlanQuery.Route(
                gtfsId = "1_5",
                shortName = "5",
                longName = "Fifth Ave",
                color = "0000FF",
                agency = PlanQuery.Agency(name = "Metro", timezone = "America/Los_Angeles"),
            ),
            trip = PlanQuery.Trip(gtfsId = "1_trip_5", tripHeadsign = "Downtown"),
            legGeometry = null,
            steps = null,
        )
        val node = PlanQuery.Node(
            start = "2026-07-11T10:00:00-07:00",
            end = "2026-07-11T10:12:00-07:00",
            duration = 1500L,
            numberOfTransfers = 1,
            legs = listOf(walkLeg, busLeg),
        )
        val data = PlanQuery.Data(
            planConnection = PlanQuery.PlanConnection(
                searchDateTime = "2026-07-11T10:00:00-07:00",
                routingErrors = emptyList(),
                edges = listOf(PlanQuery.Edge(node = node)),
            ),
        )

        val itineraries = data.toTripItineraries()
        assertEquals(1, itineraries.size)
        val itinerary = itineraries[0]
        assertEquals(1500L, itinerary.duration.inWholeSeconds)
        assertEquals(iso("2026-07-11T10:00:00-07:00"), itinerary.startTime)
        assertEquals(2, itinerary.legs.size)

        val walk = itinerary.legs[0]
        assertEquals(TripMode.WALK, walk.mode)
        assertEquals(123.4, walk.distance, 1e-6)
        assertEquals(120L, walk.duration.inWholeSeconds)
        assertEquals(iso("2026-07-11T10:00:00-07:00"), walk.startTime)
        assertEquals(iso("2026-07-11T10:02:00-07:00"), walk.endTime)
        assertEquals("Origin", walk.from.name)
        assertEquals(47.6, walk.from.lat!!, 1e-6)
        // No stop/rentalVehicle/vehicleParking/vehicleRentalStation on the origin place -> NORMAL.
        assertEquals(TripVertexType.NORMAL, walk.from.vertexType)
        assertEquals("1001", walk.to.stopCode)
        assertEquals(TripVertexType.TRANSIT, walk.to.vertexType)
        val walkGeometry = walk.legGeometry!!
        assertEquals("abc_def", walkGeometry.points)
        assertEquals(2, walkGeometry.length)
        assertEquals(1, walk.steps.size)
        assertEquals(TripRelativeDirection.LEFT, walk.steps[0].relativeDirection)
        assertEquals("Main St", walk.steps[0].streetName)

        val bus = itinerary.legs[1]
        assertEquals(TripMode.BUS, bus.mode)
        assertTrue(bus.realTime)
        assertEquals("5", bus.routeShortName)
        assertEquals("Fifth Ave", bus.routeLongName)
        assertEquals("0000FF", bus.routeColor)
        assertEquals("Metro", bus.agencyName)
        assertEquals("Downtown", bus.headsign)
        assertEquals("1_trip_5", bus.tripId)
        // start.estimated is present (real-time delay) — endTime/startTime prefer estimated.time.
        assertEquals(iso("2026-07-11T10:02:30-07:00"), bus.startTime)
        assertEquals(30.seconds, bus.departureDelay)
        assertEquals(TripVertexType.BIKESHARE, bus.from.vertexType)
        assertEquals("bs_9", bus.from.bikeShareId)
    }

    /** An unrecognized wire `Mode` (Apollo's `UNKNOWN__` sentinel) must degrade to null, not throw. */
    @Test
    fun toleratesUnknownModeDegradesToNull() {
        val data = planDataWithSingleLeg(mode = Mode.UNKNOWN__)
        assertNull(data.toTripItineraries()[0].legs[0].mode)
    }

    /**
     * An itinerary missing a field every well-formed OTP2 node carries (here, `start`) must fail
     * loudly at the adapter boundary rather than silently defaulting — mirrors
     * [OtpPlanDecodeTest.missingRequiredLegFieldThrows] for the OTP1 path.
     */
    @Test
    fun missingRequiredStartFieldThrows() {
        val data = planDataWithSingleLeg(mode = Mode.WALK, itineraryStart = null)
        assertThrows(IllegalStateException::class.java) { data.toTripItineraries() }
    }

    private fun planDataWithSingleLeg(
        mode: Mode,
        itineraryStart: String? = "2026-07-11T10:00:00-07:00",
    ): PlanQuery.Data {
        val leg = PlanQuery.Leg(
            mode = mode,
            duration = 60.0,
            distance = 10.0,
            realTime = false,
            start = PlanQuery.Start(scheduledTime = "2026-07-11T10:00:00-07:00", estimated = null),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:01:00-07:00", estimated = null),
            from = from(place(name = "X", lat = 1.0, lon = 2.0)),
            to = to(place(name = "Y", lat = 3.0, lon = 4.0)),
            route = null,
            trip = null,
            legGeometry = null,
            steps = null,
        )
        val node = PlanQuery.Node(
            start = itineraryStart,
            end = "2026-07-11T10:01:00-07:00",
            duration = 60L,
            numberOfTransfers = 0,
            legs = listOf(leg),
        )
        return PlanQuery.Data(
            planConnection = PlanQuery.PlanConnection(
                searchDateTime = null,
                routingErrors = emptyList(),
                edges = listOf(PlanQuery.Edge(node = node)),
            ),
        )
    }

    private fun iso(value: String): ServerTime = ServerTime(OffsetDateTime.parse(value).toInstant().toEpochMilli())

    /** Builds a [PlaceFields] fixture (the fragment shared by `Leg.from`/`Leg.to` — see Plan.graphql). */
    private fun place(
        name: String?,
        lat: Double,
        lon: Double,
        stop: PlaceFields.Stop? = null,
        rentalVehicle: PlaceFields.RentalVehicle? = null,
        vehicleParking: PlaceFields.VehicleParking? = null,
        vehicleRentalStation: PlaceFields.VehicleRentalStation? = null,
    ): PlaceFields = PlaceFields(name, lat, lon, stop, rentalVehicle, vehicleParking, vehicleRentalStation)

    private fun from(fields: PlaceFields) = PlanQuery.From(__typename = "Place", placeFields = fields)

    private fun to(fields: PlaceFields) = PlanQuery.To(__typename = "Place", placeFields = fields)
}
