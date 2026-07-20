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

import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.api.adapters.toTripItinerary
import org.onebusaway.android.api.contract.OtpPlanParser
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
 * Covers the OTP `/plan` decode ([OtpPlanParser]) and the mapping onto the app-owned trip-plan domain
 * model ([org.onebusaway.android.directions.model.TripItinerary]/`TripLeg`/…) via
 * [org.onebusaway.android.api.adapters.toTripItinerary] — the kotlinx.serialization replacement for the
 * retired Jackson `JacksonConfig` path, now targeting our own domain model instead of the vendored OTP1
 * POJOs.
 *
 * The pinned case is the epoch-millis timestamps: OTP emits `startTime`/`endTime` as JSON *numbers*, but
 * some servers quote them as strings; [org.onebusaway.android.api.contract.OtpResponseDto] must preserve
 * both forms so they decode to the same [ServerTime].
 */
class OtpPlanDecodeTest {

    @Test
    fun decodesAndMapsPlan() {
        // startTime is a bare number (as OTP emits it); the bus leg's endTime is a quoted string —
        // both must decode to the same Instant. `debugOutput` is an unmodeled key that must be
        // ignored, not rejected. duration/departureDelay are seconds on the wire.
        val body = """
            {
              "debugOutput": { "totalTime": 42 },
              "plan": {
                "date": 1699999990000,
                "from": { "name": "Origin" },
                "itineraries": [
                  {
                    "duration": 1500,
                    "startTime": 1699999999000,
                    "legs": [
                      {
                        "mode": "WALK",
                        "distance": 123.4,
                        "startTime": 1699999999000,
                        "endTime": 1700000100000,
                        "from": { "name": "Origin", "lat": 47.6, "lon": -122.3 },
                        "to": { "name": "Stop A", "stopCode": "1001", "vertexType": "TRANSIT" },
                        "legGeometry": { "points": "abc_def", "length": 2 },
                        "steps": [
                          { "distance": 50.0, "relativeDirection": "LEFT",
                            "absoluteDirection": "NORTH", "streetName": "Main St",
                            "lon": -122.3, "lat": 47.6 }
                        ]
                      },
                      {
                        "mode": "BUS",
                        "realTime": true,
                        "agencyTimeZoneOffset": -14400,
                        "routeShortName": "5",
                        "routeColor": "0000FF",
                        "startTime": 1700000100000,
                        "endTime": "1700000700000",
                        "departureDelay": 30,
                        "tripId": "trip_5",
                        "from": { "name": "Stop A", "vertexType": "BIKESHARE", "bikeShareId": "bs_9" },
                        "to": { "name": "Stop B" },
                        "intermediateStops": [ { "name": "Stop Mid", "stopCode": "1002" } ]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val response = OtpPlanParser.parse(body)
        assertNull(response.error)

        val itineraries = response.plan!!.itineraries.map { it.toTripItinerary() }
        assertEquals(1, itineraries.size)
        val itinerary = itineraries[0]
        assertEquals(1500L, itinerary.duration.inWholeSeconds)
        assertEquals(ServerTime(1699999999000), itinerary.startTime)
        assertEquals(2, itinerary.legs.size)

        val walk = itinerary.legs[0]
        assertEquals(TripMode.WALK, walk.mode)
        assertEquals(123.4, walk.distance, 1e-6)
        assertEquals(ServerTime(1699999999000), walk.startTime)
        assertEquals(ServerTime(1700000100000), walk.endTime)
        val walkFrom = walk.from
        val walkTo = walk.to
        val walkGeometry = walk.legGeometry!!
        assertEquals("Origin", walkFrom.name)
        assertEquals(47.6, walkFrom.lat!!, 1e-6)
        assertEquals("1001", walkTo.stopCode)
        assertEquals(TripVertexType.TRANSIT, walkTo.vertexType)
        // legGeometry + walk steps flow through the mapped fields
        assertEquals("abc_def", walkGeometry.points)
        assertEquals(2, walkGeometry.length)
        assertEquals(1, walk.steps.size)
        assertEquals(TripRelativeDirection.LEFT, walk.steps[0].relativeDirection)
        assertEquals("Main St", walk.steps[0].streetName)

        val bus = itinerary.legs[1]
        assertEquals(TripMode.BUS, bus.mode)
        assertTrue(bus.realTime)
        assertEquals("5", bus.routeShortName)
        assertEquals("0000FF", bus.routeColor)
        assertEquals(30L, bus.departureDelay.inWholeSeconds)
        assertEquals(30.seconds, bus.departureDelay)
        assertEquals("trip_5", bus.tripId)
        // quoted-string endTime decodes the same as a bare number
        assertEquals(ServerTime(1700000700000), bus.endTime)
        val busFrom = bus.from
        val intermediateStops = bus.intermediateStops!!
        assertEquals(TripVertexType.BIKESHARE, busFrom.vertexType)
        assertEquals("bs_9", busFrom.bikeShareId)
        assertEquals(1, intermediateStops.size)
        assertEquals("Stop Mid", intermediateStops[0].name)
    }

    @Test
    fun mapsErrorResponse() {
        val body = """{ "error": { "id": 404, "msg": "Path not found", "noPath": true } }"""

        val response = OtpPlanParser.parse(body)
        assertNull(response.plan)
        val error = response.error!!
        assertEquals(404, error.id)
        assertEquals("Path not found", error.msg)
    }

    /** An unknown enum string must degrade to null rather than blow up the whole parse. */
    @Test
    fun toleratesUnknownVertexType() {
        val body = """
            { "plan": { "itineraries": [ { "startTime": 1, "legs": [
              { "mode": "WALK", "startTime": 1, "endTime": 2,
                "from": { "name": "X", "vertexType": "WHO_KNOWS" }, "to": { "name": "Y" } }
            ] } ] } }
        """.trimIndent()

        val response = OtpPlanParser.parse(body)
        val leg = response.plan!!.itineraries[0].toTripItinerary().legs[0]
        assertNull(leg.from.vertexType)
    }

    /**
     * A leg missing a field every well-formed OTP leg carries (here, `startTime`) must fail loudly at
     * the adapter boundary rather than silently defaulting — this is the one place that knows the
     * response is malformed, so every downstream consumer can treat these fields as non-null.
     */
    @Test
    fun missingRequiredLegFieldThrows() {
        val body = """
            { "plan": { "itineraries": [ { "startTime": 1, "legs": [
              { "mode": "WALK", "endTime": 2, "from": { "name": "X" }, "to": { "name": "Y" } }
            ] } ] } }
        """.trimIndent()

        val itinerary = OtpPlanParser.parse(body).plan!!.itineraries[0]
        assertThrows(IllegalStateException::class.java) { itinerary.toTripItinerary() }
    }

    /**
     * A malformed body must surface as [IOException] — not the unchecked `SerializationException`
     * `decodeFromString` raises — so it routes through the same network-failure handling both call
     * sites already have (the Java AsyncTask only catches `IOException`; the repository maps it to a
     * user-facing message).
     */
    @Test
    fun malformedBodyThrowsIOException() {
        assertThrows(IOException::class.java) {
            OtpPlanParser.parse("""{ "plan": { "itineraries": [ """)
        }
    }
}
