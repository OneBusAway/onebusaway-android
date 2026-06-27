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
import org.onebusaway.android.api.contract.ShapeEntry

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ports the retired instrumented ShapeRequestTest onto the modernized `shape` endpoint: decodes the
 * captured shape envelope (read from its source-tree location; unit tests run with the module dir as
 * working directory) and asserts the encoded polyline has points. The polyline *decoding* itself is
 * still covered by PolylineDecoderTest (PolylineDecoder.decodeLine, the shared decoder this entry feeds).
 */
class ShapeDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesShapeEntry() {
        val body = File("src/androidTest/res/raw/shape_1_40046045.json").readText()
        val envelope: ObaEnvelope<EntryWithReferences<ShapeEntry>> = json.decodeFromString(body)

        val entry = envelope.requireData().entry
        assertEquals(230, entry.length)
        assertTrue(entry.points.isNotEmpty())
    }
}
