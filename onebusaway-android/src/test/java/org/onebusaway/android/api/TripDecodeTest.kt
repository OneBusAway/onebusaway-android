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

import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripReference

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ports the legacy TripRequestTest onto the modernized `trip` endpoint, asserting the full trip
 * record (parity with the retired ObaTripResponse: directionId/serviceId/shapeId/timeZone, not just
 * id/routeId/headsign) plus resolution of the trip's route from the references.
 */
class TripDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val body = """
        {
          "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
          "data": {
            "entry": {
              "id": "1_18196913", "routeId": "1_100", "tripShortName": "",
              "tripHeadsign": "UNIVERSITY DISTRICT", "directionId": "1",
              "serviceId": "1_WEEK", "shapeId": "1_20025002", "timeZone": "America/Los_Angeles",
              "blockId": "1_7517643"
            },
            "references": {
              "routes": [ { "id": "1_100", "shortName": "44", "agencyId": "1" } ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun decodesFullTripRecord() {
        val envelope: ObaEnvelope<EntryWithReferences<TripReference>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val data = envelope.data!!
        val trip = data.entry
        assertEquals("1_18196913", trip.id)
        assertEquals("1_100", trip.routeId)
        assertEquals("UNIVERSITY DISTRICT", trip.tripHeadsign)
        assertEquals("1", trip.directionId)
        assertEquals("1_WEEK", trip.serviceId)
        assertEquals("1_20025002", trip.shapeId)
        assertEquals("America/Los_Angeles", trip.timeZone)
        assertEquals("1_7517643", trip.blockId)
        // The trip's route resolves from the references pool.
        assertEquals("44", data.references.route(trip.routeId)?.shortName)
    }
}
