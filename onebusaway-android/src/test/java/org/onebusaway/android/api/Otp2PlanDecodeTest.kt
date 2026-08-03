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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.toTripItineraries
import org.onebusaway.android.api.graphql.PlanQuery
import org.onebusaway.android.api.graphql.fragment.AlternativeRouteFields
import org.onebusaway.android.api.graphql.fragment.PlaceFields
import org.onebusaway.android.api.graphql.fragment.RentalNetworkFields
import org.onebusaway.android.api.graphql.fragment.RentalUriFields
import org.onebusaway.android.api.graphql.fragment.RouteFields
import org.onebusaway.android.api.graphql.type.AbsoluteDirection
import org.onebusaway.android.api.graphql.type.AlertSeverityLevelType
import org.onebusaway.android.api.graphql.type.FormFactor
import org.onebusaway.android.api.graphql.type.Mode
import org.onebusaway.android.api.graphql.type.PropulsionType
import org.onebusaway.android.api.graphql.type.RelativeDirection
import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.directions.model.TripAlertSeverity
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripRelativeDirection
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.time.ServerTime

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
            interlineWithPreviousLeg = null,
            start = PlanQuery.Start(scheduledTime = "2026-07-11T10:00:00-07:00", estimated = null),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:02:00-07:00", estimated = null),
            from = from(place(name = "Origin", lat = 47.6, lon = -122.3)),
            to = to(
                place(
                    name = "Stop A",
                    lat = 47.61,
                    lon = -122.31,
                    stop = PlaceFields.Stop(gtfsId = "1_1001", code = "1001")
                )
            ),
            route = null,
            trip = null,
            stopCalls = emptyList(),
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
                    lon = -122.3
                )
            ),
            // A non-transit leg has no alternatives — OTP returns null rather than erroring.
            nextLegs = null,
            // Nor any alerts; OTP returns an empty list rather than null on a leg with nothing to report.
            alerts = emptyList()
        )
        val busLeg = PlanQuery.Leg(
            mode = Mode.BUS,
            duration = 600.0,
            distance = null,
            realTime = true,
            interlineWithPreviousLeg = true,
            start = PlanQuery.Start(
                scheduledTime = "2026-07-11T10:02:00-07:00",
                estimated = PlanQuery.Estimated(time = "2026-07-11T10:02:30-07:00", delay = "PT30S")
            ),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:12:00-07:00", estimated = null),
            from = from(
                place(
                    name = "Stop A",
                    lat = 47.61,
                    lon = -122.31,
                    rentalVehicle = rentalVehicle()
                )
            ),
            to = to(place(name = "Stop B", lat = 47.62, lon = -122.32)),
            route = PlanQuery.Route(__typename = "Route", routeFields = routeFields()),
            trip = PlanQuery.Trip(gtfsId = "1_trip_5", tripHeadsign = "Downtown"),
            stopCalls = listOf(
                stopCall("1_1001", "Stop A", 47.61, -122.31, code = "1001"),
                stopCall("1_1050", "Stop Before B", 47.619, -122.319, code = "1050"),
                stopCall("1_1002", "Stop B", 47.62, -122.32, code = "1002")
            ),
            legGeometry = null,
            steps = null,
            // One alternative departure on another route between the same two stops (#2010).
            nextLegs = listOf(
                PlanQuery.NextLeg(
                    duration = 540.0,
                    route = PlanQuery.Route1(
                        __typename = "Route",
                        alternativeRouteFields = AlternativeRouteFields(
                            gtfsId = "1_7",
                            shortName = "7",
                            longName = "Rainier Beach - Downtown",
                            color = "FF0000",
                            agency = AlternativeRouteFields.Agency(gtfsId = "1_1", name = "Metro")
                        )
                    ),
                    trip = PlanQuery.Trip1(tripHeadsign = "Downtown via 7th"),
                    from = PlanQuery.From1(stop = PlanQuery.Stop(gtfsId = "1_1001")),
                    to = PlanQuery.To1(stop = PlanQuery.Stop1(gtfsId = "1_1002"))
                )
            ),
            // Two alerts OTP scoped to this leg (#2143): one it stated a severity for, one it did
            // not, and one whose header/url the feed left blank.
            alerts = listOf(
                PlanQuery.Alert(
                    id = "alert_1",
                    alertHeaderText = "5 is on reroute",
                    alertDescriptionText = "Detoured via 4th Ave. See https://example.org/detour",
                    alertUrl = "https://example.org/detour",
                    alertSeverityLevel = AlertSeverityLevelType.SEVERE
                ),
                PlanQuery.Alert(
                    id = "alert_2",
                    alertHeaderText = "",
                    alertDescriptionText = "Elevator out of service",
                    alertUrl = "",
                    alertSeverityLevel = null
                )
            )
        )
        val node = PlanQuery.Node(
            start = "2026-07-11T10:00:00-07:00",
            end = "2026-07-11T10:12:00-07:00",
            duration = 1500L,
            numberOfTransfers = 1,
            legs = listOf(walkLeg, busLeg)
        )
        val data = PlanQuery.Data(
            planConnection = PlanQuery.PlanConnection(
                searchDateTime = "2026-07-11T10:00:00-07:00",
                routingErrors = emptyList(),
                edges = listOf(PlanQuery.Edge(node = node))
            )
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
        // A null wire `interlineWithPreviousLeg` maps to false (an ordinary leg, no stay-aboard seam).
        assertFalse(walk.interlineWithPreviousLeg)
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
        assertTrue("interlineWithPreviousLeg maps through from the wire", bus.interlineWithPreviousLeg)
        // start.estimated is present (real-time delay) — endTime/startTime prefer estimated.time.
        assertEquals(iso("2026-07-11T10:02:30-07:00"), bus.startTime)
        assertEquals(30.seconds, bus.departureDelay)
        assertEquals(TripVertexType.BIKESHARE, bus.from.vertexType)
        // The whole rental record, not just its id (#2150): who the vehicle belongs to, what it is,
        // and the operator's own link to it — each straight off the field OTP publishes for it. The
        // network is read from `rentalNetwork.networkId`, never parsed off the `network:id` prefix
        // the vehicle id wears.
        val rental = bus.from.rental!!
        assertEquals("lime_seattle:bs_9", rental.id)
        assertEquals("lime_seattle", rental.networkId)
        assertEquals("https://www.li.me/", rental.networkUrl)
        assertEquals("lime://vehicle/bs_9", rental.androidUri)
        assertEquals("https://lime.example/vehicle/bs_9", rental.webUri)
        assertEquals(RentalFormFactor.BICYCLE, rental.formFactor)
        assertEquals(RentalPropulsion.ELECTRIC_ASSIST, rental.propulsion)
        assertEquals(43356, rental.rangeMeters)
        // A free-floating vehicle has no dock, and that absence is how the domain says so.
        assertNull(rental.stationName)
        // OTP2's `stopCalls` include the boarding and alighting calls (1_1001 / 1_1002 here), but
        // `TripLeg.stop` carries only the stops *in between* — what the drawer counts as "N stops
        // in between" and what the reminder plan walks — so the two endpoints are dropped.
        assertEquals(listOf("1_1050"), bus.stop?.map { it.stopId })
        assertEquals(TripVertexType.TRANSIT, bus.stop?.single()?.vertexType)
        // The rider-facing stop number the drawer appends to the name, as OTP1 legs already carry.
        assertEquals("1050", bus.stop?.single()?.stopCode)

        // The leg's alternative departures (`nextLegs`) come across unjudged — route identity, the
        // ride time the interchangeability rule compares, and both stop ids to check it against.
        assertEquals(1, bus.alternatives.size)
        val alternative = bus.alternatives[0]
        assertEquals("1_7", alternative.routeId)
        assertEquals("7", alternative.routeShortName)
        assertEquals("Rainier Beach - Downtown", alternative.routeLongName)
        assertEquals("FF0000", alternative.routeColor)
        assertEquals("1_1", alternative.agencyId)
        assertEquals("Metro", alternative.agencyName)
        assertEquals("Downtown via 7th", alternative.headsign)
        assertEquals(540.seconds, alternative.duration)
        assertEquals("1_1001", alternative.fromStopId)
        assertEquals("1_1002", alternative.toStopId)
        // A walk leg's `nextLegs` is null on the wire, not an error — it maps to no alternatives.
        assertTrue(walk.alternatives.isEmpty())

        // The leg's service alerts (#2143) come across as OTP scoped them, severity included.
        assertEquals(2, bus.alerts.size)
        val severe = bus.alerts[0]
        assertEquals("alert_1", severe.id)
        assertEquals("5 is on reroute", severe.header)
        assertEquals("Detoured via 4th Ave. See https://example.org/detour", severe.description)
        assertEquals("https://example.org/detour", severe.url)
        assertEquals(TripAlertSeverity.SEVERE, severe.severity)
        // Blank header/url normalize to null (one representation of "the feed didn't publish this"),
        // and an unstated severity lands on the wire vocabulary's own UNKNOWN_SEVERITY token.
        val unstated = bus.alerts[1]
        assertNull(unstated.header)
        assertNull(unstated.url)
        assertEquals("Elevator out of service", unstated.description)
        assertEquals(TripAlertSeverity.UNKNOWN_SEVERITY, unstated.severity)
        assertTrue(walk.alerts.isEmpty())
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

    /**
     * `CallStopLocation` is a union — a flex leg's call can be a `Location`/`LocationGroup` this
     * query cannot represent as a transit stop. Dropping just that call would leave a hole in the
     * middle of the stop list, which shifts the reminder plan's penultimate stop and would alert
     * the rider at the wrong place, so the whole list collapses to null instead.
     */
    @Test
    fun unrepresentableStopCallCollapsesTheWholeStopList() {
        val data = planDataWithSingleLeg(
            mode = Mode.BUS,
            stopCalls = listOf(
                stopCall("1_1001", "Stop A", 47.61, -122.31),
                PlanQuery.StopCall(PlanQuery.StopLocation(__typename = "LocationGroup", onStop = null)),
                stopCall("1_1050", "Stop Before B", 47.619, -122.319),
                stopCall("1_1002", "Stop B", 47.62, -122.32)
            )
        )
        assertNull(data.toTripItineraries()[0].legs[0].stop)
    }

    /**
     * A docked pickup — OTP's other rental shape. Both shapes land on one domain type, and the
     * station's name is what survives of the difference: it is the dock the row sends the rider to.
     */
    @Test
    fun mapsARentalStationPickup() {
        val data = planDataWithSingleLeg(
            mode = Mode.BICYCLE,
            fromPlace = place(
                name = "Pine St & 3rd Ave",
                lat = 47.61,
                lon = -122.33,
                vehicleRentalStation = PlaceFields.VehicleRentalStation(
                    stationId = "seattle_bikes:42",
                    name = "Pine St & 3rd Ave",
                    rentalNetwork = PlaceFields.RentalNetwork1(
                        __typename = "VehicleRentalNetwork",
                        rentalNetworkFields = network("seattle_bikes")
                    ),
                    rentalUris = null
                )
            )
        )
        val rental = data.toTripItineraries()[0].legs[0].from.rental!!
        assertEquals(TripVertexType.BIKESHARE, data.toTripItineraries()[0].legs[0].from.vertexType)
        assertEquals("seattle_bikes:42", rental.id)
        assertEquals("seattle_bikes", rental.networkId)
        assertEquals("Pine St & 3rd Ave", rental.stationName)
        // A dock publishes no vehicle type — it holds whatever the operator left in it.
        assertNull(rental.formFactor)
        assertNull(rental.androidUri)
    }

    private fun planDataWithSingleLeg(
        mode: Mode,
        itineraryStart: String? = "2026-07-11T10:00:00-07:00",
        stopCalls: List<PlanQuery.StopCall> = emptyList(),
        fromPlace: PlaceFields = place(name = "X", lat = 1.0, lon = 2.0)
    ): PlanQuery.Data {
        val leg = PlanQuery.Leg(
            mode = mode,
            duration = 60.0,
            distance = 10.0,
            realTime = false,
            interlineWithPreviousLeg = null,
            start = PlanQuery.Start(scheduledTime = "2026-07-11T10:00:00-07:00", estimated = null),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:01:00-07:00", estimated = null),
            from = from(fromPlace),
            to = to(place(name = "Y", lat = 3.0, lon = 4.0)),
            route = null,
            trip = null,
            stopCalls = stopCalls,
            legGeometry = null,
            steps = null,
            nextLegs = null,
            alerts = emptyList()
        )
        val node = PlanQuery.Node(
            start = itineraryStart,
            end = "2026-07-11T10:01:00-07:00",
            duration = 60L,
            numberOfTransfers = 0,
            legs = listOf(leg)
        )
        return PlanQuery.Data(
            planConnection = PlanQuery.PlanConnection(
                searchDateTime = null,
                routingErrors = emptyList(),
                edges = listOf(PlanQuery.Edge(node = node))
            )
        )
    }

    private fun stopCall(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        code: String? = null
    ) = PlanQuery.StopCall(
        PlanQuery.StopLocation(
            __typename = "Stop",
            onStop = PlanQuery.OnStop(id, name, lat, lon, code)
        )
    )

    private fun iso(value: String): ServerTime = ServerTime(OffsetDateTime.parse(value).toInstant().toEpochMilli())

    /** Builds a [PlaceFields] fixture (the fragment shared by `Leg.from`/`Leg.to` — see Plan.graphql). */
    private fun place(
        name: String?,
        lat: Double,
        lon: Double,
        stop: PlaceFields.Stop? = null,
        rentalVehicle: PlaceFields.RentalVehicle? = null,
        vehicleParking: PlaceFields.VehicleParking? = null,
        vehicleRentalStation: PlaceFields.VehicleRentalStation? = null
    ): PlaceFields = PlaceFields(name, lat, lon, stop, rentalVehicle, vehicleParking, vehicleRentalStation)

    /**
     * A free-floating rental vehicle, shaped like the ones the live Puget Sound OTP2 deployment
     * returns (a `network:uuid` id, a network id of `lime_seattle`, an electric-assist bicycle) —
     * except for the rental URIs and network URL, which that deployment publishes on nothing at all
     * and which are here precisely because the app has to carry them the day an operator does.
     */
    private fun rentalVehicle(): PlaceFields.RentalVehicle = PlaceFields.RentalVehicle(
        vehicleId = "lime_seattle:bs_9",
        rentalNetwork = PlaceFields.RentalNetwork(
            __typename = "VehicleRentalNetwork",
            rentalNetworkFields = network("lime_seattle", url = "https://www.li.me/")
        ),
        rentalUris = PlaceFields.RentalUris(
            __typename = "VehicleRentalUris",
            rentalUriFields = RentalUriFields(
                android = "lime://vehicle/bs_9",
                web = "https://lime.example/vehicle/bs_9"
            )
        ),
        vehicleType = PlaceFields.VehicleType(
            formFactor = FormFactor.BICYCLE,
            propulsionType = PropulsionType.ELECTRIC_ASSIST
        ),
        fuel = PlaceFields.Fuel(range = 43356)
    )

    /** The network fragment both rental shapes carry — see the RentalNetworkFields fragment in Plan.graphql. */
    private fun network(networkId: String, url: String? = null) = RentalNetworkFields(networkId = networkId, url = url)

    /** Builds a [RouteFields] fixture (the fragment shared by the planned leg's route and each
     *  alternative leg's — see Plan.graphql). */
    private fun routeFields(
        gtfsId: String = "1_5",
        shortName: String? = "5",
        longName: String? = "Fifth Ave",
        color: String? = "0000FF"
    ): RouteFields = RouteFields(
        gtfsId = gtfsId,
        shortName = shortName,
        longName = longName,
        color = color,
        agency = RouteFields.Agency(gtfsId = "1_1", name = "Metro", timezone = "America/Los_Angeles")
    )

    private fun from(fields: PlaceFields) = PlanQuery.From(__typename = "Place", placeFields = fields)

    private fun to(fields: PlaceFields) = PlanQuery.To(__typename = "Place", placeFields = fields)
}
