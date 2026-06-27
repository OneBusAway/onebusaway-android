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

import org.onebusaway.android.api.adapters.toRouteDetails

import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.RouteReference

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic coverage for [toRouteDetails] and the kotlinx.serialization wire models: agency
 * resolution from references, and that the OBA envelope decodes (ignoring unknown keys).
 */
class RouteDetailsMapperTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun resolvesAgencyReferenceById() {
        val data = EntryWithReferences(
            entry = RouteReference(
                id = "1_100",
                shortName = "8",
                longName = "Capitol Hill",
                description = "via Denny",
                url = "https://example.org/route/8",
                agencyId = "1",
            ),
            references = References(
                agencies = listOf(
                    AgencyReference(id = "2", name = "Other"),
                    AgencyReference(id = "1", name = "Metro", url = "https://metro.example"),
                ),
            ),
        )

        val details = data.toRouteDetails()

        assertEquals("1_100", details.id)
        assertEquals("8", details.shortName)
        assertEquals("Capitol Hill", details.longName)
        assertEquals("via Denny", details.description)
        assertEquals("https://example.org/route/8", details.url)
        assertEquals("1", details.agency?.id)
        assertEquals("Metro", details.agency?.name)
        assertEquals("https://metro.example", details.agency?.url)
    }

    @Test
    fun agencyIsNullWhenReferenceMissing() {
        val data = EntryWithReferences(
            entry = RouteReference(id = "1_100", agencyId = "missing"),
            references = References(agencies = listOf(AgencyReference(id = "1", name = "Metro"))),
        )

        assertNull(data.toRouteDetails().agency)
    }

    @Test
    fun decodesEnvelopeIgnoringUnknownKeys() {
        // Mirrors the real OBA wire shape: `version` is a JSON number (not a string), `code` and
        // `currentTime` are numbers, and the references pool carries entity kinds (situations,
        // stops, trips, stopTimes) and per-agency fields the model does not declare.
        val body = """
            {
              "version": 2,
              "code": 200,
              "currentTime": 1700000000000,
              "text": "OK",
              "unexpectedTopLevel": 42,
              "data": {
                "entry": {
                  "id": "1_100",
                  "shortName": "8",
                  "nullSafeShortName": "8",
                  "type": 3,
                  "agencyId": "1",
                  "somethingNew": true
                },
                "references": {
                  "agencies": [
                    { "id": "1", "name": "Metro", "privateService": false, "fareUrl": "x" }
                  ],
                  "routes": [],
                  "situations": [],
                  "stops": [],
                  "trips": [],
                  "stopTimes": []
                }
              }
            }
        """.trimIndent()

        val envelope: ObaEnvelope<EntryWithReferences<RouteReference>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        assertEquals(2, envelope.version)
        val details = envelope.data!!.toRouteDetails()
        assertEquals("8", details.shortName)
        assertEquals("Metro", details.agency?.name)
    }
}
