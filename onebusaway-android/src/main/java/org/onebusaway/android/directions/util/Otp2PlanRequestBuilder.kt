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

import android.content.Context
import com.apollographql.apollo.api.Optional
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.onebusaway.android.api.graphql.PlanQuery
import org.onebusaway.android.api.graphql.type.AccessibilityPreferencesInput
import org.onebusaway.android.api.graphql.type.BicyclePreferencesInput
import org.onebusaway.android.api.graphql.type.CyclingOptimizationInput
import org.onebusaway.android.api.graphql.type.CyclingOptimizationType
import org.onebusaway.android.api.graphql.type.PlanAccessMode
import org.onebusaway.android.api.graphql.type.PlanCoordinateInput
import org.onebusaway.android.api.graphql.type.PlanDateTimeInput
import org.onebusaway.android.api.graphql.type.PlanDirectMode
import org.onebusaway.android.api.graphql.type.PlanEgressMode
import org.onebusaway.android.api.graphql.type.PlanLabeledLocationInput
import org.onebusaway.android.api.graphql.type.PlanLocationInput
import org.onebusaway.android.api.graphql.type.PlanModesInput
import org.onebusaway.android.api.graphql.type.PlanPreferencesInput
import org.onebusaway.android.api.graphql.type.PlanStreetPreferencesInput
import org.onebusaway.android.api.graphql.type.PlanTransferMode
import org.onebusaway.android.api.graphql.type.PlanTransitModePreferenceInput
import org.onebusaway.android.api.graphql.type.PlanTransitModesInput
import org.onebusaway.android.api.graphql.type.TransferPreferencesInput
import org.onebusaway.android.api.graphql.type.TransitMode
import org.onebusaway.android.api.graphql.type.TransitPreferencesInput
import org.onebusaway.android.api.graphql.type.WalkPreferencesInput
import org.onebusaway.android.api.graphql.type.WheelchairPreferencesInput
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.StreetMode
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.VehicleMode
import org.onebusaway.android.ui.tripplan.WalkPreference
import org.onebusaway.android.util.BikeshareAvailability

/**
 * Builds the OTP 2.x GraphQL [PlanQuery] variables from a [TripRequestBuilder]'s already-parsed
 * state (#1780) — the GraphQL sibling of [TripRequestBuilder.buildRequest]. Reads the same
 * protocol-agnostic getters ([TripRequestBuilder.from]/[TripRequestBuilder.to]/
 * [TripRequestBuilder.dateTime]/[TripRequestBuilder.arriveBy]/
 * [TripRequestBuilder.getWheelchairAccessible]/[TripRequestBuilder.getOptimizeTransfers]/
 * [TripRequestBuilder.getModeSetId]) rather than duplicating request state, so both protocols are
 * driven from one shared bundle-backed builder.
 */
object Otp2PlanRequestBuilder {

    /**
     * Itineraries requested per search. Not user-configurable, and not a translation of any OTP1
     * setting — OTP1 requests never set `numItineraries` either, relying on the server default.
     * This is purely this client's OTP2 page size (`first`, in Relay-pagination terms).
     */
    private const val NUM_ITINERARIES = 5

    /**
     * How many upcoming departures OTP's alternative-leg search returns per transit leg
     * (`Leg.nextLegs(numberOfLegs:)`, #2010). A response page size, not a tuning threshold on the
     * data: OTP walks the same patterns and timetables either way and applies this as the final
     * `.limit(…)` (see `AlternativeLegs.getAlternativeLegs`), so raising it costs response bytes
     * rather than server work.
     *
     * It is still a **cap on what the rider is told**, and worth naming as such: the page is shared by
     * every route serving the leg, so on a frequent corridor an infrequent-but-equivalent route can
     * fall past it and simply not be offered. The failure is silent and one-directional — the badge
     * under-reports, never over-reports — and which routes make the page varies with how often the
     * planned one runs.
     */
    private const val ALTERNATIVE_LEGS = 12

    private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * The additional transfer cost (seconds) OTP1's `optimize=TRANSFERS` applied on top of the
     * (always-0-here) base `transferPenalty`, verified against OTP1 1.5.0's own source
     * (`api/common/RoutingResource.java`: `if (optimize == OptimizeType.TRANSFERS) { optimize =
     * OptimizeType.QUICK; request.transferPenalty += 1800; }` — `OptimizeType.java` itself calls
     * `TRANSFERS` "obsolete, replaced by the transferPenalty option"). OTP2's GraphQL API carries
     * the same concept forward as `TransitPreferencesInput.transfer.cost` (confirmed via OTP2's own
     * legacy-field mapper, `LegacyRouteRequestMapper`: `callWith.argument("transferPenalty",
     * tx::withCost)` — same field, renamed). Not a guessed value: this is what `optimize=TRANSFERS`
     * has always meant for every OTP1 region this app has talked to.
     */
    private const val OPTIMIZE_TRANSFERS_COST_SECONDS = 1800

    /**
     * The neutral point of both reluctance scales: **2.0**, OTP2's own documented default for
     * `walk.reluctance` *and* `bicycle.reluctance` ("A multiplier for how bad walking/cycling is,
     * compared to being in transit for equal lengths of time" — RouteRequest docs, v2.9.0, the
     * version this directory's schema is pinned to). It is the anchor the other four stops are
     * derived from, and the value [WalkPreference.MEDIUM]/[BikePreference.MEDIUM] send.
     *
     * **Sent explicitly rather than omitted**, which is a deliberate trade. Omitting it would let a
     * region that tuned its own reluctance in `router-config.json` keep that tuning — but it would
     * also make the middle of a five-point slider mean "whatever this region configured", so the
     * rider-visible ordering could invert: against a region at 6.0, "Low" (4.0) would produce *more*
     * walking than "Medium". A slider whose labels can run backwards is worse than one that
     * overrides a server default, so every stop states its multiplier and the scale is monotonic on
     * every region.
     *
     * The cost is real and worth naming: on an OTP2 region this app no longer inherits a
     * region-configured walk/bike reluctance, even for a rider who never opened the dialog.
     * [CyclingPreference.DEFAULT] is *not* a midpoint — there is no ordering for it to invert — so it
     * still omits `bicycle.optimization` and leaves that piece of the region's tuning alone.
     */
    private const val RELUCTANCE_MEDIUM = 2.0

    /**
     * The ratio between adjacent stops **above** the neutral point: each step away from
     * [RELUCTANCE_MEDIUM] on the "less of this mode" side multiplies by this, giving 4 and 8.
     *
     * Deliberately gentle. An earlier version stepped by 5, putting the far stop at 50, which is past
     * the point where the setting stops meaning "less of this mode" and starts producing nonsense:
     * measured against the live server, a direct bike trip at `bicycle.reluctance` 50 comes back at
     * **4.7 km/h** — OTP routes the rider to *push the bike on foot* the whole way, because at a 50x
     * penalty walking it is cheaper than riding it. The same trip is a normal 14-15 km/h ride at a
     * reluctance of 2 and still at 10, so the nonsense is specific to the far end of that old scale.
     * A "minimum cycling" setting that silently converts the trip to a walk is worse than one that
     * merely discourages it.
     */
    private const val RELUCTANCE_STEP = 2.0

    /**
     * The bottom of the scale, and a hard floor: **1.0**, OTP's neutral point, where a minute on
     * foot or on a bike costs the router exactly what a minute on transit does.
     *
     * Below 1.0 the setting stops meaning "more of this mode" and starts meaning "**don't use
     * transit**". That is the schema's own definition — "1 means neutral and values below 1 mean that
     * something is preferred over transit" — and OTP acts on it literally, deleting transit
     * itineraries in favour of a street-only one. Measured against the live server, with the
     * threshold falling exactly at 1.0:
     *
     * ```
     * rail only + own bike, bicycle.reluctance = 0.9  ->  no itineraries, NO_TRANSIT_CONNECTION
     * rail only + own bike, bicycle.reluctance = 1.0  ->  BICYCLE + TRAM(1 Line) + BICYCLE
     * ```
     *
     * An earlier version of this scale ran down to 0.1 and so put its top two stops inside that
     * region: asking for *more* cycling made bike-and-transit itineraries vanish entirely, which is
     * the exact opposite of the label's promise. Nothing in the advanced-settings dialog offers
     * "street only, no transit" on purpose, so a slider stop that silently becomes one is a trap
     * rather than a feature.
     *
     * (At exactly 1.0 a short trip can still come back street-only with
     * `WALKING_BETTER_THAN_TRANSIT`. That is a correct answer to "walking is as good as transit to
     * me", and `resolveOtp2Plan` already returns the walk itinerary rather than an error.)
     */
    private const val RELUCTANCE_FLOOR = 1.0

    /**
     * The stop between [RELUCTANCE_MEDIUM] and [RELUCTANCE_FLOOR] — their geometric mean, √2 ≈ 1.4.
     *
     * The scale is deliberately asymmetric: above neutral there is unbounded room, so it steps by
     * [RELUCTANCE_STEP]; below it there is only a factor of two before [RELUCTANCE_FLOOR], so that
     * remaining range is split once rather than forced onto the same ratio.
     */
    private const val RELUCTANCE_HIGH = 1.4

    /**
     * The two stops above the neutral point, completing the scale: **8 / 4 / 2 / 1.4 / 1.0**. Both
     * scales share one table — the rider-facing meaning is the same either way, and OTP documents the
     * same 2.0 default for both.
     *
     * Note the inversion: *less* of the mode is a *higher* multiplier. It lives here, at the wire
     * boundary, so the preference enums can stay in plain rider terms.
     */
    private const val RELUCTANCE_MINIMUM = RELUCTANCE_MEDIUM * RELUCTANCE_STEP * RELUCTANCE_STEP
    private const val RELUCTANCE_LOW = RELUCTANCE_MEDIUM * RELUCTANCE_STEP
    private const val RELUCTANCE_MAXIMUM = RELUCTANCE_FLOOR

    /** The reluctance for [preference]; every stop states one (see [RELUCTANCE_MEDIUM]). */
    private fun reluctanceFor(preference: WalkPreference): Double = when (preference) {
        WalkPreference.MINIMUM -> RELUCTANCE_MINIMUM
        WalkPreference.LOW -> RELUCTANCE_LOW
        WalkPreference.MEDIUM -> RELUCTANCE_MEDIUM
        WalkPreference.HIGH -> RELUCTANCE_HIGH
        WalkPreference.MAXIMUM -> RELUCTANCE_MAXIMUM
    }

    /** The reluctance for [preference]; every stop states one (see [RELUCTANCE_MEDIUM]). */
    private fun reluctanceFor(preference: BikePreference): Double = when (preference) {
        BikePreference.MINIMUM -> RELUCTANCE_MINIMUM
        BikePreference.LOW -> RELUCTANCE_LOW
        BikePreference.MEDIUM -> RELUCTANCE_MEDIUM
        BikePreference.HIGH -> RELUCTANCE_HIGH
        BikePreference.MAXIMUM -> RELUCTANCE_MAXIMUM
    }

    /**
     * @throws IllegalArgumentException if the origin/destination lack real coordinates or no
     * date/time was supplied — mirrors [TripRequestBuilder.buildRequest]'s own validation.
     */
    fun build(builder: TripRequestBuilder, context: Context): PlanQuery {
        val from = builder.from
        val to = builder.to
        if (from == null || !from.isSet || to == null || !to.isSet) {
            throw IllegalArgumentException("Must supply start and end coordinates to route between.")
        }
        val dateTime = builder.dateTime
            ?: throw IllegalArgumentException("Must supply a date/time to route at.")

        val formattedDateTime = DATE_TIME_FORMATTER.format(dateTime.atZone(ZoneId.systemDefault()))
        val planDateTime = if (builder.arriveBy) {
            PlanDateTimeInput(latestArrival = Optional.present(formattedDateTime))
        } else {
            PlanDateTimeInput(earliestDeparture = Optional.present(formattedDateTime))
        }

        return PlanQuery(
            origin = PlanLabeledLocationInput(location = coordinateLocation(from.latitude, from.longitude)),
            destination = PlanLabeledLocationInput(location = coordinateLocation(to.latitude, to.longitude)),
            dateTime = Optional.present(planDateTime),
            preferences = Optional.present(
                buildPreferences(
                    wheelchairAccessible = builder.getWheelchairAccessible(),
                    optimizeTransfers = builder.getOptimizeTransfers(),
                    walkPreference = builder.getWalkPreference(),
                    cyclingPreference = builder.getCyclingPreference(),
                    bikePreference = builder.getBikePreference()
                )
            ),
            modes = buildModes(builder.getModeSelection(), BikeshareAvailability.isTripPlanningEnabled(context)),
            numItineraries = NUM_ITINERARIES,
            alternativeLegs = ALTERNATIVE_LEGS
        )
    }

    private fun coordinateLocation(lat: Double, lon: Double): PlanLocationInput = PlanLocationInput(coordinate = Optional.present(PlanCoordinateInput(lat, lon)))

    /**
     * @param optimizeTransfers mirrors [TripRequestBuilder.getOptimizeTransfers] — OTP1's
     * `optimize=TRANSFERS` vs. the `QUICK` default; see [OPTIMIZE_TRANSFERS_COST_SECONDS].
     * @param walkPreference the OTP2-only stand-in for OTP1's `maxWalkDistance`, which this API
     * has no equivalent for at all (see [WalkPreference]).
     * @param cyclingPreference OTP2-only; OTP1's single `optimize` parameter is already spent on
     * [optimizeTransfers] (see [CyclingPreference]).
     * @param bikePreference OTP2-only; the nearest thing to a bike-distance setting, which no OTP
     * version provides (see [BikePreference]).
     */
    internal fun buildPreferences(
        wheelchairAccessible: Boolean,
        optimizeTransfers: Boolean,
        walkPreference: WalkPreference,
        cyclingPreference: CyclingPreference,
        bikePreference: BikePreference
    ): PlanPreferencesInput = PlanPreferencesInput(
        accessibility = Optional.present(
            AccessibilityPreferencesInput(
                wheelchair = Optional.present(
                    WheelchairPreferencesInput(enabled = Optional.present(wheelchairAccessible))
                )
            )
        ),
        street = buildStreetPreferences(walkPreference, cyclingPreference, bikePreference),
        transit = if (optimizeTransfers) {
            Optional.present(
                TransitPreferencesInput(
                    transfer = Optional.present(
                        TransferPreferencesInput(cost = Optional.present(OPTIMIZE_TRANSFERS_COST_SECONDS))
                    )
                )
            )
        } else {
            Optional.Absent
        }
    )

    /**
     * `preferences.street` — walk reluctance, bike reluctance and cycling optimization.
     *
     * Both reluctances are always present, including at the neutral stop, so the five-point scales
     * keep their rider-visible ordering on every region; see [RELUCTANCE_MEDIUM] for that trade-off
     * and what it costs. `bicycle.optimization` is the exception: [CyclingPreference.DEFAULT] is an
     * "unset" option rather than the midpoint of a scale, so it is omitted and the region's own
     * `bicycle.optimization` tuning survives.
     *
     * The cycling half is sent regardless of the selected [StreetMode]: it only bears on legs
     * OTP actually plans by bike, so it is inert on a walk-and-transit plan, and gating it on the
     * mode here would just duplicate — and risk disagreeing with — [buildModes].
     */
    internal fun buildStreetPreferences(
        walkPreference: WalkPreference,
        cyclingPreference: CyclingPreference,
        bikePreference: BikePreference
    ): Optional<PlanStreetPreferencesInput?> {
        val optimization = when (cyclingPreference) {
            CyclingPreference.DEFAULT -> null
            CyclingPreference.FASTEST -> CyclingOptimizationType.SHORTEST_DURATION
            CyclingPreference.SAFEST -> CyclingOptimizationType.SAFEST_STREETS
            CyclingPreference.FLATTEST -> CyclingOptimizationType.FLAT_STREETS
        }
        return Optional.present(
            PlanStreetPreferencesInput(
                walk = Optional.present(
                    WalkPreferencesInput(reluctance = Optional.present(reluctanceFor(walkPreference)))
                ),
                bicycle = Optional.present(
                    BicyclePreferencesInput(
                        reluctance = Optional.present(reluctanceFor(bikePreference)),
                        // CyclingOptimizationInput is a @oneOf input — exactly one of `type`/`triangle`
                        // may be present, and Apollo's generated `init` asserts it. Only `type` is ever
                        // set here.
                        optimization = optimization?.let {
                            Optional.present(CyclingOptimizationInput(type = Optional.present(it)))
                        } ?: Optional.Absent
                    )
                )
            )
        )
    }

    /**
     * Composes a [TripModeSelection] into OTP2's `modes` input.
     *
     * The two halves are orthogonal and are built independently: [VehicleMode] decides whether this
     * is a transit search at all and which transit modes are usable, [StreetMode] decides how the
     * street phases are covered. That is why this is a composition rather than a table of the twelve
     * combinations — only the *rules* below are per-mode, and each is stated once.
     *
     * An all-transit + walk selection leaves `modes` unset entirely: the schema's own default is
     * "all transit modes usable, WALK for access/egress", so there is nothing to express, and the
     * request stays byte-identical to what this app sent before the mode split.
     *
     * Takes [bikeshareEnabled] rather than a `Context` (see
     * [BikeshareAvailability.isTripPlanningEnabled]'s pure overload) so this mapping is a plain,
     * JVM-unit-testable function; `internal` for `Otp2PlanRequestBuilderTest`. A bikeshare selection
     * on a region without a rental network degrades to walking rather than being refused, matching
     * what the dialog would have offered.
     */
    internal fun buildModes(selection: TripModeSelection, bikeshareEnabled: Boolean): Optional<PlanModesInput?> {
        val street = if (selection.street == StreetMode.WALK_AND_BIKESHARE && !bikeshareEnabled) {
            StreetMode.WALK
        } else {
            selection.street
        }

        if (selection.vehicle == VehicleMode.NONE) {
            // A direct street trip: `directOnly` stops OTP also returning transit itineraries, which
            // is the whole point of choosing no vehicle.
            return Optional.present(
                PlanModesInput(
                    direct = Optional.present(directModes(street)),
                    directOnly = Optional.present(true)
                )
            )
        }

        val transitModes = when (selection.vehicle) {
            VehicleMode.BUS -> listOf(TransitMode.BUS)
            // TRAM is included so light rail counts as rail.
            VehicleMode.RAIL -> listOf(TransitMode.RAIL, TransitMode.TRAM)
            // Every mode the region runs — the schema default, so send nothing.
            VehicleMode.ALL_TRANSIT -> null
            VehicleMode.NONE -> null // unreachable, handled above
        }

        if (transitModes == null && street == StreetMode.WALK) {
            return Optional.Absent
        }

        return Optional.present(
            PlanModesInput(
                // The direct suggestion must match how the rider is actually travelling. OTP returns
                // one alongside the transit results and falls back to it when no transit connection
                // exists; left unset it defaults to WALK, so a cyclist whose requested vehicle didn't
                // pan out was offered a multi-hour *walk* (measured: rail-only from an origin with no
                // Link station in range returned a 9.8 km / 128 min walk). With this set, the same
                // search falls back to a ride.
                direct = Optional.present(directModes(street)),
                transit = Optional.present(
                    PlanTransitModesInput(
                        access = accessModes(street),
                        egress = egressModes(street),
                        transfer = transferModes(street),
                        transit = transitModes?.let { modes ->
                            Optional.present(modes.map { PlanTransitModePreferenceInput(mode = it) })
                        } ?: Optional.Absent
                    )
                )
            )
        )
    }

    /**
     * Access modes for [street].
     *
     * The two bike modes have **opposite** requirements, which is the one genuinely subtle thing here:
     *  - `BICYCLE_RENTAL` must be accompanied by `WALK` in the same list — OTP2 rejects a bare rental
     *    leg ("BIKE_RENTAL needs to be combined with WALK mode for the same leg", BadRequestError),
     *    since a hired bike must be walked to and from. Verified against the live server (#1780).
     *  - `BICYCLE` must **not** be: the schema says access "can use cycling only if the mode used for
     *    transfers and egress is also `BICYCLE`". Your own bike is with you the whole way, so there is
     *    no phase without it. A stray WALK here silently degrades the plan to walking.
     *
     * [StreetMode.WALK] sends nothing, since WALK is the schema's default for every phase.
     */
    private fun accessModes(street: StreetMode): Optional<List<PlanAccessMode>?> = when (street) {
        StreetMode.WALK -> Optional.Absent
        StreetMode.WALK_AND_BIKESHARE -> Optional.present(listOf(PlanAccessMode.WALK, PlanAccessMode.BICYCLE_RENTAL))
        StreetMode.BICYCLE -> Optional.present(listOf(PlanAccessMode.BICYCLE))
    }

    /** Egress modes for [street]; same rules as [accessModes]. */
    private fun egressModes(street: StreetMode): Optional<List<PlanEgressMode>?> = when (street) {
        StreetMode.WALK -> Optional.Absent
        StreetMode.WALK_AND_BIKESHARE -> Optional.present(listOf(PlanEgressMode.WALK, PlanEgressMode.BICYCLE_RENTAL))
        StreetMode.BICYCLE -> Optional.present(listOf(PlanEgressMode.BICYCLE))
    }

    /**
     * Transfer mode for [street]. Only [StreetMode.BICYCLE] sets one: `PlanTransferMode` has no
     * rental option (a hired bike is returned before boarding, so transfers are walked), and WALK is
     * already the default.
     */
    private fun transferModes(street: StreetMode): Optional<List<PlanTransferMode>?> = when (street) {
        StreetMode.BICYCLE -> Optional.present(listOf(PlanTransferMode.BICYCLE))
        StreetMode.WALK, StreetMode.WALK_AND_BIKESHARE -> Optional.Absent
    }

    /** Direct (no-transit) street modes for [street]; same rental-needs-WALK rule as [accessModes]. */
    private fun directModes(street: StreetMode): List<PlanDirectMode> = when (street) {
        StreetMode.WALK -> listOf(PlanDirectMode.WALK)
        StreetMode.WALK_AND_BIKESHARE -> listOf(PlanDirectMode.WALK, PlanDirectMode.BICYCLE_RENTAL)
        StreetMode.BICYCLE -> listOf(PlanDirectMode.BICYCLE)
    }
}
