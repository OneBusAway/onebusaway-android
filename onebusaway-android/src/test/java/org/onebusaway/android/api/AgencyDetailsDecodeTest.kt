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

import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ports the legacy AgencyRequestTest onto the modernized `agency` endpoint: decodes a real Puget
 * Sound agency envelope (numeric version; entry fields the model doesn't declare — disclaimer,
 * fareUrl, privateService, etc. — are ignored) and asserts the agency id/name.
 */
class AgencyDetailsDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Captured from api.pugetsound.onebusaway.org/api/where/agency/1.json (trimmed).
    private val body = """
        {
          "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
          "data": {
            "entry": {
              "id": "1", "name": "Metro Transit", "url": "https://kingcounty.gov/en/dept/metro",
              "timezone": "America/Los_Angeles", "lang": "EN", "phone": "206-553-3000",
              "disclaimer": "", "email": "", "privateService": false,
              "fareUrl": "https://kingcounty.gov/en/dept/metro/fares-and-payment/prices"
            },
            "references": {}
          }
        }
    """.trimIndent()

    @Test
    fun decodesAgency() {
        val envelope: ObaEnvelope<EntryWithReferences<AgencyReference>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val agency = envelope.data!!.entry
        assertEquals("1", agency.id)
        assertEquals("Metro Transit", agency.name)
        assertEquals("https://kingcounty.gov/en/dept/metro", agency.url)
        // Full agency record (parity with the retired ObaAgencyResponse), not just id/name/url.
        assertEquals("America/Los_Angeles", agency.timezone)
        assertEquals("EN", agency.lang)
        assertEquals("206-553-3000", agency.phone)
        assertEquals("https://kingcounty.gov/en/dept/metro/fares-and-payment/prices", agency.fareUrl)
        assertEquals(false, agency.privateService)
    }
}
