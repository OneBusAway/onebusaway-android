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
import org.onebusaway.android.api.contract.StopReference

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Ports the legacy StopRequestTest onto the modernized `stop` endpoint: decodes a real Puget Sound
 * stop envelope (numeric version) and asserts the stop entry fields and that every routeId resolves
 * to a route in the references — the fixture-based, network-free replacement for the live
 * instrumented test (whose hardcoded route ids had since gone stale on the server).
 */
class StopDetailsDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Captured from api.pugetsound.onebusaway.org/api/where/stop/1_29261.json (trimmed; extra
    // entry fields parent/staticRouteIds/wheelchairBoarding and ref kinds are ignored by the model).
    private val body = """
        {
          "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
          "data": {
            "entry": {
              "id": "1_29261", "code": "29261", "name": "Pine St & 3rd Ave", "direction": "E",
              "lat": 47.610, "lon": -122.337, "locationType": 0,
              "routeIds": ["1_100275", "1_100009", "1_100223", "1_102650"],
              "staticRouteIds": ["1_100275"], "wheelchairBoarding": "ACCESSIBLE"
            },
            "references": {
              "routes": [
                { "id": "1_100275", "shortName": "8", "agencyId": "1" },
                { "id": "1_100009", "shortName": "10", "agencyId": "1" },
                { "id": "1_100223", "shortName": "43", "agencyId": "1" },
                { "id": "1_102650", "shortName": "Link", "agencyId": "1" }
              ],
              "agencies": [ { "id": "1", "name": "Metro" } ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun decodesStopAndResolvesItsRoutes() {
        val envelope: ObaEnvelope<EntryWithReferences<StopReference>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val data = envelope.data!!
        val stop = data.entry
        assertEquals("1_29261", stop.id)
        assertEquals("29261", stop.code)
        // locationType 0 == a stop (vs. a station).
        assertEquals(0, stop.locationType)
        assertEquals(listOf("1_100275", "1_100009", "1_100223", "1_102650"), stop.routeIds)

        // Every routeId resolves to a route in the references (legacy asserted routes.size == routeIds).
        val routes = stop.routeIds.map { data.references.route(it) }
        assertEquals(stop.routeIds.size, routes.size)
        routes.forEach { assertNotNull(it) }
        assertEquals("8", data.references.route("1_100275")?.shortName)
    }
}
