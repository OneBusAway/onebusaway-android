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
package org.onebusaway.android.ui.tripdetails

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripDetailsEntry

/**
 * Guards the trip-details wire shape (numeric version, status/schedule, and the trip ref's
 * `tripHeadsign`/`tripShortName` names) and the reference resolution the repository relies on.
 */
class TripDetailsDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Trimmed to the modeled fields plus extras (occupancy, position, etc.) that must be ignored.
    private val body = """
        {
          "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
          "data": {
            "entry": {
              "tripId": "1_t",
              "serviceDate": 1782284400000,
              "status": {
                "activeTripId": "1_t", "predicted": true, "scheduleDeviation": -380,
                "serviceDate": 1782284400000, "status": "SCHEDULED", "nextStop": "1_s2",
                "vehicleId": "1_6980", "lastUpdateTime": 1782347926000,
                "position": { "lat": 47.6, "lon": -122.3 }, "occupancyStatus": "MANY_SEATS_AVAILABLE"
              },
              "schedule": {
                "timeZone": "America/Los_Angeles",
                "stopTimes": [
                  { "stopId": "1_s1", "arrivalTime": 60180, "departureTime": 60180 },
                  { "stopId": "1_s2", "arrivalTime": 60480, "departureTime": 60480 }
                ]
              }
            },
            "references": {
              "trips": [
                { "id": "1_t", "routeId": "1_r", "tripHeadsign": "Downtown", "tripShortName": "X" }
              ],
              "routes": [ { "id": "1_r", "shortName": "8", "color": "FDB71A", "agencyId": "1" } ],
              "agencies": [ { "id": "1", "name": "Metro" } ],
              "stops": [
                { "id": "1_s1", "name": "First", "direction": "N", "lat": 47.6, "lon": -122.3 },
                { "id": "1_s2", "name": "Second", "direction": "S", "lat": 47.7, "lon": -122.4 }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun decodesStatusScheduleAndReferences() {
        val envelope: ObaEnvelope<EntryWithReferences<TripDetailsEntry>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val data = envelope.data!!
        val entry = data.entry
        assertEquals("1_t", entry.tripId)

        val status = entry.status!!
        assertTrue(status.predicted)
        assertEquals(-380L, status.scheduleDeviation)
        assertEquals("1_t", status.activeTripId)
        assertEquals("SCHEDULED", status.status)
        assertEquals("1_s2", status.nextStop)

        assertEquals(2, entry.schedule!!.stopTimes.size)
        assertEquals("1_s1", entry.schedule!!.stopTimes[0].stopId)
        assertEquals(60180L, entry.schedule!!.stopTimes[0].arrivalTime)

        // Trip ref uses the wire names tripHeadsign / tripShortName.
        val trip = data.references.trip("1_t")!!
        assertEquals("1_r", trip.routeId)
        assertEquals("Downtown", trip.tripHeadsign)
        assertEquals("X", trip.tripShortName)

        val route = data.references.route("1_r")!!
        assertEquals("FDB71A", route.color)
        assertEquals("Metro", data.references.agency(route.agencyId)?.name)
        assertEquals("Second", data.references.stop("1_s2")?.name)
        assertNull(data.references.trip("nope"))
    }
}
