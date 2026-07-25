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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Region.usesOtp2], the single definition of the OTP-protocol signal. Two
 * subsystems now resolve off it — `TripRequestBuilder.otpTarget` (which endpoint a plan is sent to)
 * and `BikeshareAvailability.isTripPlanningEnabled` (which per-server capability flag answers) — so
 * they can't disagree about which server a plan will hit. Pinned directly here rather than only
 * through those consumers.
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
}
