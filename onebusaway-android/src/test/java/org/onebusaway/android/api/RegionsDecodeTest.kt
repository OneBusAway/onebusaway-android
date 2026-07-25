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

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.toObaRegion
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.RegionDto

/**
 * Ports the regions-parsing coverage of the retired instrumented RegionsTest onto the modernized
 * `regions` client: decodes the real bundled directory file and the Umami fixture (read from their
 * source-tree locations; unit tests run with the module dir as the working directory), then maps
 * through [toObaRegion] to assert the same things the old test did.
 */
class RegionsDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

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

    /**
     * OTP2 protocol selection (#1780) is carried by a region's `otpBaseGraphqlUrl` (the OTP mount
     * base; the client appends `/gtfs/v1`). Puget Sound is pointed at its OTP2 GraphQL server;
     * every other bundled region stays on OTP1 REST (null). Guards both the intended flip and
     * against an accidental one elsewhere.
     */
    @Test
    fun bundledRegionsSelectOtpProtocolByGraphqlUrl() {
        val regions = decode("src/main/res/raw/regions_v3.json").map { it.toObaRegion() }
        val pugetSound = regions.single { it.name == "Puget Sound" }
        assertEquals(
            "https://peq6qe6fei.execute-api.us-west-2.amazonaws.com/prod/otp",
            pugetSound.otpBaseGraphqlUrl
        )
        regions.filter { it.name != "Puget Sound" }
            .forEach { assertNull(it.otpBaseGraphqlUrl) }
    }

    /**
     * The per-protocol bikeshare flags decode off the wire and stay distinct, **and each region is
     * paired with the protocol that makes its flag the one consulted** (`Region.usesOtp2`). The
     * bundled fail-safe file is the first-launch/offline source, so either half going missing there
     * silently reinstates the bug even once the live directory is right — a dropped
     * `supportsOtpGraphqlBikeshare` (the DTO default is false), or a dropped `otpBaseGraphqlUrl`,
     * which sends Puget Sound back to its `false` OTP1 flag. That is exactly how its bikeshare trip
     * planning stayed hidden.
     */
    @Test
    fun bundledRegionsCarryPerProtocolBikeshareFlags() {
        val regions = decode("src/main/res/raw/regions_v3.json").map { it.toObaRegion() }

        val pugetSound = regions.single { it.name == "Puget Sound" }
        assertTrue("Puget Sound plans over OTP2, so its GraphQL flag is the one that answers", pugetSound.usesOtp2)
        assertFalse("Puget Sound's OTP1 /bike_rental is empty", pugetSound.supportsOtpBikeshare)
        assertTrue("Puget Sound's OTP2 server plans bikeshare", pugetSound.supportsOtpGraphqlBikeshare)

        // The mirror case, so the two flags can't be collapsed back onto one. Tampa Bay plans over
        // OTP1, so its OTP1 flag is the one that answers; the bundled entry omits
        // supportsOtpGraphqlBikeshare entirely, which decodes to the same false the live directory
        // states explicitly.
        val tampa = regions.single { it.name == "Tampa Bay" }
        assertFalse("Tampa Bay plans over OTP1, so its OTP1 flag is the one that answers", tampa.usesOtp2)
        assertTrue(tampa.supportsOtpBikeshare)
        assertFalse(tampa.supportsOtpGraphqlBikeshare)
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
