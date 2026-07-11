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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [modeStringRequestsBikeRental], which [DefaultTripPlanRepository]'s pre-1.0-server URL
 * rewrite uses to detect a bike-rental request. Before #1778 this came from the vendored
 * `Request.bikeRental` field, which nothing in this app ever set (bikeshare mode has always been
 * requested via the `BICYCLE_RENT` mode-string token instead), so the rewrite had been dead since the
 * bikeshare feature shipped in 2017. This pins the fix: the token's presence in the built mode string
 * is now the source of truth.
 */
class ModeStringRequestsBikeRentalTest {

    @Test
    fun bikeRentalModeIsDetected() {
        assertTrue(modeStringRequestsBikeRental("TRANSIT,WALK,BICYCLE_RENT", "BICYCLE_RENT"))
        assertTrue(modeStringRequestsBikeRental("BICYCLE_RENT", "BICYCLE_RENT"))
    }

    @Test
    fun nonBikeRentalModesAreNotDetected() {
        assertFalse(modeStringRequestsBikeRental("TRANSIT,WALK", "BICYCLE_RENT"))
        assertFalse(modeStringRequestsBikeRental("BUS,WALK", "BICYCLE_RENT"))
        assertFalse(modeStringRequestsBikeRental(null, "BICYCLE_RENT"))
    }
}
