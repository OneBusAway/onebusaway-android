/*
 * Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com)
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

/**
 * What the rider is willing to *ride*, paired with [StreetMode] to describe a whole trip.
 *
 * The two are genuinely independent — how you reach a stop says nothing about which vehicles you'll
 * board — so they are chosen separately rather than enumerated as a flat list of combinations. That
 * also reaches trips the old single list could not express at all: rail plus your own bike, bus plus
 * bikeshare, or a plain walking route.
 */
enum class VehicleMode {
    /** No transit at all — a direct street trip, walked or ridden end to end. */
    NONE,

    /** Any transit mode the region runs; the server's own default. */
    ALL_TRANSIT,
    BUS,

    /** Rail, including light rail (OTP models those as separate `RAIL` and `TRAM` modes). */
    RAIL
}

/**
 * How the rider covers the street portions — reaching the first stop, transferring, and leaving the
 * last one — or the whole trip when [VehicleMode.NONE].
 *
 * The three are mutually exclusive from the router's point of view, and each maps to a different
 * OTP2 access/egress shape with its own rules; see `Otp2PlanRequestBuilder.buildModes`.
 */
enum class StreetMode {
    /** On foot. OTP's own default, so it sends nothing. */
    WALK,

    /** On foot plus a hired bike where one is available. Requires a rental network in the region. */
    WALK_AND_BIKESHARE,

    /** The rider's own bicycle, carried for the whole journey. OTP2-only. */
    BICYCLE;

    /** Whether a plan in this mode can contain a bike leg, and so has bike preferences worth showing. */
    val usesBike: Boolean get() = this != WALK

    /**
     * Whether a region can serve this mode: bikeshare needs a rental network, and the rider's own
     * bike is expressible only in the OTP2 request shape (see `Otp2PlanRequestBuilder.buildModes`;
     * OTP1's flat mode list has no verified equivalent). [VehicleMode] has no such constraint — every
     * region has transit, and "no transit" is always a valid ask.
     *
     * The single statement of this rule. The mode picker filters its options with it, and every
     * request path degrades through [TripModeSelection.availableIn] rather than restating it.
     */
    fun isAvailableIn(bikeshareEnabled: Boolean, usesOtp2: Boolean): Boolean = when (this) {
        WALK -> true
        WALK_AND_BIKESHARE -> bikeshareEnabled
        BICYCLE -> usesOtp2
    }
}

/**
 * The rider's full mode choice. Kept as one type so it can be threaded, persisted, and degraded as a
 * unit (see [availableIn]).
 */
data class TripModeSelection(
    val vehicle: VehicleMode = VehicleMode.ALL_TRANSIT,
    val street: StreetMode = StreetMode.WALK
) {
    companion object {

        /**
         * The selection behind a legacy `preference_trip_plan_travel_by` value — the flat mode id this
         * pair replaced. Read-only migration: [AdvancedSettingsRepository] falls back to it when the
         * new preferences are absent, and the next save writes the new keys, so a rider's existing
         * choice survives the upgrade without a migration step that could itself go wrong.
         *
         * The ids are the former `TripModes` constants — the only values a stored preference can hold —
         * kept here rather than left scattered as bare numbers: 0 transit+bikeshare, 1 bus, 2 rail,
         * 3 bikeshare-only, 4 transit-only. Own bike has no legacy id: it is new in this split, so no
         * rider can have chosen it before.
         */
        fun fromLegacyModeId(modeId: Int): TripModeSelection = when (modeId) {
            0 -> TripModeSelection(VehicleMode.ALL_TRANSIT, StreetMode.WALK_AND_BIKESHARE)
            1 -> TripModeSelection(VehicleMode.BUS, StreetMode.WALK)
            2 -> TripModeSelection(VehicleMode.RAIL, StreetMode.WALK)
            3 -> TripModeSelection(VehicleMode.NONE, StreetMode.WALK_AND_BIKESHARE)
            // 4 (transit-only) and anything unrecognized land on the default pair, which is what
            // transit-only meant.
            else -> TripModeSelection()
        }
    }

    /**
     * This selection as the region can serve it: a street mode it can't ([StreetMode.isAvailableIn])
     * degrades to walking, which is what the server would do anyway.
     *
     * Applied on the way to a request, never by rewriting the stored preference — so a rider who picks
     * their own bike at home and then travels through an OTP1 region still has it when they come back.
     */
    fun availableIn(bikeshareEnabled: Boolean, usesOtp2: Boolean): TripModeSelection = if (street.isAvailableIn(bikeshareEnabled, usesOtp2)) this else copy(street = StreetMode.WALK)
}
