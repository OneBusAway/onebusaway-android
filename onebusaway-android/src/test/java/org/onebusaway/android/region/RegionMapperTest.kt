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
package org.onebusaway.android.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.database.oba.Open311ServerRecord
import org.onebusaway.android.database.oba.RegionBoundRecord
import org.onebusaway.android.database.oba.RegionRecord
import org.onebusaway.android.database.oba.RegionWithChildren

/** Parity + round-trip for the region-cache <-> domain [Region] mapping (replaces RegionCursor). */
class RegionMapperTest {

    @Test
    fun `toRegion maps scalar fields, flags, bounds and open311 servers`() {
        val row = RegionWithChildren(
            region = RegionRecord(
                id = 42L,
                name = "Puget Sound",
                obaBaseUrl = "https://oba",
                siriBaseUrl = "https://siri",
                language = "en",
                contactEmail = "a@b.c",
                supportsObaDiscovery = 1,
                supportsObaRealtime = 0,
                supportsSiriRealtime = 1,
                twitterUrl = "https://twitter",
                experimental = 1,
                stopInfoUrl = "https://stop",
                otpBaseUrl = "https://otp",
                otpContactEmail = "otp@b.c",
                supportsOtpBikeshare = 1,
                supportsEmbeddedSocial = 0,
                paymentAndroidAppId = "app.id",
                paymentWarningTitle = "title",
                paymentWarningBody = "body",
                sidecarBaseUrl = "https://sidecar",
                plausibleAnalyticsServerUrl = "https://plausible",
                umamiAnalyticsUrl = "https://umami",
                umamiAnalyticsId = "umami-id",
            ),
            bounds = listOf(RegionBoundRecord(regionId = 42L, latitude = 47.6, longitude = -122.3, latSpan = 0.4, lonSpan = 0.5)),
            open311Servers = listOf(Open311ServerRecord(regionId = 42L, jurisdiction = "j", apiKey = "k", baseUrl = "https://311")),
        )

        val region = RegionMapper.toRegion(row)

        assertEquals(42L, region.id)
        assertEquals("Puget Sound", region.name)
        assertTrue(region.active)
        assertEquals("https://oba", region.obaBaseUrl)
        assertTrue(region.supportsObaDiscoveryApis)
        assertFalse(region.supportsObaRealtimeApis)
        assertTrue(region.supportsSiriRealtimeApis)
        assertTrue(region.experimental)
        assertTrue(region.supportsOtpBikeshare)
        assertFalse(region.supportsEmbeddedSocial)
        assertEquals(1, region.bounds.size)
        assertEquals(47.6, region.bounds[0].lat, 0.0)
        assertEquals(0.5, region.bounds[0].lonSpan, 0.0)
        assertEquals(1, region.open311Servers.size)
        assertEquals("https://311", region.open311Servers[0].baseUrl)
        assertEquals("https://umami", region.umamiAnalytics?.url)
        assertEquals("umami-id", region.umamiAnalytics?.id)
    }

    @Test
    fun `null integer flags read as false and null umami is absent`() {
        val row = RegionWithChildren(
            region = RegionRecord(
                id = 1L, name = "R", obaBaseUrl = "", siriBaseUrl = "", language = "", contactEmail = "",
                supportsObaDiscovery = 0, supportsObaRealtime = 0, supportsSiriRealtime = 0,
                experimental = null, supportsOtpBikeshare = null, supportsEmbeddedSocial = null,
                umamiAnalyticsUrl = null, umamiAnalyticsId = null,
            ),
            bounds = emptyList(),
            open311Servers = emptyList(),
        )

        val region = RegionMapper.toRegion(row)

        assertFalse(region.experimental)
        assertFalse(region.supportsOtpBikeshare)
        assertNull(region.umamiAnalytics)
    }

    @Test
    fun `region round-trips through entities preserving fields`() {
        val original = Region(
            id = 7L,
            name = "Round Trip",
            active = true,
            obaBaseUrl = "https://oba",
            siriBaseUrl = "https://siri",
            bounds = arrayOf(Region.Bounds(1.0, 2.0, 3.0, 4.0)),
            open311Servers = arrayOf(Region.Open311Server("juris", "key", "https://311")),
            language = "en",
            contactEmail = "a@b.c",
            supportsObaDiscoveryApis = true,
            supportsObaRealtimeApis = false,
            supportsSiriRealtimeApis = true,
            twitterUrl = "https://twitter",
            experimental = false,
            supportsOtpBikeshare = true,
            supportsEmbeddedSocial = true,
            umamiAnalytics = Region.UmamiAnalyticsConfig("https://umami", "id"),
        )

        val back = RegionMapper.toRegion(RegionMapper.toEntities(original))

        assertEquals(original.id, back.id)
        assertEquals(original.name, back.name)
        assertEquals(original.obaBaseUrl, back.obaBaseUrl)
        assertEquals(original.supportsObaDiscoveryApis, back.supportsObaDiscoveryApis)
        assertEquals(original.supportsObaRealtimeApis, back.supportsObaRealtimeApis)
        assertEquals(original.experimental, back.experimental)
        assertEquals(original.supportsOtpBikeshare, back.supportsOtpBikeshare)
        assertEquals(original.language, back.language)
        assertEquals(original.contactEmail, back.contactEmail)
        assertEquals(original.siriBaseUrl, back.siriBaseUrl)
        assertEquals(original.supportsSiriRealtimeApis, back.supportsSiriRealtimeApis)
        assertEquals(original.twitterUrl, back.twitterUrl)
        assertEquals(original.supportsEmbeddedSocial, back.supportsEmbeddedSocial)
        assertEquals(1, back.bounds.size)
        assertEquals(3.0, back.bounds[0].latSpan, 0.0)
        assertEquals("juris", back.open311Servers[0].jurisdictionId)
        assertEquals("https://umami", back.umamiAnalytics?.url)
    }
}
