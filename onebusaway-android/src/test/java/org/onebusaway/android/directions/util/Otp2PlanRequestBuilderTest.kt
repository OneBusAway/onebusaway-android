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

import com.apollographql.apollo.api.Optional
import org.onebusaway.android.api.graphql.type.PlanAccessMode
import org.onebusaway.android.api.graphql.type.PlanDirectMode
import org.onebusaway.android.api.graphql.type.PlanEgressMode
import org.onebusaway.android.api.graphql.type.TransitMode
import org.onebusaway.android.ui.tripplan.TripModes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [Otp2PlanRequestBuilder.buildModes] — the OTP2 `PlanModesInput` sibling of
 * [TripRequestBuilder.setModeSetById]'s OTP1 mode-string mapping (#1780). A plain JVM unit test
 * (mirrors `ModeStringRequestsBikeRentalTest`'s style): `buildModes` takes `bikeshareEnabled`
 * directly rather than a `Context`, so no Robolectric/DI is needed.
 */
class Otp2PlanRequestBuilderTest {

    @Test
    fun transitOnlyLeavesModesUnset() {
        // The schema's own default (all transit modes, WALK access/egress) already matches
        // TRANSIT_ONLY's OTP1 semantics — nothing to express.
        assertEquals(Optional.Absent, Otp2PlanRequestBuilder.buildModes(TripModes.TRANSIT_ONLY, bikeshareEnabled = true))
    }

    @Test
    fun busOnlyRequestsOnlyBusTransitMode() {
        val modes = requirePresent(Otp2PlanRequestBuilder.buildModes(TripModes.BUS_ONLY, bikeshareEnabled = false))
        val transit = requirePresent(modes.transit)
        val transitModes = requirePresent(transit.transit).map { it.mode }
        assertEquals(listOf(TransitMode.BUS), transitModes)
    }

    @Test
    fun railOnlyRequestsRailAndTram() {
        val modes = requirePresent(Otp2PlanRequestBuilder.buildModes(TripModes.RAIL_ONLY, bikeshareEnabled = false))
        val transit = requirePresent(modes.transit)
        val transitModes = requirePresent(transit.transit).map { it.mode }
        assertEquals(listOf(TransitMode.RAIL, TransitMode.TRAM), transitModes)
    }

    @Test
    fun transitAndBikeRequestsBicycleRentalAccessEgressWhenBikeshareEnabled() {
        val modes = requirePresent(
            Otp2PlanRequestBuilder.buildModes(TripModes.TRANSIT_AND_BIKE, bikeshareEnabled = true)
        )
        val transit = requirePresent(modes.transit)
        assertEquals(listOf(PlanAccessMode.BICYCLE_RENTAL), requirePresent(transit.access))
        assertEquals(listOf(PlanEgressMode.BICYCLE_RENTAL), requirePresent(transit.egress))
    }

    @Test
    fun transitAndBikeFallsBackToUnsetWhenBikeshareDisabled() {
        // Mirrors setModeSetById's own fallback: TRANSIT_AND_BIKE without bikeshare == TRANSIT_ONLY.
        assertEquals(
            Optional.Absent,
            Otp2PlanRequestBuilder.buildModes(TripModes.TRANSIT_AND_BIKE, bikeshareEnabled = false),
        )
    }

    @Test
    fun bikeshareRequestsDirectBicycleRentalOnly() {
        val modes = requirePresent(Otp2PlanRequestBuilder.buildModes(TripModes.BIKESHARE, bikeshareEnabled = true))
        assertEquals(listOf(PlanDirectMode.BICYCLE_RENTAL), requirePresent(modes.direct))
        assertEquals(true, requirePresent(modes.directOnly))
    }

    @Test
    fun invalidModeIdLeavesModesUnset() {
        assertEquals(Optional.Absent, Otp2PlanRequestBuilder.buildModes(-1, bikeshareEnabled = true))
    }

    /** Unwraps an [Optional.Present]'s non-null value, failing the test on [Optional.Absent] or a
     * present-but-null value (mirrors `dataOrThrow` elsewhere: absent-when-a-value-was-expected is a
     * test bug, not something to null-check around). */
    private fun <T : Any> requirePresent(optional: Optional<T?>): T =
        (optional as Optional.Present<T?>).value!!
}
