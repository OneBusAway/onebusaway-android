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
package org.onebusaway.android.ui.tripresults

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.time.WallTime

/**
 * Covers the assembly of the rental link the app builds itself (#2158) — the `android.net.Uri` step
 * `RentalPickupsTest` (a pure JVM test over the link's *components*) can't reach, since `Uri` is stubbed
 * off-device.
 *
 * What's at risk here is what components buy over a format string: that a vehicle id carrying a query
 * delimiter stays one parameter, and that the timestamp goes out in the unit the operator publishes.
 */
@RunWith(AndroidJUnit4::class)
class RentalDeepLinksTest {

    // Method names are camelCase, not the backtick-with-spaces style the JVM unit tests use: these get
    // dexed, and D8 rejects spaces in a SimpleName below DEX version 040.

    private val lime = RentalOperators.known("lime_seattle")!!.vehicleUri!!

    @Test
    fun limeVehicleUri_isTheShapeLimePublishes() {
        assertEquals(
            "limebike://map?selected_vehicle_id=abc123&generated_at=1785807940",
            RentalDeepLinks.vehicleUri(
                RentalLink.Synthesized(lime, "abc123"),
                WallTime(1785807940_000L)
            ).toString()
        )
    }

    @Test
    fun timestamp_isEpochSecondsAndTruncates() {
        // Epoch seconds, not millis: the stamp is a whole second, and the sub-second remainder is
        // dropped rather than rounded — a timestamp the operator reads as "the future" is worse than
        // one it reads as a moment ago.
        assertEquals(
            "1785807940",
            RentalDeepLinks.vehicleUri(RentalLink.Synthesized(lime, "abc123"), WallTime(1785807940_999L))
                .getQueryParameter("generated_at")
        )
    }

    @Test
    fun vehicleIdCarryingQueryDelimiters_staysOneParameter() {
        // The whole reason the link is held as components: interpolating this id into the template by
        // hand would hand Lime a `selected_vehicle_id` of "a" plus two parameters we never wrote.
        val uri = RentalDeepLinks.vehicleUri(RentalLink.Synthesized(lime, "a&b=c"), WallTime(0L))
        assertEquals("a&b=c", uri.getQueryParameter("selected_vehicle_id"))
        assertEquals(setOf("selected_vehicle_id", "generated_at"), uri.queryParameterNames)
    }

    @Test
    fun operatorWithNoTimestampParam_getsNoTimestamp() {
        val uri = RentalDeepLinks.vehicleUri(
            RentalLink.Synthesized(lime.copy(timestampParam = null), "abc123"),
            WallTime(1785807940_000L)
        )
        assertEquals("limebike://map?selected_vehicle_id=abc123", uri.toString())
    }
}
