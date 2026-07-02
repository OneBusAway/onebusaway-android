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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports the legacy RouteIdsForAgencyRequestTest / StopIdsForAgencyRequestTest onto the modernized
 * `route-ids-for-agency` and `stop-ids-for-agency` endpoints, which share the
 * `ListWithReferences<String>` shape (a bare list of ids plus `limitExceeded`).
 */
class AgencyIdListsDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesRouteIds() {
        val body = """
            {
              "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
              "data": { "list": ["1_100", "1_101", "1_102"], "limitExceeded": false, "references": {} }
            }
        """.trimIndent()
        val envelope: ObaEnvelope<ListWithReferences<String>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val data = envelope.data!!
        assertEquals(listOf("1_100", "1_101", "1_102"), data.list)
        assertEquals(false, data.limitExceeded)
    }

    @Test
    fun decodesStopIdsWithLimitExceeded() {
        val body = """
            {
              "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
              "data": { "list": ["1_75403", "1_75404"], "limitExceeded": true, "references": {} }
            }
        """.trimIndent()
        val envelope: ObaEnvelope<ListWithReferences<String>> = json.decodeFromString(body)

        val data = envelope.data!!
        assertTrue(data.list.contains("1_75403"))
        assertEquals(true, data.limitExceeded)
    }
}
