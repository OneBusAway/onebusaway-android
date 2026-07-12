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
 * Covers [Otp2PlanRequestBuilder.buildModes]/[Otp2PlanRequestBuilder.buildPreferences] — the OTP2
 * `PlanModesInput`/`PlanPreferencesInput` siblings of [TripRequestBuilder.setModeSetById]'s OTP1
 * mode-string mapping and the wheelchair/`optimize=TRANSFERS` request params (#1780). A plain JVM
 * unit test (mirrors `ModeStringRequestsBikeRentalTest`'s style): both take plain booleans rather
 * than a `Context`, so no Robolectric/DI is needed.
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
        // WALK must accompany BICYCLE_RENTAL — OTP2 rejects a bare BICYCLE_RENTAL leg (#1780).
        assertEquals(listOf(PlanAccessMode.WALK, PlanAccessMode.BICYCLE_RENTAL), requirePresent(transit.access))
        assertEquals(listOf(PlanEgressMode.WALK, PlanEgressMode.BICYCLE_RENTAL), requirePresent(transit.egress))
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
        // WALK must accompany BICYCLE_RENTAL — OTP2 rejects a bare BICYCLE_RENTAL leg (#1780).
        assertEquals(listOf(PlanDirectMode.WALK, PlanDirectMode.BICYCLE_RENTAL), requirePresent(modes.direct))
        assertEquals(true, requirePresent(modes.directOnly))
    }

    @Test
    fun invalidModeIdLeavesModesUnset() {
        assertEquals(Optional.Absent, Otp2PlanRequestBuilder.buildModes(-1, bikeshareEnabled = true))
    }

    @Test
    fun preferencesCarryTheWheelchairFlagEitherWay() {
        val enabled = requirePresent(
            requirePresent(
                Otp2PlanRequestBuilder.buildPreferences(wheelchairAccessible = true, optimizeTransfers = false).accessibility
            ).wheelchair
        ).enabled
        assertEquals(true, requirePresent(enabled))

        val disabled = requirePresent(
            requirePresent(
                Otp2PlanRequestBuilder.buildPreferences(wheelchairAccessible = false, optimizeTransfers = false).accessibility
            ).wheelchair
        ).enabled
        assertEquals(false, requirePresent(disabled))
    }

    @Test
    fun optimizeTransfersSetsTheHistoricalOtp1TransferCost() {
        val prefs = Otp2PlanRequestBuilder.buildPreferences(wheelchairAccessible = false, optimizeTransfers = true)
        val transferCost = requirePresent(requirePresent(requirePresent(prefs.transit).transfer).cost)
        // 1800s (30 min) is what OTP1's optimize=TRANSFERS actually added to transferPenalty —
        // see the sourced comment on Otp2PlanRequestBuilder.OPTIMIZE_TRANSFERS_COST_SECONDS.
        assertEquals(1800, transferCost)
    }

    @Test
    fun defaultTransfersLeaveTransitPreferencesUnset() {
        val prefs = Otp2PlanRequestBuilder.buildPreferences(wheelchairAccessible = false, optimizeTransfers = false)
        assertEquals(Optional.Absent, prefs.transit)
    }

    /** Unwraps an [Optional.Present]'s non-null value, failing the test on [Optional.Absent] or a
     * present-but-null value (mirrors `dataOrThrow` elsewhere: absent-when-a-value-was-expected is a
     * test bug, not something to null-check around). */
    private fun <T : Any> requirePresent(optional: Optional<T?>): T =
        (optional as Optional.Present<T?>).value!!
}
