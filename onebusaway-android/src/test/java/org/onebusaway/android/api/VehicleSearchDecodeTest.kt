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
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.contract.AgencyVehicleResponse
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.data.coachNumberOf
import org.onebusaway.android.api.data.toVehicleTrip

/**
 * Covers the two hops of a coach-number search: the region sidecar's partial-match array (bare JSON,
 * snake_case, so [AgencyVehicleResponse] carries `@SerialName`s) and the OBA `trip-for-vehicle`
 * envelope that turns the matched id into the route + trip the map drills into. Both bodies mirror
 * live Puget Sound payloads.
 */
class VehicleSearchDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun decodesSidecarVehicleMatches() {
        val body = """
            [
              {"id":"1","name":"Metro Transit","vehicle_id":"1_4531"},
              {"id":"19","name":"Intercity Transit","vehicle_id":"19_453"},
              {"id":"40","name":"Sound Transit"}
            ]
        """.trimIndent()

        val matches = json.decodeFromString<List<AgencyVehicleResponse>>(body)

        assertEquals(3, matches.size)
        assertEquals("1", matches[0].agencyId)
        assertEquals("Metro Transit", matches[0].agencyName)
        assertEquals("1_4531", matches[0].vehicleId)
        assertEquals("19_453", matches[1].vehicleId)
        // The sidecar can name an agency with no live vehicle id; the adapter drops those.
        assertNull(matches[2].vehicleId)
    }

    @Test
    fun coachNumberStripsTheAgencyPrefix() {
        assertEquals("4531", coachNumberOf("1_4531", "1"))
        assertEquals("453", coachNumberOf("19_453", "19"))
        // Only the stated agency's prefix comes off, so an underscore inside the number survives.
        assertEquals("103_897453", coachNumberOf("40_103_897453", "40"))
        // An id that doesn't carry the OBA agency prefix is passed through rather than guessed at.
        assertEquals("4531", coachNumberOf("4531", "1"))
        // A hit that named no agency has no prefix to strip — not a bare "_" to strip either.
        assertEquals("1_4531", coachNumberOf("1_4531", ""))
        assertEquals("_4531", coachNumberOf("_4531", ""))
    }

    @Test
    fun tripForVehicleResolvesTheActiveTripsRouteAndHeadsign() {
        val trip = json.decodeFromString<ObaEnvelope<EntryWithReferences<TripDetailsEntry>>>(TRIP_FOR_VEHICLE)
            .requireData()
            .toVehicleTrip()

        assertEquals("1_800587510", trip?.tripId)
        assertEquals("1_100263", trip?.routeId)
        assertEquals("7", trip?.routeShortName)
        assertEquals("Prentice St Via Rainier Ave S", trip?.headsign)
        // The fixture carries no route color: colorArgb() parses via android.graphics.Color, which
        // isn't available to a JVM test. It's an unmodified pass-through of the shared route parser.
        assertNull(trip?.routeColor)
    }

    @Test
    fun tripForVehiclePrefersTheActiveTripOverTheEntrysOwn() {
        // A block rollover: the entry still names the finished trip, while status names the new one.
        val body = TRIP_FOR_VEHICLE.replace("\"tripId\": \"1_800587510\"", "\"tripId\": \"1_800587509\"")

        val trip = json.decodeFromString<ObaEnvelope<EntryWithReferences<TripDetailsEntry>>>(body)
            .requireData()
            .toVehicleTrip()

        assertEquals("1_800587510", trip?.tripId)
    }

    @Test
    fun tripForVehicleWithoutTheTripInReferencesIsNotARide() {
        // includeTrip omitted (or an inconsistent response): nothing to drill into.
        val body = TRIP_FOR_VEHICLE.replace("\"trips\":", "\"unusedTrips\":")

        val trip = json.decodeFromString<ObaEnvelope<EntryWithReferences<TripDetailsEntry>>>(body)
            .requireData()
            .toVehicleTrip()

        assertNull(trip)
    }

    private companion object {

        val TRIP_FOR_VEHICLE = """
            {
              "code": 200,
              "currentTime": 1786127569099,
              "version": 2,
              "data": {
                "entry": {
                  "serviceDate": 1786086000000,
                  "status": {
                    "activeTripId": "1_800587510",
                    "phase": "in_progress",
                    "predicted": true,
                    "vehicleId": "1_4531"
                  },
                  "tripId": "1_800587510"
                },
                "references": {
                  "agencies": [{"id": "1", "name": "Metro Transit"}],
                  "routes": [
                    {
                      "agencyId": "1", "id": "1_100263",
                      "longName": "", "shortName": "7", "type": 3
                    }
                  ],
                  "stops": [],
                  "situations": [],
                  "trips": [
                    {
                      "blockId": "1_8127845", "directionId": "0", "id": "1_800587510",
                      "routeId": "1_100263", "shapeId": "1_21007015",
                      "tripHeadsign": "Prentice St Via Rainier Ave S"
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }
}
