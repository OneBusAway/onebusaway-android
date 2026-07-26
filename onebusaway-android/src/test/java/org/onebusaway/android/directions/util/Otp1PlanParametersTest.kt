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
import org.junit.Test

/**
 * Covers [otp1PlanParameters] — the OTP 1.x REST sibling of [Otp2PlanRequestBuilder]'s
 * `buildPreferences`/`buildStreetPreferences`. A plain JVM unit test: it takes already-parsed values
 * rather than a `Bundle`, so no Robolectric is needed.
 *
 * The point is the boundary between the protocols. One builder feeds both, but they accept disjoint
 * settings — OTP1 has `maxWalkDistance` and no street preferences, OTP2 the reverse — so what OTP1
 * sends is pinned exactly, key set included, rather than asserted field by field.
 */
class Otp1PlanParametersTest {

    @Test
    fun aFullRequestCarriesExactlyTheOtp1Parameters() {
        assertEquals(
            mapOf(
                "fromPlace" to "47.6,-122.3",
                "toPlace" to "47.7,-122.4",
                "optimize" to "TRANSFERS",
                "wheelchair" to "true",
                "arriveBy" to "true",
                "date" to "07-26-2026",
                "time" to "8:30am",
                "showIntermediateStops" to "true",
                "maxWalkDistance" to "1600.0",
                "mode" to "TRANSIT,WALK"
            ),
            parameters(maxWalkDistanceMeters = 1600.0, modeString = "TRANSIT,WALK")
        )
    }

    /**
     * The street preferences are OTP2-only and must not appear here under any name. OTP1 would ignore
     * an unknown query parameter silently, so a leak would look like a working setting while changing
     * nothing — and `optimize` is already spent on TRANSFERS/QUICK, so a cycling optimization sent
     * here would displace the minimize-transfers switch rather than sit alongside it.
     */
    @Test
    fun noStreetPreferenceLeaksIntoTheOtp1Request() {
        val keys = parameters(maxWalkDistanceMeters = 1600.0, modeString = "TRANSIT,WALK").keys
        assertEquals(
            setOf(
                "fromPlace",
                "toPlace",
                "optimize",
                "wheelchair",
                "arriveBy",
                "date",
                "time",
                "showIntermediateStops",
                "maxWalkDistance",
                "mode"
            ),
            keys
        )
    }

    /** Both optional parameters drop out rather than being sent empty. */
    @Test
    fun anUncappedWalkAndUnsetModeOmitTheirParameters() {
        val params = parameters(maxWalkDistanceMeters = null, modeString = null)
        assertEquals(null, params["maxWalkDistance"])
        assertEquals(null, params["mode"])
        assertEquals(8, params.size)
    }

    /** `optimize` is OTP1's single-valued knob, and minimize-transfers owns it. */
    @Test
    fun optimizeCarriesWhicheverValueTheCallerResolved() {
        assertEquals("QUICK", parameters(optimize = "QUICK")["optimize"])
        assertEquals("TRANSFERS", parameters(optimize = "TRANSFERS")["optimize"])
    }

    private fun parameters(
        optimize: String = "TRANSFERS",
        maxWalkDistanceMeters: Double? = null,
        modeString: String? = null
    ): Map<String, String> = otp1PlanParameters(
        fromPlace = "47.6,-122.3",
        toPlace = "47.7,-122.4",
        optimize = optimize,
        wheelchair = true,
        arriveBy = true,
        date = "07-26-2026",
        time = "8:30am",
        maxWalkDistanceMeters = maxWalkDistanceMeters,
        modeString = modeString
    )
}
