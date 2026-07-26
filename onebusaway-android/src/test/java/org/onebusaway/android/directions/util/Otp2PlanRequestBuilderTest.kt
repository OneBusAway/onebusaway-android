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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.graphql.type.CyclingOptimizationType
import org.onebusaway.android.api.graphql.type.PlanAccessMode
import org.onebusaway.android.api.graphql.type.PlanDirectMode
import org.onebusaway.android.api.graphql.type.PlanEgressMode
import org.onebusaway.android.api.graphql.type.TransitMode
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.TripModes
import org.onebusaway.android.ui.tripplan.WalkPreference
import org.onebusaway.android.ui.tripplan.enumValueOrDefault

/**
 * Covers [Otp2PlanRequestBuilder.buildModes]/[Otp2PlanRequestBuilder.buildPreferences]/
 * [Otp2PlanRequestBuilder.buildStreetPreferences] — the OTP2 `PlanModesInput`/`PlanPreferencesInput`
 * siblings of [TripRequestBuilder.setModeSetById]'s OTP1 mode-string mapping and the
 * wheelchair/`optimize=TRANSFERS` request params (#1780), plus the walk/cycling street preferences.
 * A plain JVM unit test (mirrors `ModeStringRequestsBikeRentalTest`'s style): they take plain
 * booleans and enums rather than a `Context`, so no Robolectric/DI is needed.
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
            Otp2PlanRequestBuilder.buildModes(TripModes.TRANSIT_AND_BIKE, bikeshareEnabled = false)
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

    @Test
    fun defaultStreetPreferencesAreOmittedEntirely() {
        // Both defaults means "let the region's own router-config decide" — send no `street` block
        // at all rather than an empty one, which is also what this app sent before these settings
        // existed.
        assertEquals(
            Optional.Absent,
            Otp2PlanRequestBuilder.buildStreetPreferences(WalkPreference.MEDIUM, CyclingPreference.DEFAULT)
        )
        val prefs = Otp2PlanRequestBuilder.buildPreferences(wheelchairAccessible = false, optimizeTransfers = false)
        assertEquals(Optional.Absent, prefs.street)
    }

    /**
     * The scale is 8 / 4 / (unset) / 1.4 / 1.0 — OTP2's documented 2.0 default at the neutral stop,
     * stepped by a factor of two above it and split geometrically down to the 1.0 floor below it.
     * Pinned exactly because these are the numbers that actually reach the server.
     */
    @Test
    fun walkPreferenceWalksTheFivePointReluctanceScale() {
        assertEquals(8.0, walkReluctanceFor(WalkPreference.MINIMUM), 1e-9)
        assertEquals(4.0, walkReluctanceFor(WalkPreference.LOW), 1e-9)
        assertEquals(1.4, walkReluctanceFor(WalkPreference.HIGH), 1e-9)
        assertEquals(1.0, walkReluctanceFor(WalkPreference.MAXIMUM), 1e-9)
    }

    /** Same table for cycling — OTP documents the same 2.0 default for both. */
    @Test
    fun bikePreferenceWalksTheSameFivePointScale() {
        assertEquals(8.0, bikeReluctanceFor(BikePreference.MINIMUM), 1e-9)
        assertEquals(4.0, bikeReluctanceFor(BikePreference.LOW), 1e-9)
        assertEquals(1.4, bikeReluctanceFor(BikePreference.HIGH), 1e-9)
        assertEquals(1.0, bikeReluctanceFor(BikePreference.MAXIMUM), 1e-9)
    }

    /**
     * Every stop must land inside the range OTP's `Reluctance` scalar actually accepts. The bound is
     * NOT in the vendored schema (its doc comment only says "should be greater than 0") — it was
     * found by a live server rejecting 0.08:
     *
     *   "... is not a valid 'Reluctance' - Reluctance needs to be between 0.1 and 100000.0"
     *
     * An out-of-range value fails the whole plan with a GraphQL validation error, so this guards
     * anyone re-deriving the scale (e.g. changing RELUCTANCE_STEP) from silently breaking planning.
     */

    /**
     * No stop may drop below 1.0, OTP's neutral point. Below it the schema says the mode is
     * "preferred over transit", and OTP acts on that by deleting transit itineraries — measured
     * against a live server, rail + own bike returns nothing at 0.9 and a real itinerary at 1.0.
     * This is stricter than the scalar's own 0.1 validation floor, and unlike that one it fails
     * silently, as an empty result rather than an error.
     */
    @Test
    fun noStopAsksTheRouterToPreferStreetOverTransit() {
        val all = WalkPreference.entries.mapNotNull { walkReluctanceFor2(it) } +
            BikePreference.entries.mapNotNull { bikeReluctanceFor2(it) }
        assertTrue("every reluctance must be >= 1.0 (OTP's neutral point)", all.all { it >= 1.0 })
    }

    @Test
    fun everyStopIsInsideTheRangeTheServerAccepts() {
        val all = WalkPreference.entries.mapNotNull { walkReluctanceFor2(it) } +
            BikePreference.entries.mapNotNull { bikeReluctanceFor2(it) }
        assertEquals("all five stops, both scales, minus the two neutral ones", 8, all.size)
        assertTrue("every reluctance must be >= 0.1", all.all { it >= 0.1 })
        assertTrue("every reluctance must be <= 100000.0", all.all { it <= 100_000.0 })
    }

    /** Null at the neutral stop (which sends nothing), else the reluctance. */
    private fun walkReluctanceFor2(preference: WalkPreference): Double? {
        val street = Otp2PlanRequestBuilder.buildStreetPreferences(preference, CyclingPreference.DEFAULT, BikePreference.MEDIUM)
        if (street == Optional.Absent) return null
        return requirePresent(requirePresent(requirePresent(street).walk).reluctance)
    }

    private fun bikeReluctanceFor2(preference: BikePreference): Double? {
        val street = Otp2PlanRequestBuilder.buildStreetPreferences(WalkPreference.MEDIUM, CyclingPreference.DEFAULT, preference)
        if (street == Optional.Absent) return null
        return requirePresent(requirePresent(requirePresent(street).bicycle).reluctance)
    }

    /**
     * The scale is inverted on purpose — *less* walking is a *higher* multiplier — and every stop
     * must stay strictly inside the schema's "should be greater than 0" contract. A 0 would make
     * street time free, letting an arbitrarily long walk beat any transit itinerary.
     */
    @Test
    fun theScaleDecreasesMonotonicallyAndStaysAboveZero() {
        val ordered = listOf(
            WalkPreference.MINIMUM,
            WalkPreference.LOW,
            WalkPreference.HIGH,
            WalkPreference.MAXIMUM
        ).map { walkReluctanceFor(it) }
        assertEquals("more walking must never cost more", ordered.sortedDescending(), ordered)
        assertTrue("every stop must stay > 0", ordered.all { it > 0.0 })
    }

    /** The neutral stop sends nothing, so a region's own router-config reluctance survives. */
    @Test
    fun theMediumStopSendsNoReluctanceAtAll() {
        assertEquals(
            Optional.Absent,
            Otp2PlanRequestBuilder.buildStreetPreferences(
                WalkPreference.MEDIUM,
                CyclingPreference.DEFAULT,
                BikePreference.MEDIUM
            )
        )
    }

    @Test
    fun defaultWalkPreferenceLeavesWalkUnsetEvenWhenCyclingIsSet() {
        // The two halves are independent: choosing a cycling optimization must not pin a walk
        // reluctance the rider never asked for.
        val street = requirePresent(
            Otp2PlanRequestBuilder.buildStreetPreferences(WalkPreference.MEDIUM, CyclingPreference.FASTEST)
        )
        assertEquals(Optional.Absent, street.walk)
        assertEquals(
            CyclingOptimizationType.SHORTEST_DURATION,
            requirePresent(requirePresent(requirePresent(street.bicycle).optimization).type)
        )
    }

    @Test
    fun defaultCyclingPreferenceLeavesBicycleUnsetEvenWhenWalkIsSet() {
        val street = requirePresent(
            Otp2PlanRequestBuilder.buildStreetPreferences(WalkPreference.MINIMUM, CyclingPreference.DEFAULT)
        )
        assertEquals(Optional.Absent, street.bicycle)
        assertEquals(8.0, requirePresent(requirePresent(street.walk).reluctance), 1e-9)
    }

    @Test
    fun cyclingPreferenceMapsToTheOtpOptimizationType() {
        assertEquals(CyclingOptimizationType.SHORTEST_DURATION, cyclingOptimizationFor(CyclingPreference.FASTEST))
        assertEquals(CyclingOptimizationType.SAFEST_STREETS, cyclingOptimizationFor(CyclingPreference.SAFEST))
        assertEquals(CyclingOptimizationType.FLAT_STREETS, cyclingOptimizationFor(CyclingPreference.FLATTEST))
    }

    @Test
    fun unknownStoredPreferenceNameFallsBackToTheServerDefault() {
        // Persisted by name, so a value written by a build that knows an option this one doesn't
        // must degrade to "send nothing" rather than to some arbitrary neighbouring option.
        assertEquals(WalkPreference.MEDIUM, enumValueOrDefault("MODERATE_WALKS", WalkPreference.MEDIUM))
        assertEquals(WalkPreference.MEDIUM, enumValueOrDefault(null, WalkPreference.MEDIUM))
        assertEquals(CyclingPreference.SAFEST, enumValueOrDefault("SAFEST", CyclingPreference.DEFAULT))
    }

    /**
     * The two bicycle knobs share one `BicyclePreferencesInput`, so each must be able to travel
     * without the other — picking a route optimization must not pin a reluctance the rider never
     * chose, and vice versa.
     */
    @Test
    fun theTwoBicycleSettingsTravelIndependently() {
        val reluctanceOnly = requirePresent(
            requirePresent(
                Otp2PlanRequestBuilder.buildStreetPreferences(
                    WalkPreference.MEDIUM,
                    CyclingPreference.DEFAULT,
                    BikePreference.MAXIMUM
                )
            ).bicycle
        )
        assertEquals(1.0, requirePresent(reluctanceOnly.reluctance), 1e-9)
        assertEquals(Optional.Absent, reluctanceOnly.optimization)

        val optimizationOnly = requirePresent(
            requirePresent(
                Otp2PlanRequestBuilder.buildStreetPreferences(
                    WalkPreference.MEDIUM,
                    CyclingPreference.SAFEST,
                    BikePreference.MEDIUM
                )
            ).bicycle
        )
        assertEquals(Optional.Absent, optimizationOnly.reluctance)
        assertEquals(
            CyclingOptimizationType.SAFEST_STREETS,
            requirePresent(requirePresent(optimizationOnly.optimization).type)
        )
    }

    @Test
    fun bothBicycleSettingsRideInOneInputWhenBothAreSet() {
        val bicycle = requirePresent(
            requirePresent(
                Otp2PlanRequestBuilder.buildStreetPreferences(
                    WalkPreference.MEDIUM,
                    CyclingPreference.FLATTEST,
                    BikePreference.MINIMUM
                )
            ).bicycle
        )
        assertEquals(8.0, requirePresent(bicycle.reluctance), 1e-9)
        assertEquals(
            CyclingOptimizationType.FLAT_STREETS,
            requirePresent(requirePresent(bicycle.optimization).type)
        )
    }

    @Test
    fun allThreeDefaultsStillOmitStreetPreferencesEntirely() {
        assertEquals(
            Optional.Absent,
            Otp2PlanRequestBuilder.buildStreetPreferences(
                WalkPreference.MEDIUM,
                CyclingPreference.DEFAULT,
                BikePreference.MEDIUM
            )
        )
    }

    private fun bikeReluctanceFor(preference: BikePreference): Double {
        val street = requirePresent(
            Otp2PlanRequestBuilder.buildStreetPreferences(WalkPreference.MEDIUM, CyclingPreference.DEFAULT, preference)
        )
        return requirePresent(requirePresent(street.bicycle).reluctance)
    }

    private fun walkReluctanceFor(preference: WalkPreference): Double {
        val street = requirePresent(Otp2PlanRequestBuilder.buildStreetPreferences(preference, CyclingPreference.DEFAULT))
        return requirePresent(requirePresent(street.walk).reluctance)
    }

    private fun cyclingOptimizationFor(preference: CyclingPreference): CyclingOptimizationType {
        val street = requirePresent(Otp2PlanRequestBuilder.buildStreetPreferences(WalkPreference.MEDIUM, preference))
        return requirePresent(requirePresent(requirePresent(street.bicycle).optimization).type)
    }

    /** Unwraps an [Optional.Present]'s non-null value, failing the test on [Optional.Absent] or a
     * present-but-null value (mirrors `dataOrThrow` elsewhere: absent-when-a-value-was-expected is a
     * test bug, not something to null-check around). */
    private fun <T : Any> requirePresent(optional: Optional<T?>): T = (optional as Optional.Present<T?>).value!!
}
