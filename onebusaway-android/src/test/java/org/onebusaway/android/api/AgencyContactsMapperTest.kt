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

import org.onebusaway.android.api.data.toAgencyContacts

import org.onebusaway.android.api.contract.AgencyCoverage
import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.References

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic coverage for [toAgencyContacts] and the list-with-references wire models: agency
 * resolution from references by id, skipping unresolved entries, blank-URL normalization, and that
 * the real agencies-with-coverage envelope decodes (numeric version, unmodeled coverage geometry).
 */
class AgencyContactsMapperTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun resolvesAgenciesAndNormalizesBlankUrl() {
        val data = ListWithReferences(
            list = listOf(AgencyCoverage("1"), AgencyCoverage("2")),
            references = References(
                agencies = listOf(
                    AgencyReference(id = "1", name = "Metro", url = "https://metro.example"),
                    AgencyReference(id = "2", name = "Ferry", url = ""),
                )
            )
        )

        val items = data.toAgencyContacts()

        assertEquals(2, items.size)
        assertEquals("Metro", items[0].name)
        assertEquals("https://metro.example", items[0].url)
        assertEquals("Ferry", items[1].name)
        // Blank URL normalized to null so consumers only need one check.
        assertNull(items[1].url)
    }

    @Test
    fun skipsCoverageEntriesWithNoResolvableAgency() {
        val data = ListWithReferences(
            list = listOf(AgencyCoverage("1"), AgencyCoverage("missing")),
            references = References(agencies = listOf(AgencyReference(id = "1", name = "Metro")))
        )

        val items = data.toAgencyContacts()

        assertEquals(1, items.size)
        assertEquals("1", items[0].id)
    }

    @Test
    fun decodesRealEnvelopeShape() {
        // Mirrors the wire: numeric version, `list` entries carrying unmodeled coverage geometry,
        // `limitExceeded`, and per-agency fields the model does not declare.
        val body = """
            {
              "version": 2,
              "code": 200,
              "currentTime": 1700000000000,
              "text": "OK",
              "data": {
                "limitExceeded": false,
                "list": [
                  { "agencyId": "1", "lat": 47.5, "lon": -122.1, "latSpan": 0.6, "lonSpan": 0.7 }
                ],
                "references": {
                  "agencies": [
                    { "id": "1", "name": "Metro", "url": "https://metro.example",
                      "privateService": false, "lang": "EN" }
                  ]
                }
              }
            }
        """.trimIndent()

        val envelope: ObaEnvelope<ListWithReferences<AgencyCoverage>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val items = envelope.data!!.toAgencyContacts()
        assertEquals(1, items.size)
        assertEquals("Metro", items[0].name)
    }
}
