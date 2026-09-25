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
package org.onebusaway.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.region.Region

/**
 * JVM unit tests for the pure [BikeshareAvailability] predicates (region + custom OTP URL), covering
 * both the trip-planning question (answered per the protocol the plan will use) and the station-layer
 * question (always OTP1, the overlay's only data source).
 */
class BikeshareAvailabilityTest {

    /** An OTP1-only region: no GraphQL endpoint published, so plans go over OTP1 REST. */
    private fun otp1Region(supportsBikeshare: Boolean) = Region(supportsOtpBikeshare = supportsBikeshare)

    /**
     * An OTP2 region, mirroring the live directory entries: Puget Sound publishes a GraphQL endpoint
     * with `supportsOtpBikeshare: false` + `supportsOtpGraphqlBikeshare: true`.
     */
    private fun otp2Region(
        supportsOtp1Bikeshare: Boolean,
        supportsGraphqlBikeshare: Boolean
    ) = Region(
        otpBaseGraphqlUrl = "https://otp2.example/otp",
        supportsOtpBikeshare = supportsOtp1Bikeshare,
        supportsOtpGraphqlBikeshare = supportsGraphqlBikeshare
    )

    @Test
    fun `trip planning enabled when an OTP1 region supports OTP bikeshare`() {
        assertTrue(BikeshareAvailability.isTripPlanningEnabled(otp1Region(supportsBikeshare = true), null))
    }

    @Test
    fun `trip planning disabled when the region does not support bikeshare and no custom OTP URL`() {
        assertFalse(BikeshareAvailability.isTripPlanningEnabled(otp1Region(supportsBikeshare = false), null))
        assertFalse(BikeshareAvailability.isTripPlanningEnabled(otp1Region(supportsBikeshare = false), ""))
    }

    /** An OTP2 region must not inherit its stale OTP1 flag. */
    @Test
    fun `trip planning disabled for an OTP2 region whose GraphQL server lacks bikeshare`() {
        assertFalse(
            BikeshareAvailability.isTripPlanningEnabled(
                otp2Region(supportsOtp1Bikeshare = true, supportsGraphqlBikeshare = false),
                null
            )
        )
    }

    /** A blank GraphQL URL is not an OTP2 region, so the OTP1 flag still answers. */
    @Test
    fun `a blank GraphQL URL leaves the region on the OTP1 flag`() {
        assertTrue(
            BikeshareAvailability.isTripPlanningEnabled(
                Region(otpBaseGraphqlUrl = "  ", supportsOtpBikeshare = true, supportsOtpGraphqlBikeshare = false),
                null
            )
        )
    }

    @Test
    fun `trip planning enabled when a custom OTP URL is set, even if the region does not support bikeshare`() {
        assertTrue(
            BikeshareAvailability.isTripPlanningEnabled(otp1Region(supportsBikeshare = false), "https://otp.example")
        )
    }

    @Test
    fun `custom OTP URL enables trip planning bikeshare even with no region`() {
        assertTrue(BikeshareAvailability.isTripPlanningEnabled(null, "https://otp.example"))
    }

    @Test
    fun `trip planning disabled with no region and no custom OTP URL`() {
        assertFalse(BikeshareAvailability.isTripPlanningEnabled(null, null))
        assertFalse(BikeshareAvailability.isTripPlanningEnabled(null, ""))
    }

    @Test
    fun `station layer follows the OTP1 flag`() {
        assertTrue(BikeshareAvailability.isStationLayerEnabled(otp1Region(supportsBikeshare = true), null))
        assertFalse(BikeshareAvailability.isStationLayerEnabled(otp1Region(supportsBikeshare = false), null))
    }

    /**
     * The Puget Sound case. Since #2168 the rental map layer reads `vehicleRentalsByBbox` where a
     * region publishes a bikeshare-capable GraphQL endpoint, so an OTP2-only bikeshare region now
     * *does* draw rentals — where before it could plan bike trips while drawing nothing, the overlay's
     * only source being OTP1 `/bike_rental`.
     */
    @Test
    fun `OTP2-only bikeshare region both plans bike trips and draws rentals`() {
        val region = otp2Region(supportsOtp1Bikeshare = false, supportsGraphqlBikeshare = true)
        assertTrue(BikeshareAvailability.isTripPlanningEnabled(region, null))
        assertTrue(BikeshareAvailability.isStationLayerEnabled(region, null))
    }

    /**
     * The two gates are still separate flags, which is what stops them re-collapsing onto one: a
     * region whose OTP1 server publishes rentals draws them even though its OTP2 server — the one a
     * plan is sent to — says it has none.
     */
    @Test
    fun `an OTP2 region with rentals only on its OTP1 host still draws them`() {
        val region = otp2Region(supportsOtp1Bikeshare = true, supportsGraphqlBikeshare = false)
        assertFalse(BikeshareAvailability.isTripPlanningEnabled(region, null))
        assertTrue(BikeshareAvailability.isStationLayerEnabled(region, null))
    }

    @Test
    fun `station layer disabled with no region and no custom OTP URL`() {
        assertFalse(BikeshareAvailability.isStationLayerEnabled(null, null))
        assertFalse(BikeshareAvailability.isStationLayerEnabled(null, ""))
    }
}
