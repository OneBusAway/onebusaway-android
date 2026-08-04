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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the custom-region model behind the `add-region` deep link (#2027): id allocation, and
 * the invariants [customRegion] has to hold for the resulting region to actually be usable and to stay
 * out of automatic region selection.
 */
class CustomRegionsTest {

    private val request = CustomRegionRequest(
        name = "Test Deployment",
        obaBaseUrl = "https://api.example.com",
        otpBaseUrl = "https://otp.example.com",
        sidecarBaseUrl = "https://sidecar.example.com",
        umamiAnalyticsUrl = "https://umami.example.com",
        umamiAnalyticsId = "abc123"
    )

    // --- id allocation ---

    @Test
    fun `the first custom region on an empty cache gets the first custom id`() {
        assertEquals(FIRST_CUSTOM_REGION_ID, nextCustomRegionId(minId = null))
    }

    @Test
    fun `a cache holding only directory regions still gets the first custom id`() {
        // Directory ids are non-negative (Tampa is 0), so the minimum tells us nothing is custom yet.
        assertEquals(FIRST_CUSTOM_REGION_ID, nextCustomRegionId(minId = 0L))
        assertEquals(FIRST_CUSTOM_REGION_ID, nextCustomRegionId(minId = 5L))
    }

    @Test
    fun `each new custom id goes below every id present`() {
        // Live regions can't collide. (Removing the lowest one does free its id again — see the KDoc for
        // why that's safe: no durable reference to a removed region's id survives.)
        assertEquals(-3L, nextCustomRegionId(minId = -2L))
        assertEquals(-4L, nextCustomRegionId(minId = -3L))
    }

    @Test
    fun `every allocated custom id stays below the sentinel and survives the preference`() {
        // Two properties over one sequence: an allocated id can never be read as a directory id
        // (`>= 0`) nor as the "no region" sentinel (`-1`), which is what makes it round-trip.
        generateSequence(nextCustomRegionId(null), ::nextCustomRegionId).take(50).forEach { id ->
            assertTrue("id $id must be below the sentinel", id < NO_REGION_ID)
            assertNotNull("id $id must survive the preference", persistedRegionId(id))
        }
    }

    // --- the region-id preference sentinel ---

    @Test
    fun `the no-region sentinel reads back as no region`() {
        assertNull(persistedRegionId(NO_REGION_ID))
    }

    @Test
    fun `a custom region id survives a round trip through the preference`() {
        // The bug this rule exists for: reading the preference as `id < 0` rather than
        // `id != NO_REGION_ID` discarded every custom region at cold start — it stayed in the database
        // but was never restored, so the app silently fell back to region resolution on each launch.
        // The rule now lives only in persistedRegionId, which is what this pins; the repository call site
        // itself is private and Context-coupled, so it isn't reachable from a JVM test.
        assertEquals(FIRST_CUSTOM_REGION_ID, persistedRegionId(FIRST_CUSTOM_REGION_ID))
        assertEquals(-7L, persistedRegionId(-7L))
    }

    @Test
    fun `a directory region id survives a round trip through the preference`() {
        assertEquals(0L, persistedRegionId(0L)) // Tampa
        assertEquals(1L, persistedRegionId(1L))
    }

    // --- the region built from a request ---

    @Test
    fun `a custom region carries every field the link supplied`() {
        val region = customRegion(FIRST_CUSTOM_REGION_ID, request)
        assertEquals("Test Deployment", region.name)
        assertEquals("https://api.example.com", region.obaBaseUrl)
        assertEquals("https://otp.example.com", region.otpBaseUrl)
        assertEquals("https://sidecar.example.com", region.sidecarBaseUrl)
        assertEquals("https://umami.example.com", region.umamiAnalyticsUrl)
        assertEquals("abc123", region.umamiAnalyticsId)
        assertEquals(FIRST_CUSTOM_REGION_ID, region.id)
    }

    @Test
    fun `a custom region is marked custom`() {
        // The flag the cache, the picker and resolveRegionStatus all key off.
        assertTrue(customRegion(FIRST_CUSTOM_REGION_ID, request).custom)
    }

    @Test
    fun `a custom region satisfies the usability requirements`() {
        // RegionUtils.isRegionUsable requires all four, or the app refuses to use the region at all -
        // which would make the link pointless. These are declarations, not observations; see the KDoc.
        val region = customRegion(FIRST_CUSTOM_REGION_ID, request)
        assertTrue("active", region.active)
        assertTrue("discovery APIs", region.supportsObaDiscoveryApis)
        assertTrue("realtime APIs", region.supportsObaRealtimeApis)
        assertFalse("must not be experimental (that would need an opt-in)", region.experimental)
    }

    @Test
    fun `a custom region has no bounds, so it can never be auto-selected`() {
        // getClosestRegion skips a region it can't measure a distance to. A deep link carries no
        // coverage area, so this is the honest representation as well as the safe one.
        assertTrue(customRegion(FIRST_CUSTOM_REGION_ID, request).bounds.isEmpty())
    }

    @Test
    fun `a custom region has no umami config when the link supplied no url`() {
        val region = customRegion(FIRST_CUSTOM_REGION_ID, request.copy(umamiAnalyticsUrl = null))
        assertNull(region.umamiAnalytics)
    }

    // --- the sidecar's id for the region (#2165) ---

    @Test
    fun `the link's region-id addresses the sidecar without entering the directory id space`() {
        // The whole point of keeping the two apart: the sidecar hears the id it published, while the
        // primary key stays negative, where a directory refresh carrying region 19 can never land on it.
        val region = customRegion(FIRST_CUSTOM_REGION_ID, request.copy(regionId = 19L))
        assertEquals(19L, region.sidecarId)
        assertEquals(FIRST_CUSTOM_REGION_ID, region.id)
        assertTrue("the local id must stay out of the directory's space", region.id < NO_REGION_ID)
    }

    @Test
    fun `a region with no region-id addresses the sidecar by its own id`() {
        // The pre-#2165 behaviour, which is also the right one for every directory region: our primary
        // key *is* the directory's id there, so there is nothing to override.
        val region = customRegion(FIRST_CUSTOM_REGION_ID, request)
        assertNull(region.sidecarRegionId)
        assertEquals(FIRST_CUSTOM_REGION_ID, region.sidecarId)
        assertEquals(1L, Region(id = 1L).sidecarId)
    }

    @Test
    fun `a custom region has no contact email, so no problem-report address is offered`() {
        // Deliberately not iOS's placeholder example@example.com: ReportTypeRepository offers the
        // email-a-problem option when a contact address exists, and mailing a made-up one is worse.
        assertTrue(customRegion(FIRST_CUSTOM_REGION_ID, request).contactEmail.isNullOrEmpty())
    }
}
