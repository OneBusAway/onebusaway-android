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
package org.onebusaway.android.directions.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.ui.tripplan.StreetMode
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.VehicleMode

/**
 * Covers [otp1ModeTokens] — the OTP1 half of the vehicle/street mode split, and the sibling of
 * [Otp2PlanRequestBuilder.buildModes] that `Otp2PlanRequestBuilderTest` pins.
 *
 * The claim under test is a compatibility one: every selection the old flat mode list could express
 * must still produce the *exact* mode string this app sent before the split, so no OTP1 region sees a
 * changed request. A plain JVM unit test — the mapping takes the bike-rental token and the bikeshare
 * flag rather than a `Context`, so no Robolectric/DI is needed.
 */
class TripRequestBuilderTest {

    /** The wire token `R.string.traverse_mode_bicycle_rent` resolves to. */
    private val bikeRental = "BICYCLE_RENT"

    private fun tokens(
        vehicle: VehicleMode,
        street: StreetMode,
        bikeshareEnabled: Boolean = true
    ) = otp1ModeTokens(TripModeSelection(vehicle, street), bikeRental, bikeshareEnabled).joinToString(",")

    /**
     * The five selections the pre-split dialog could express, against the exact strings the flat
     * mode list built. Any drift here changes a live OTP1 region's request.
     */
    @Test
    fun theOldModeListsStringsAreReproducedExactly() {
        assertEquals("TRANSIT,WALK", tokens(VehicleMode.ALL_TRANSIT, StreetMode.WALK))
        assertEquals("BUS,WALK", tokens(VehicleMode.BUS, StreetMode.WALK))
        assertEquals("RAIL,TRAM,WALK", tokens(VehicleMode.RAIL, StreetMode.WALK))
        assertEquals("TRANSIT,WALK,BICYCLE_RENT", tokens(VehicleMode.ALL_TRANSIT, StreetMode.WALK_AND_BIKESHARE))
        // A direct bikeshare trip historically sent the rental token alone, with no WALK — the one
        // non-compositional case, preserved deliberately rather than "fixed" here.
        assertEquals(bikeRental, tokens(VehicleMode.NONE, StreetMode.WALK_AND_BIKESHARE))
    }

    /** Mirrors the old flat list's fallback: a bikeshare pick with no rental network walks. */
    @Test
    fun bikeshareWithoutARentalNetworkFallsBackToWalking() {
        assertEquals(
            tokens(VehicleMode.ALL_TRANSIT, StreetMode.WALK),
            tokens(VehicleMode.ALL_TRANSIT, StreetMode.WALK_AND_BIKESHARE, bikeshareEnabled = false)
        )
        // ...including the direct case, which then has no rental token to return alone.
        assertEquals("WALK", tokens(VehicleMode.NONE, StreetMode.WALK_AND_BIKESHARE, bikeshareEnabled = false))
    }

    /**
     * Own bike has no verified OTP1 equivalent, so it walks. The dialog only offers it on OTP2 and
     * [TripModeSelection.availableIn] already degrades it — this is the belt to that braces.
     */
    @Test
    fun ownBikeWalksOnOtp1() {
        assertEquals("TRANSIT,WALK", tokens(VehicleMode.ALL_TRANSIT, StreetMode.BICYCLE))
        assertEquals("WALK", tokens(VehicleMode.NONE, StreetMode.BICYCLE))
    }

    /** Combinations the old flat list could not express compose into the obvious token lists. */
    @Test
    fun theTwoHalvesCompose() {
        assertEquals("BUS,WALK,BICYCLE_RENT", tokens(VehicleMode.BUS, StreetMode.WALK_AND_BIKESHARE))
        assertEquals("RAIL,TRAM,WALK,BICYCLE_RENT", tokens(VehicleMode.RAIL, StreetMode.WALK_AND_BIKESHARE))
        // No transit at all, on foot — a plain walking route, which the old list had no entry for.
        assertEquals("WALK", tokens(VehicleMode.NONE, StreetMode.WALK))
    }
}
