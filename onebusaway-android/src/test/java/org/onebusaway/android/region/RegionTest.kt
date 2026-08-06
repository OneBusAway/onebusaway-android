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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Region]'s derived properties — the single definitions that several subsystems
 * resolve off, pinned directly here rather than only through their consumers.
 *
 * [Region.usesOtp2] is the OTP-protocol signal, shared by `TripRequestBuilder.otpTarget` (which
 * endpoint a plan is sent to) and `BikeshareAvailability.isTripPlanningEnabled` (which per-server
 * capability flag answers), so the two can't disagree about which server a plan will hit.
 * [Region.sidecarTarget] is the identity of a region's sidecar endpoint, keying the weather and
 * wide-alert flows.
 */
class RegionTest {

    @Test
    fun `no GraphQL URL means the region plans over OTP1`() {
        assertFalse(Region(otpBaseGraphqlUrl = null).usesOtp2)
    }

    /**
     * A directory entry can carry the key with an empty/whitespace value, which is not an endpoint.
     * `otpTarget` would otherwise route a plan at a blank base URL.
     */
    @Test
    fun `a blank GraphQL URL is not an OTP2 region`() {
        assertFalse(Region(otpBaseGraphqlUrl = "").usesOtp2)
        assertFalse(Region(otpBaseGraphqlUrl = "   ").usesOtp2)
    }

    @Test
    fun `a published GraphQL endpoint means the region plans over OTP2`() {
        assertTrue(Region(otpBaseGraphqlUrl = "https://otp2.example/otp").usesOtp2)
    }

    @Test
    fun `a directory region addresses the sidecar by its own id`() {
        val region = Region(id = 19, sidecarBaseUrl = "https://a.example.org/")
        assertEquals(19L, region.sidecarId)
        assertEquals(Region.SidecarTarget("https://a.example.org/", 19L), region.sidecarTarget)
    }

    /**
     * The reason [Region.sidecarTarget] exists: the sidecar id is not unique across regions. A
     * deep-link-added region carries the id its *own* sidecar knows it by (#2165), which can equal a
     * directory region's id on a completely different host. Anything keying sidecar-scoped state on the
     * id alone would read the two as the same endpoint and never refresh on the switch.
     */
    @Test
    fun `two regions can share a sidecar id but never a sidecar target`() {
        val directory = Region(id = 19, sidecarBaseUrl = "https://a.example.org/")
        val custom = Region(
            id = -2,
            sidecarBaseUrl = "https://b.example.org/",
            sidecarRegionId = 19,
            custom = true
        )
        assertEquals(directory.sidecarId, custom.sidecarId)
        assertNotEquals(directory.sidecarTarget, custom.sidecarTarget)
    }
}
