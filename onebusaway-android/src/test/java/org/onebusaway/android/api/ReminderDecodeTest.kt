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

import org.onebusaway.android.api.contract.ReminderResponse

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the create-alarm response decode. The reminders endpoint returns the created alarm's delete
 * path as `{ "url": "…" }`; that url is what the caller persists and later DELETEs. Unknown keys are
 * ignored so an expanded response stays compatible.
 */
class ReminderDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesReminderResponse() {
        val body =
            """{ "url": "https://sidecar.onebusaway.org/api/v2/regions/1/alarms/abc-123", "extra": true }"""
        val response = json.decodeFromString<ReminderResponse>(body)
        assertEquals("https://sidecar.onebusaway.org/api/v2/regions/1/alarms/abc-123", response.url)
    }

    @Test
    fun missingUrlDecodesToNull() {
        val response = json.decodeFromString<ReminderResponse>("{}")
        assertNull(response.url)
    }
}
