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
import org.junit.Test

/**
 * Unit tests for [bikeRentalUrl] — the OTP1 URL-structure selection.
 *
 * This file used to cover a directions-mode station filter and a show/clear/leave action gate as well.
 * Both went when directions stopped drawing rentals (#2168): with no mode that forces the layer on,
 * "which of the trip's own stations to keep" and "is the filter known yet" are questions nobody asks.
 */
class RentalUrlTest {

    // --- bikeRentalUrl: the OTP1 url-structure selection (the doubled-path fix) ---

    @Test
    fun `new structure inserts routers default for a server-rooted base`() {
        // Tampa/HART form: otpBaseUrl is the OTP server root.
        assertEquals(
            "https://otp.prod.obahart.org/otp/routers/default/bike_rental" +
                "?lowerLeft=27.9,-82.5&upperRight=28.1,-82.4",
            bikeRentalUrl(
                "https://otp.prod.obahart.org/otp",
                useOldUrlStructure = false,
                27.9,
                -82.5,
                28.1,
                -82.4
            )
        )
    }

    @Test
    fun `old structure appends bike_rental directly to a router-rooted base`() {
        // Puget Sound form: otpBaseUrl already ends in routers/default — the new structure would
        // double it (the bug being fixed), so the old structure appends bike_rental directly.
        assertEquals(
            "https://otp.prod.sound.obaweb.org/otp/routers/default/bike_rental" +
                "?lowerLeft=47.5,-122.4&upperRight=47.7,-122.2",
            bikeRentalUrl(
                "https://otp.prod.sound.obaweb.org/otp/routers/default",
                useOldUrlStructure = true,
                47.5,
                -122.4,
                47.7,
                -122.2
            )
        )
    }
}
