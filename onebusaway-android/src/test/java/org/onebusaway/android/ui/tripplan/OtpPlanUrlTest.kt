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
package org.onebusaway.android.ui.tripplan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the OTP `/plan` URL shape decision — the regression behind "planning not possible": a base
 * already rooted at a router (Puget Sound) must NOT have `/routers/default` prepended again, or the
 * doubled path 500s. Server-root bases still get the segment inserted.
 */
class OtpPlanUrlTest {

    private val query = "?fromPlace=47.6,-122.3&toPlace=47.7,-122.4"

    @Test
    fun routerRootedBase_appendsPlanOnly_noDoubledSegment() {
        val base = "https://otp.prod.sound.obaweb.org/otp/routers/default"
        assertEquals(
            "https://otp.prod.sound.obaweb.org/otp/routers/default/plan$query",
            otpPlanUrl(base, query, oldServer = false)
        )
    }

    @Test
    fun serverRootBase_insertsRoutersDefaultSegment() {
        val base = "https://otp.prod.obahart.org/otp"
        assertEquals(
            "https://otp.prod.obahart.org/otp/routers/default/plan$query",
            otpPlanUrl(base, query, oldServer = false)
        )
    }

    @Test
    fun serverRootBase_oldServerFallback_appendsBarePlan() {
        val base = "https://legacy.example.org/otp"
        assertEquals(
            "https://legacy.example.org/otp/plan$query",
            otpPlanUrl(base, query, oldServer = true)
        )
    }

    @Test
    fun routerRootedBase_ignoresStaleOldServerFlag() {
        // A router-rooted base is unambiguously modern; a stale old-server flag must not change its URL.
        val base = "https://otp.prod.sound.obaweb.org/otp/routers/default"
        assertEquals(
            otpPlanUrl(base, query, oldServer = false),
            otpPlanUrl(base, query, oldServer = true)
        )
    }
}
