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
import org.onebusaway.android.api.graphql.type.PlanPreferencesInput
import org.onebusaway.android.api.graphql.type.PlanStreetPreferencesInput
import org.onebusaway.android.api.graphql.type.TransitMode
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.TripModes
import org.onebusaway.android.ui.tripplan.WalkPreference

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
            requirePresent(preferences(wheelchairAccessible = true, optimizeTransfers = false).accessibility).wheelchair
        ).enabled
        assertEquals(true, requirePresent(enabled))

        val disabled = requirePresent(
            requirePresent(preferences(wheelchairAccessible = false, optimizeTransfers = false).accessibility).wheelchair
        ).enabled
        assertEquals(false, requirePresent(disabled))
    }

    @Test
    fun optimizeTransfersSetsTheHistoricalOtp1TransferCost() {
        val prefs = preferences(wheelchairAccessible = false, optimizeTransfers = true)
        val transferCost = requirePresent(requirePresent(requirePresent(prefs.transit).transfer).cost)
        // 1800s (30 min) is what OTP1's optimize=TRANSFERS actually added to transferPenalty —
        // see the sourced comment on Otp2PlanRequestBuilder.OPTIMIZE_TRANSFERS_COST_SECONDS.
        assertEquals(1800, transferCost)
    }

    @Test
    fun defaultTransfersLeaveTransitPreferencesUnset() {
        val prefs = preferences(wheelchairAccessible = false, optimizeTransfers = false)
        assertEquals(Optional.Absent, prefs.transit)
    }

    /** [Otp2PlanRequestBuilder.buildPreferences] with the street preferences on their neutral stops. */
    private fun preferences(wheelchairAccessible: Boolean, optimizeTransfers: Boolean): PlanPreferencesInput = Otp2PlanRequestBuilder.buildPreferences(
        wheelchairAccessible = wheelchairAccessible,
        optimizeTransfers = optimizeTransfers,
        walkPreference = WalkPreference.MEDIUM,
        cyclingPreference = CyclingPreference.DEFAULT,
        bikePreference = BikePreference.MEDIUM
    )

    /**
     * The neutral stop is stated on the wire, not omitted: both reluctances carry OTP's own
     * documented 2.0 default so the five-point scales keep their ordering on a region that tuned its
     * own reluctance (see `Otp2PlanRequestBuilder.RELUCTANCE_MEDIUM`). `bicycle.optimization` is the
     * one piece still left unset at its default stop — it is an "unset" option, not a midpoint, so
     * there is no ordering for a region's own value to invert.
     */
    @Test
    fun theNeutralStopStatesOtpsOwnDefaultsAndLeavesOptimizationUnset() {
        val street = street()
        assertEquals(2.0, requirePresent(requirePresent(street.walk).reluctance), 1e-9)
        val bicycle = requirePresent(street.bicycle)
        assertEquals(2.0, requirePresent(bicycle.reluctance), 1e-9)
        assertEquals(Optional.Absent, bicycle.optimization)
    }

    /**
     * The scale is 8 / 4 / 2 / 1.4 / 1.0 — OTP2's documented 2.0 default at the neutral stop, stepped
     * by a factor of two above it and split geometrically down to the 1.0 floor below it. Pinned
     * exactly because these are the numbers that actually reach the server.
     */
    @Test
    fun walkPreferenceWalksTheFivePointReluctanceScale() {
        assertEquals(8.0, walkReluctanceFor(WalkPreference.MINIMUM), 1e-9)
        assertEquals(4.0, walkReluctanceFor(WalkPreference.LOW), 1e-9)
        assertEquals(2.0, walkReluctanceFor(WalkPreference.MEDIUM), 1e-9)
        assertEquals(1.4, walkReluctanceFor(WalkPreference.HIGH), 1e-9)
        assertEquals(1.0, walkReluctanceFor(WalkPreference.MAXIMUM), 1e-9)
    }

    /** Same table for cycling — OTP documents the same 2.0 default for both. */
    @Test
    fun bikePreferenceWalksTheSameFivePointScale() {
        assertEquals(8.0, bikeReluctanceFor(BikePreference.MINIMUM), 1e-9)
        assertEquals(4.0, bikeReluctanceFor(BikePreference.LOW), 1e-9)
        assertEquals(2.0, bikeReluctanceFor(BikePreference.MEDIUM), 1e-9)
        assertEquals(1.4, bikeReluctanceFor(BikePreference.HIGH), 1e-9)
        assertEquals(1.0, bikeReluctanceFor(BikePreference.MAXIMUM), 1e-9)
    }

    /**
     * The scale is inverted on purpose — *less* of the mode is a *higher* multiplier — and it must
     * decrease **strictly**, across *every* stop including the neutral one. Two things ride on that:
     * two slider positions must never send the same value, and no stop may be out of order with its
     * neighbours (the reason the neutral stop states its multiplier rather than deferring to whatever
     * the region configured, which could sit anywhere on or off the scale).
     *
     * Every stop must also stay strictly inside the schema's "should be greater than 0" contract. A 0
     * would make street time free, letting an arbitrarily long walk beat any transit itinerary.
     */
    @Test
    fun bothScalesDecreaseStrictlyAcrossEveryStopAndStayAboveZero() {
        for (scale in listOf(walkScale(), bikeScale())) {
            assertEquals("every stop must be on the wire", 5, scale.size)
            assertTrue("every stop must stay > 0", scale.all { it > 0.0 })
            assertTrue(
                "more of the mode must always cost strictly less: $scale",
                scale.zipWithNext().all { (higher, lower) -> higher > lower }
            )
        }
    }

    /**
     * No stop may drop below 1.0, OTP's neutral point. Below it the schema says the mode is
     * "preferred over transit", and OTP acts on that by deleting transit itineraries — measured
     * against a live server, rail + own bike returns nothing at 0.9 and a real itinerary at 1.0.
     * This is stricter than the scalar's own 0.1 validation floor, and unlike that one it fails
     * silently, as an empty result rather than an error.
     */
    @Test
    fun noStopAsksTheRouterToPreferStreetOverTransit() {
        assertTrue(
            "every reluctance must be >= 1.0 (OTP's neutral point)",
            (walkScale() + bikeScale()).all { it >= 1.0 }
        )
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
    @Test
    fun everyStopIsInsideTheRangeTheServerAccepts() {
        val all = walkScale() + bikeScale()
        assertEquals("all five stops of both scales", 10, all.size)
        assertTrue("every reluctance must be >= 0.1", all.all { it >= 0.1 })
        assertTrue("every reluctance must be <= 100000.0", all.all { it <= 100_000.0 })
    }

    @Test
    fun cyclingPreferenceMapsToTheOtpOptimizationType() {
        assertEquals(CyclingOptimizationType.SHORTEST_DURATION, cyclingOptimizationFor(CyclingPreference.FASTEST))
        assertEquals(CyclingOptimizationType.SAFEST_STREETS, cyclingOptimizationFor(CyclingPreference.SAFEST))
        assertEquals(CyclingOptimizationType.FLAT_STREETS, cyclingOptimizationFor(CyclingPreference.FLATTEST))
    }

    /**
     * The walk and bicycle halves travel independently: choosing a cycling optimization must not
     * disturb the walk reluctance, and choosing a walk reluctance must not invent an optimization the
     * rider never picked.
     */
    @Test
    fun theWalkAndBicycleHalvesTravelIndependently() {
        val cyclingOnly = street(cyclingPreference = CyclingPreference.FASTEST)
        assertEquals(2.0, requirePresent(requirePresent(cyclingOnly.walk).reluctance), 1e-9)
        assertEquals(
            CyclingOptimizationType.SHORTEST_DURATION,
            requirePresent(requirePresent(requirePresent(cyclingOnly.bicycle).optimization).type)
        )

        val walkOnly = street(walkPreference = WalkPreference.MINIMUM)
        assertEquals(8.0, requirePresent(requirePresent(walkOnly.walk).reluctance), 1e-9)
        assertEquals(Optional.Absent, requirePresent(walkOnly.bicycle).optimization)
    }

    /** The two bicycle knobs share one `BicyclePreferencesInput`, so both must fit in it at once. */
    @Test
    fun bothBicycleSettingsRideInOneInputWhenBothAreSet() {
        val bicycle = requirePresent(
            street(
                cyclingPreference = CyclingPreference.FLATTEST,
                bikePreference = BikePreference.MINIMUM
            ).bicycle
        )
        assertEquals(8.0, requirePresent(bicycle.reluctance), 1e-9)
        assertEquals(
            CyclingOptimizationType.FLAT_STREETS,
            requirePresent(requirePresent(bicycle.optimization).type)
        )
    }

    /** Both reluctance scales, ordered least → most of the mode, as they reach the server. */
    private fun walkScale(): List<Double> = WalkPreference.entries.map { walkReluctanceFor(it) }

    private fun bikeScale(): List<Double> = BikePreference.entries.map { bikeReluctanceFor(it) }

    private fun walkReluctanceFor(preference: WalkPreference): Double = requirePresent(requirePresent(street(walkPreference = preference).walk).reluctance)

    private fun bikeReluctanceFor(preference: BikePreference): Double = requirePresent(requirePresent(street(bikePreference = preference).bicycle).reluctance)

    private fun cyclingOptimizationFor(preference: CyclingPreference): CyclingOptimizationType = requirePresent(
        requirePresent(requirePresent(street(cyclingPreference = preference).bicycle).optimization).type
    )

    /**
     * [Otp2PlanRequestBuilder.buildStreetPreferences] with every unnamed preference on its neutral
     * stop. The neutral defaults live here rather than on the production function, so a real call
     * site that forgets a preference fails to compile instead of silently sending neutral.
     */
    private fun street(
        walkPreference: WalkPreference = WalkPreference.MEDIUM,
        cyclingPreference: CyclingPreference = CyclingPreference.DEFAULT,
        bikePreference: BikePreference = BikePreference.MEDIUM
    ): PlanStreetPreferencesInput = requirePresent(
        Otp2PlanRequestBuilder.buildStreetPreferences(walkPreference, cyclingPreference, bikePreference)
    )

    /** Unwraps an [Optional.Present]'s non-null value, failing the test on [Optional.Absent] or a
     * present-but-null value (mirrors `dataOrThrow` elsewhere: absent-when-a-value-was-expected is a
     * test bug, not something to null-check around). */
    private fun <T : Any> requirePresent(optional: Optional<T?>): T = (optional as Optional.Present<T?>).value!!
}
