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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.ArrivalsForLocationData
import org.onebusaway.android.api.contract.ObaEnvelope

/**
 * Guards the arrivals-and-departures-for-location wire shape (#2107) — and above all the fact that
 * this endpoint answers with **two different shapes**.
 *
 * Both bodies below are trimmed from real captures (Puget Sound / Tampa) against the production Json
 * config. The empty one is the case that would otherwise crash: the server's `emptyResponse()` path
 * returns the raw `StopsWithArrivalsAndDeparturesBean` with no `entry` and no `references`, while the
 * populated path wraps it through `factory.getResponse(...)`. A non-nullable `entry` would make
 * panning the map onto water throw instead of showing an empty list.
 */
class NearbyArrivalsDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val populated = """
        {
          "version": 2, "code": 200, "currentTime": 1786132299765, "text": "OK",
          "data": {
            "entry": {
              "stopIds": ["1_431", "1_431", "1_578"],
              "situationIds": ["1_sit"],
              "limitExceeded": false,
              "nearbyStopIds": [
                { "stopId": "1_700", "distanceFromQuery": 26.843080565472977 }
              ],
              "arrivalsAndDepartures": [
                {
                  "routeId": "1_100479", "tripId": "1_t1", "stopId": "1_431",
                  "tripHeadsign": "Capitol Hill", "routeShortName": "10", "routeLongName": "",
                  "stopSequence": 3, "serviceDate": 1786089600000, "vehicleId": "1_6980",
                  "predicted": true,
                  "scheduledArrivalTime": 1786132501000, "predictedArrivalTime": 1786132162000,
                  "scheduledDepartureTime": 1786132501000, "predictedDepartureTime": 1786132162000,
                  "numberOfStopsAway": 2, "occupancyStatus": "EMPTY",
                  "situationIds": ["1_sit2"]
                },
                {
                  "routeId": "1_100512", "tripId": "1_t2", "stopId": "1_578",
                  "tripHeadsign": "Northgate", "routeShortName": "40", "routeLongName": "",
                  "stopSequence": 7, "serviceDate": 1786089600000,
                  "predicted": false,
                  "scheduledArrivalTime": 1786132900000, "predictedArrivalTime": 0,
                  "scheduledDepartureTime": 1786132900000, "predictedDepartureTime": 0,
                  "situationIds": []
                }
              ]
            },
            "references": {
              "agencies": [ { "id": "1", "name": "Metro Transit" } ],
              "stops": [
                { "id": "1_431", "name": "Pine St & 4th Ave", "code": "431", "direction": "W",
                  "lat": 47.6109, "lon": -122.3376, "locationType": 0, "routeIds": ["1_100479"] },
                { "id": "1_578", "name": "3rd Ave & Pine St", "code": "578", "direction": "SE",
                  "lat": 47.6112, "lon": -122.3382, "locationType": 0, "routeIds": ["1_100512"] }
              ],
              "routes": [
                { "id": "1_100479", "shortName": "10", "agencyId": "1", "type": 3 },
                { "id": "1_100512", "shortName": "40", "agencyId": "1", "type": 3 }
              ],
              "trips": [
                { "id": "1_t1", "routeId": "1_100479", "directionId": "0", "blockId": "1_b1" },
                { "id": "1_t2", "routeId": "1_100512", "directionId": "1", "blockId": "1_b2" }
              ],
              "situations": []
            }
          }
        }
    """.trimIndent()

    /**
     * The server's answer for a box with no stops in it — captured verbatim (modulo currentTime) and
     * identical on all four regions that serve this endpoint.
     */
    private val empty = """
        {
          "code": 200, "currentTime": 1786132839215, "text": "OK", "version": 2,
          "data": {
            "arrivalsAndDepartures": [], "limitExceeded": false, "nearbyStops": [],
            "situations": [], "stops": [], "timeZone": ""
          }
        }
    """.trimIndent()

    @Test
    fun `decodes a populated response`() {
        val envelope = json.decodeFromString<ObaEnvelope<ArrivalsForLocationData>>(populated)
        assertEquals(200, envelope.code)
        assertEquals(1786132299765L, envelope.currentTime)

        val entry = requireNotNull(envelope.data?.entry)
        assertEquals(2, entry.arrivalsAndDepartures.size)
        assertFalse(entry.limitExceeded)
        assertEquals(listOf("1_sit"), entry.situationIds)

        val first = entry.arrivalsAndDepartures.first()
        assertEquals("1_431", first.stopId)
        assertEquals("10", first.routeShortName)
        assertEquals("Capitol Hill", first.tripHeadsign)
        assertEquals(1786132162000L, first.predictedArrivalTime)
    }

    /** Each arrival names its own bay — the field the per-stop entry doesn't need and this one lives on. */
    @Test
    fun `every arrival carries a stop id resolvable in the references`() {
        val data = json.decodeFromString<ObaEnvelope<ArrivalsForLocationData>>(populated).data!!
        val stopIds = data.entry!!.arrivalsAndDepartures.map { it.stopId }
        assertEquals(listOf("1_431", "1_578"), stopIds)
        assertEquals("Pine St & 4th Ave", data.references.stop("1_431")?.name)
        assertEquals("SE", data.references.stop("1_578")?.direction)
    }

    /**
     * directionId lives on the trip references, not the arrival — the same resolution the per-stop
     * path does, and the thing the route-first grouping keys on.
     */
    @Test
    fun `direction id resolves through the trip references`() {
        val data = json.decodeFromString<ObaEnvelope<ArrivalsForLocationData>>(populated).data!!
        val arrivals = data.entry!!.arrivalsAndDepartures
        assertEquals(0, data.references.trip(arrivals[0].tripId)?.directionId?.toIntOrNull())
        assertEquals(1, data.references.trip(arrivals[1].tripId)?.directionId?.toIntOrNull())
    }

    /** stopIds repeats ids, which is why the bays come off the arrivals rather than this list. */
    @Test
    fun `stop ids list is not a distinct set`() {
        val entry = json.decodeFromString<ObaEnvelope<ArrivalsForLocationData>>(populated)
            .data!!.entry!!
        assertEquals(3, entry.stopIds.size)
        assertEquals(2, entry.stopIds.toSet().size)
    }

    /**
     * The whole reason `entry` is nullable: this body carries no `entry` key at all. It must decode to
     * an empty result, not throw.
     */
    @Test
    fun `decodes the empty-box response shape`() {
        val envelope = json.decodeFromString<ObaEnvelope<ArrivalsForLocationData>>(empty)
        assertEquals(200, envelope.code)
        assertNotNull(envelope.data)
        assertNull(envelope.data?.entry)
        assertTrue(envelope.data!!.references.stops.isEmpty())
    }
}
