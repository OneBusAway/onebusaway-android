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

import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripDetailsEntry

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports the legacy TripsForLocationTest onto the modernized `trips-for-location` endpoint: the
 * in-range case yields a list of trip-details entries; the out-of-range case sets `outOfRange`.
 */
class TripsForLocationDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesTripsInRange() {
        val body = """
            {
              "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
              "data": {
                "list": [ { "tripId": "1_18196913", "status": { "activeTripId": "1_18196913" } } ],
                "outOfRange": false, "limitExceeded": false, "references": {}
              }
            }
        """.trimIndent()
        val envelope: ObaEnvelope<ListWithReferences<TripDetailsEntry>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val data = envelope.data!!
        assertTrue(data.list.isNotEmpty())
        assertEquals("1_18196913", data.list[0].tripId)
        assertEquals(false, data.outOfRange)
    }

    @Test
    fun decodesOutOfRange() {
        val body = """
            {
              "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
              "data": { "list": [], "outOfRange": true, "limitExceeded": false, "references": {} }
            }
        """.trimIndent()
        val envelope: ObaEnvelope<ListWithReferences<TripDetailsEntry>> = json.decodeFromString(body)

        assertEquals(true, envelope.data!!.outOfRange)
    }
}
