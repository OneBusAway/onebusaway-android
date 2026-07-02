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

import org.onebusaway.android.api.contract.NoData
import org.onebusaway.android.api.contract.ObaEnvelope

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers the report-problem response path: the endpoints carry no data payload, so the response
 * decodes to `ObaEnvelope<NoData>` and success is asserted with [requireOk] (not [requireData]).
 * Confirms the data-less envelope decodes and that the OK/non-OK codes map the way the repository's
 * `runCatching { …requireOk() }` relies on.
 */
class ReportProblemDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun okResponseDecodesAndPassesRequireOk() {
        val body = """{ "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK" }"""
        val envelope: ObaEnvelope<NoData> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        envelope.requireOk() // must not throw
    }

    @Test
    fun errorCodeThrowsApiException() {
        val body = """{ "version": 2, "code": 404, "currentTime": 1782347930000, "text": "not found" }"""
        val envelope: ObaEnvelope<NoData> = json.decodeFromString(body)

        val thrown = assertThrows(ObaApiException::class.java) { envelope.requireOk() }
        assertEquals(404, thrown.code)
    }
}
