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
import org.onebusaway.android.api.contract.OtpPlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opentripplanner.api.model.RelativeDirection
import org.opentripplanner.api.model.VertexType

/**
 * Covers the OTP `/plan` decode + the mapping onto the OTP library POJOs (`Response`/`TripPlan`/
 * `Itinerary`/`Leg`/…) that the directions + trip-planner UI read — the kotlinx.serialization
 * replacement for the retired Jackson `JacksonConfig` path.
 *
 * The pinned case is the epoch-millis timestamps: OTP emits `startTime`/`endTime` as JSON *numbers*,
 * but the POJOs store them as `String` and callers do `Long.parseLong(...)`. Jackson used to coerce
 * number→String; [org.onebusaway.android.api.contract.OtpResponseDto] must preserve that so both a
 * bare number and a quoted string decode to the same literal text.
 */
class OtpPlanDecodeTest {

    @Test
    fun decodesAndMapsPlan() {
        // startTime is a bare number (as OTP emits it); the bus leg's endTime is a quoted string —
        // both must survive as numeric-millis text. `debugOutput` is an unmodeled key that must be
        // ignored, not rejected.
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

        val itineraries = response.plan.itinerary
        assertEquals(1, itineraries.size)
        val itinerary = itineraries[0]
        assertEquals(1500L, itinerary.duration)
        // number startTime coerced to its literal millis text
        assertEquals("1699999999000", itinerary.startTime)
        assertEquals(2, itinerary.legs.size)

        val walk = itinerary.legs[0]
        assertEquals("WALK", walk.mode)
        assertEquals(123.4, walk.distance!!, 1e-6)
        assertEquals("1699999999000", walk.startTime)
        assertEquals("1700000100000", walk.endTime)
        assertEquals("Origin", walk.from.name)
        assertEquals(47.6, walk.from.lat!!, 1e-6)
        assertEquals("1001", walk.to.stopCode)
        assertEquals(VertexType.TRANSIT, walk.to.vertexType)
        // legGeometry + walk steps flow through the getter-backed fields
        assertEquals("abc_def", walk.legGeometry.points)
        assertEquals(2, walk.legGeometry.length)
        assertEquals(1, walk.steps.size)
        assertEquals(RelativeDirection.LEFT, walk.steps[0].relativeDirection)
        assertEquals("Main St", walk.steps[0].streetName)

        val bus = itinerary.legs[1]
        assertEquals("BUS", bus.mode)
        assertTrue(bus.realTime)
        assertEquals(-14400, bus.agencyTimeZoneOffset)
        assertEquals("5", bus.routeShortName)
        assertEquals("0000FF", bus.routeColor)
        assertEquals(30, bus.departureDelay)
        assertEquals("trip_5", bus.tripId)
        // quoted-string endTime survives unchanged
        assertEquals("1700000700000", bus.endTime)
        assertEquals(VertexType.BIKESHARE, bus.from.vertexType)
        assertEquals("bs_9", bus.from.bikeShareId)
        assertEquals(1, bus.intermediateStops.size)
        assertEquals("Stop Mid", bus.intermediateStops[0].name)
    }

    @Test
    fun mapsErrorResponse() {
        val body = """{ "error": { "id": 404, "msg": "Path not found", "noPath": true } }"""

        val response = OtpPlanParser.parse(body)
        assertNull(response.plan)
        assertEquals(404, response.error.id)
        assertEquals("Path not found", response.error.msg)
    }

    /** An unknown enum string must degrade to null rather than blow up the whole parse. */
    @Test
    fun toleratesUnknownVertexType() {
        val body = """
            { "plan": { "itineraries": [ { "startTime": 1, "legs": [
              { "mode": "WALK", "from": { "name": "X", "vertexType": "WHO_KNOWS" }, "to": { "name": "Y" } }
            ] } ] } }
        """.trimIndent()

        val response = OtpPlanParser.parse(body)
        val leg = response.plan.itinerary[0].legs[0]
        assertNull(leg.from.vertexType)
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
