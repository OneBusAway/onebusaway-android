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
package org.onebusaway.android.map.rental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.region.Region

/**
 * Unit tests for [rentalSource] — which OTP server the rental layer asks, and how (#2168). The rule
 * this pins is that OTP2 wins only when the region says *that* server serves rentals: a region that
 * plans over OTP2 but publishes bikeshare only on its OTP1 host must keep drawing the layer it has.
 */
class RentalSourceTest {

    private fun region(
        otpBaseUrl: String? = null,
        supportsOtpBikeshare: Boolean = false,
        otpBaseGraphqlUrl: String? = null,
        supportsOtpGraphqlBikeshare: Boolean = false
    ) = Region(
        id = 1,
        name = "Test",
        active = true,
        obaBaseUrl = null,
        siriBaseUrl = null,
        bounds = emptyArray(),
        open311Servers = emptyArray(),
        language = null,
        contactEmail = null,
        supportsObaDiscoveryApis = true,
        supportsObaRealtimeApis = true,
        supportsSiriRealtimeApis = false,
        twitterUrl = null,
        experimental = false,
        stopInfoUrl = null,
        otpBaseUrl = otpBaseUrl,
        otpContactEmail = null,
        supportsOtpBikeshare = supportsOtpBikeshare,
        otpBaseGraphqlUrl = otpBaseGraphqlUrl,
        supportsOtpGraphqlBikeshare = supportsOtpGraphqlBikeshare
    )

    @Test
    fun `a region publishing a bikeshare-capable GraphQL endpoint uses OTP2`() {
        val source = rentalSource(
            customOtpApiUrl = null,
            customUrlUsesGraphQl = false,
            region = region(
                otpBaseUrl = "https://otp.example.org/otp",
                supportsOtpBikeshare = true,
                otpBaseGraphqlUrl = "https://otp2.example.org/otp",
                supportsOtpGraphqlBikeshare = true
            )
        )
        assertEquals(RentalSource.Otp2("https://otp2.example.org/otp"), source)
    }

    @Test
    fun `an OTP2 region whose GraphQL server has no rentals falls back to OTP1`() {
        val source = rentalSource(
            customOtpApiUrl = null,
            customUrlUsesGraphQl = false,
            region = region(
                otpBaseUrl = "https://otp.example.org/otp",
                supportsOtpBikeshare = true,
                otpBaseGraphqlUrl = "https://otp2.example.org/otp",
                supportsOtpGraphqlBikeshare = false
            )
        )
        assertEquals(RentalSource.Otp1("https://otp.example.org/otp"), source)
    }

    @Test
    fun `a region with no rental server at all has no source`() {
        assertNull(rentalSource(null, false, region(otpBaseUrl = "https://otp.example.org/otp")))
        assertNull(rentalSource(null, false, region = null))
    }

    /** The advanced-setting hatch answers for itself — the user states the protocol, there is no flag. */
    @Test
    fun `a custom URL wins over the region, on the protocol the user set`() {
        val withRentals = region(
            otpBaseUrl = "https://otp.example.org/otp",
            supportsOtpBikeshare = true,
            otpBaseGraphqlUrl = "https://otp2.example.org/otp",
            supportsOtpGraphqlBikeshare = true
        )
        assertEquals(
            RentalSource.Otp2("https://custom.example.org/otp"),
            rentalSource("https://custom.example.org/otp", customUrlUsesGraphQl = true, region = withRentals)
        )
        assertEquals(
            RentalSource.Otp1("https://custom.example.org/otp"),
            rentalSource("https://custom.example.org/otp", customUrlUsesGraphQl = false, region = withRentals)
        )
    }

    /** A whitespace-only custom URL is not a configured server — see `OtpTarget.customOtpApiUrl`. */
    @Test
    fun `a blank custom URL is ignored`() {
        assertEquals(
            RentalSource.Otp1("https://otp.example.org/otp"),
            rentalSource("   ", false, region(otpBaseUrl = "https://otp.example.org/otp", supportsOtpBikeshare = true))
        )
    }
}
