/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.tripplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the rider's mode choice itself — which street modes a region can serve
 * ([StreetMode.isAvailableIn]), how an unservable one degrades ([TripModeSelection.availableIn]), and
 * the read-only migration from the flat mode id this pair replaced.
 *
 * These are the rules the request builders *consume*, so they live here rather than in either
 * protocol's test class: both `Otp2PlanRequestBuilder.buildModes` and `otp1ModeTokens` take a
 * selection that has already been through them.
 */
class TripModeSelectionTest {

    /** Availability is per street mode; the vehicle half is always offerable. */
    @Test
    fun streetModeAvailabilityFollowsTheRegionsCapabilities() {
        assertTrue(StreetMode.WALK.isAvailableIn(bikeshareEnabled = false, usesOtp2 = false))
        // Bikeshare needs a rental network, whichever protocol the region speaks.
        assertFalse(StreetMode.WALK_AND_BIKESHARE.isAvailableIn(bikeshareEnabled = false, usesOtp2 = true))
        assertTrue(StreetMode.WALK_AND_BIKESHARE.isAvailableIn(bikeshareEnabled = true, usesOtp2 = false))
        // The rider's own bike needs OTP2, and no rental network.
        assertFalse(StreetMode.BICYCLE.isAvailableIn(bikeshareEnabled = true, usesOtp2 = false))
        assertTrue(StreetMode.BICYCLE.isAvailableIn(bikeshareEnabled = false, usesOtp2 = true))
    }

    /**
     * A street mode the region can't serve degrades on the way to a request. The vehicle half is
     * untouched, and — the point of degrading here rather than rewriting the preference — so is the
     * original selection.
     */
    @Test
    fun anUnservableStreetModeDegradesToWalking() {
        val ownBike = TripModeSelection(VehicleMode.RAIL, StreetMode.BICYCLE)
        assertEquals(
            TripModeSelection(VehicleMode.RAIL, StreetMode.WALK),
            ownBike.availableIn(bikeshareEnabled = true, usesOtp2 = false)
        )
        assertEquals(ownBike, ownBike.availableIn(bikeshareEnabled = false, usesOtp2 = true))

        val bikeshare = TripModeSelection(VehicleMode.BUS, StreetMode.WALK_AND_BIKESHARE)
        assertEquals(
            TripModeSelection(VehicleMode.BUS, StreetMode.WALK),
            bikeshare.availableIn(bikeshareEnabled = false, usesOtp2 = true)
        )
        assertEquals(bikeshare, bikeshare.availableIn(bikeshareEnabled = true, usesOtp2 = false))
    }

    /** A rider's saved flat mode id must survive the split rather than resetting their choice. */
    @Test
    fun legacyModeIdsMigrateToTheEquivalentPair() {
        assertEquals(TripModeSelection(VehicleMode.ALL_TRANSIT, StreetMode.WALK_AND_BIKESHARE), TripModeSelection.fromLegacyModeId(0))
        assertEquals(TripModeSelection(VehicleMode.BUS, StreetMode.WALK), TripModeSelection.fromLegacyModeId(1))
        assertEquals(TripModeSelection(VehicleMode.RAIL, StreetMode.WALK), TripModeSelection.fromLegacyModeId(2))
        assertEquals(TripModeSelection(VehicleMode.NONE, StreetMode.WALK_AND_BIKESHARE), TripModeSelection.fromLegacyModeId(3))
        assertEquals(TripModeSelection(VehicleMode.ALL_TRANSIT, StreetMode.WALK), TripModeSelection.fromLegacyModeId(4))
        // 0-4 were the only TripModes constants, so anything else is a value no rider can have
        // stored — including own bike, which is new in this split and has no legacy id.
        assertEquals("an unknown id lands on the default pair", TripModeSelection(), TripModeSelection.fromLegacyModeId(-1))
        assertEquals("...as does one past the end of the old list", TripModeSelection(), TripModeSelection.fromLegacyModeId(5))
    }

    /** Bike preferences are worth showing for exactly the modes that can produce a bike leg. */
    @Test
    fun onlyTheBikeModesUseABike() {
        assertFalse(StreetMode.WALK.usesBike)
        assertTrue(StreetMode.WALK_AND_BIKESHARE.usesBike)
        assertTrue(StreetMode.BICYCLE.usesBike)
    }
}
