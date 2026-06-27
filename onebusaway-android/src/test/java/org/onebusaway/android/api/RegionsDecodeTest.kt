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

import org.onebusaway.android.api.adapters.toObaRegion

import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.RegionDto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ports the regions-parsing coverage of the retired instrumented RegionsTest onto the modernized
 * `regions` client: decodes the real bundled directory file and the Umami fixture (read from their
 * source-tree locations; unit tests run with the module dir as the working directory), then maps
 * through [toObaRegion] to assert the same things the old test did.
 */
class RegionsDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun decode(path: String): List<RegionDto> {
        val envelope: ObaEnvelope<ListWithReferences<RegionDto>> =
            json.decodeFromString(File(path).readText())
        assertEquals(200, envelope.code)
        return envelope.data!!.list
    }

    /** Ports testRequest: the bundled fail-safe file decodes to named regions. */
    @Test
    fun decodesBundledRegionsFile() {
        val regions = decode("src/main/res/raw/regions_v3.json")
        assertTrue(regions.isNotEmpty())
        regions.forEach { assertNotNull(it.toObaRegion().name) }
    }

    /** Ports testUmamiAnalyticsParsing: the nested umamiAnalytics object maps onto the region. */
    @Test
    fun mapsUmamiAnalyticsConfig() {
        val regions = decode("src/androidTest/res/raw/regions_umami_test.json").map { it.toObaRegion() }

        assertEquals("https://umami.example.com", regions[0].umamiAnalyticsUrl)
        assertEquals("abc-123-uuid", regions[0].umamiAnalyticsId)
        // A region without the umamiAnalytics object yields a null config (not an empty one).
        assertNull(regions[1].umamiAnalyticsUrl)
        assertNull(regions[1].umamiAnalyticsId)
    }
}
