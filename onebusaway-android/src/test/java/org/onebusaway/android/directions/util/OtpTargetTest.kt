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
package org.onebusaway.android.directions.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.region.Region

/**
 * Unit tests for [OtpTarget.resolve] — which OTP server a plan targets, and whether there is one to
 * target at all (#2264). The rule that matters here is the second one: three of the seven regions in
 * the directory publish no planner, and telling *that* apart from "no region selected" is what the
 * whole availability gate rests on.
 */
class OtpTargetTest {

    private fun region(otpBaseUrl: String? = null, otpBaseGraphqlUrl: String? = null) = Region(
        id = 4,
        name = "Washington, D.C.",
        active = true,
        otpBaseUrl = otpBaseUrl,
        otpBaseGraphqlUrl = otpBaseGraphqlUrl
    )

    private fun resolve(customUrl: String? = null, customUrlUsesOtp2: Boolean = false, region: Region? = null) = OtpTarget.resolve(customUrl, customUrlUsesOtp2, region)

    @Test
    fun `an OTP1 region targets its REST base url`() {
        val target = resolve(region = region(otpBaseUrl = "https://otp.example.org/otp"))

        assertEquals("https://otp.example.org/otp", target.baseUrl)
        assertFalse(target.usesOtp2)
        assertTrue(target.isAvailable)
        assertNull(target.unavailable)
    }

    @Test
    fun `a region publishing a GraphQL endpoint targets it over the REST one`() {
        val target = resolve(
            region = region(
                otpBaseUrl = "https://otp1.example.org/otp",
                otpBaseGraphqlUrl = "https://otp2.example.org/otp"
            )
        )

        assertEquals("https://otp2.example.org/otp", target.baseUrl)
        assertTrue(target.usesOtp2)
        assertTrue(target.isAvailable)
    }

    /** The gap the old drawer check had: OTP2-only regions are planner-carrying regions. */
    @Test
    fun `a GraphQL-only region is available`() {
        val target = resolve(region = region(otpBaseGraphqlUrl = "https://otp2.example.org/otp"))

        assertTrue(target.isAvailable)
        assertTrue(target.usesOtp2)
    }

    @Test
    fun `a region with no planner is unavailable but is still a selected region`() {
        val target = resolve(region = region())

        assertFalse(target.isAvailable)
        assertEquals(OtpTarget.Unavailable.REGION_HAS_NO_PLANNER, target.unavailable)
    }

    /** An empty otpBaseUrl is the same fact as a missing one — see `formattedOtpBaseUrl`. */
    @Test
    fun `a region with a blank planner url is unavailable`() {
        val target = resolve(region = region(otpBaseUrl = ""))

        assertFalse(target.isAvailable)
        assertEquals(OtpTarget.Unavailable.REGION_HAS_NO_PLANNER, target.unavailable)
    }

    @Test
    fun `no region and no custom url is the no-region case`() {
        val target = resolve()

        assertFalse(target.isAvailable)
        assertEquals(OtpTarget.Unavailable.NO_REGION, target.unavailable)
    }

    @Test
    fun `a custom url wins over the region and carries its own protocol setting`() {
        val target = resolve(
            customUrl = "https://custom.example.org/otp",
            customUrlUsesOtp2 = true,
            region = region(otpBaseUrl = "https://otp.example.org/otp")
        )

        assertEquals("https://custom.example.org/otp", target.baseUrl)
        assertTrue(target.usesOtp2)
        assertTrue(target.isAvailable)
    }

    /** A custom server is a planner even where the region has none — the D.C. rider who set one. */
    @Test
    fun `a custom url makes a planner-less region available`() {
        assertTrue(resolve(customUrl = "https://custom.example.org/otp", region = region()).isAvailable)
    }
}
