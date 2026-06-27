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

import org.onebusaway.android.api.contract.CurrentTime
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ports the legacy CurrentTimeRequestTest onto the modernized `current-time` endpoint. */
class CurrentTimeDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val body = """
        {
          "version": 2, "code": 200, "currentTime": 1782347930000, "text": "OK",
          "data": {
            "entry": { "time": 1782347930000, "readableTime": "2026-06-24T10:38:50-07:00" },
            "references": {}
          }
        }
    """.trimIndent()

    @Test
    fun decodesCurrentTime() {
        val envelope: ObaEnvelope<EntryWithReferences<CurrentTime>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val entry = envelope.data!!.entry
        assertEquals(1782347930000L, entry.time)
        assertEquals("2026-06-24T10:38:50-07:00", entry.readableTime)
    }
}
