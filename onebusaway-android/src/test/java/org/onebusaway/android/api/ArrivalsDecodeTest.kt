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

import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the arrivals-and-departures-for-stop wire shape (numeric version; arrival wire names
 * tripHeadsign/routeShortName; tripStatus; frequency; situation {lang,value} text + activeWindows)
 * and the reference resolution the arrivals projection will rely on. Mirrors the real payload
 * (trimmed, with extra fields the model ignores) and the production Json config.
 */
class ArrivalsDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val body = """
        {
          "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
          "data": {
            "entry": {
              "stopId": "1_75403",
              "nearbyStopIds": ["1_75404"],
              "situationIds": ["1_sit"],
              "arrivalsAndDepartures": [
                {
                  "routeId": "1_100", "tripId": "1_t", "stopId": "1_75403",
                  "tripHeadsign": "Downtown", "routeShortName": "8", "routeLongName": "",
                  "stopSequence": 3, "serviceDate": 1782284400000, "vehicleId": "1_6980",
                  "predicted": true,
                  "scheduledArrivalTime": 1782348000000, "predictedArrivalTime": 1782348180000,
                  "scheduledDepartureTime": 1782348000000, "predictedDepartureTime": 1782348180000,
                  "distanceFromStop": 1234.5, "numberOfStopsAway": 4,
                  "historicalOccupancy": "", "occupancyStatus": "MANY_SEATS_AVAILABLE",
                  "situationIds": ["1_sit"],
                  "tripStatus": {
                    "activeTripId": "1_t", "predicted": true, "scheduleDeviation": 180,
                    "serviceDate": 1782284400000, "status": "default", "nextStop": "1_75404",
                    "vehicleId": "1_6980", "lastUpdateTime": 1782347920000,
                    "lastKnownLocation": { "lat": 47.6, "lon": -122.3 }
                  }
                }
              ]
            },
            "references": {
              "stops": [ { "id": "1_75403", "name": "Pine St", "code": "75403", "direction": "E",
                           "lat": 47.6, "lon": -122.3, "locationType": 0, "routeIds": ["1_100"] } ],
              "routes": [ { "id": "1_100", "shortName": "8", "type": 3, "color": "1c3e7d",
                            "textColor": "ffffff", "agencyId": "1" } ],
              "agencies": [ { "id": "1", "name": "Metro" } ],
              "trips": [ { "id": "1_t", "routeId": "1_100", "tripHeadsign": "Downtown" } ],
              "situations": [
                { "id": "1_sit", "severity": "noImpact",
                  "summary": { "lang": "en", "value": "Reroute" },
                  "description": { "lang": "en", "value": "Detour details" },
                  "url": { "lang": "en", "value": "https://x.example" },
                  "activeWindows": [ { "from": 1776212160000, "to": 1789383540000 } ],
                  "allAffects": [ { "routeId": "1_100", "agencyId": "1" } ] }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun decodesArrivalsEnvelope() {
        val envelope: ObaEnvelope<EntryWithReferences<ArrivalsForStop>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val data = envelope.data!!
        val entry = data.entry
        assertEquals("1_75403", entry.stopId)
        assertEquals(listOf("1_75404"), entry.nearbyStopIds)

        val arrival = entry.arrivalsAndDepartures.single()
        assertEquals("8", arrival.routeShortName)
        assertEquals("Downtown", arrival.tripHeadsign)
        assertEquals(3, arrival.stopSequence)
        assertTrue(arrival.predicted)
        assertEquals(1782348180000L, arrival.predictedArrivalTime)
        assertEquals("MANY_SEATS_AVAILABLE", arrival.occupancyStatus)
        assertEquals(listOf("1_sit"), arrival.situationIds)
        assertEquals("default", arrival.tripStatus!!.status)
        assertEquals(47.6, arrival.tripStatus!!.lastKnownLocation!!.lat, 0.0)
    }

    @Test
    fun resolvesReferencesIncludingSituations() {
        val data = json.decodeFromString<ObaEnvelope<EntryWithReferences<ArrivalsForStop>>>(body).data!!
        val refs = data.references

        assertEquals("Pine St", refs.stop("1_75403")?.name)
        assertEquals(listOf("1_100"), refs.stop("1_75403")?.routeIds)
        assertEquals(3, refs.route("1_100")?.type)
        assertEquals("Metro", refs.agency("1")?.name)

        val situation = refs.situation("1_sit")!!
        assertEquals("Reroute", situation.summary.value)
        assertEquals("Detour details", situation.description.value)
        assertEquals("noImpact", situation.severity)
        assertEquals(1776212160000L, situation.activeWindows.single().from)
        assertEquals("1_100", situation.allAffects.single().routeId)
        assertNull(refs.situation("nope"))
    }
}
