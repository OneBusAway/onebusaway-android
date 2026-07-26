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
import org.onebusaway.android.api.graphql.type.PlanTransitModePreferenceInput
import org.onebusaway.android.api.graphql.type.PlanTransitModesInput
import org.onebusaway.android.api.graphql.type.TransferPreferencesInput
import org.onebusaway.android.api.graphql.type.TransitMode
import org.onebusaway.android.api.graphql.type.TransitPreferencesInput
import org.onebusaway.android.api.graphql.type.WalkPreferencesInput
import org.onebusaway.android.api.graphql.type.WheelchairPreferencesInput
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.TripModes
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
     * The neutral point of both reluctance scales — OTP2's own documented default for
     * `walk.reluctance` *and* `bicycle.reluctance` ("A multiplier for how bad walking/cycling is,
     * compared to being in transit for equal lengths of time" — RouteRequest docs, v2.9.0, the
     * version this directory's schema is pinned to).
     *
     * Never actually sent: [WalkPreference.MEDIUM]/[BikePreference.MEDIUM] omit the field, so a
     * region that tuned its own value in `router-config.json` keeps it, and an all-default request
     * stays byte-identical to what this app sent before these settings existed. It is the anchor the
     * other four stops are derived from.
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
     * penalty walking it is cheaper than riding it. At 2 and 10 the same trip is a normal 14-15 km/h
     * ride. A "minimum cycling" setting that silently converts the trip to a walk is worse than one
     * that merely discourages it.
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
     * the exact opposite of the label's promise. The app already has a legible way to say "no
     * transit" — [org.onebusaway.android.ui.tripplan.VehicleMode.NONE] — and two controls that both
     * mean that, one of them by accident, is worse than one that does.
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
     * The four non-neutral stops: 50 / 10 / (unset) / 1.4 / 1.0. Both scales share one table — the
     * rider-facing meaning is the same either way, and OTP documents the same 2.0 default for both.
     *
     * Note the inversion: *less* of the mode is a *higher* multiplier. It lives here, at the wire
     * boundary, so the preference enums can stay in plain rider terms.
     */
    private const val RELUCTANCE_MINIMUM = RELUCTANCE_MEDIUM * RELUCTANCE_STEP * RELUCTANCE_STEP
    private const val RELUCTANCE_LOW = RELUCTANCE_MEDIUM * RELUCTANCE_STEP
    private const val RELUCTANCE_MAXIMUM = RELUCTANCE_FLOOR

    /** The reluctance for [preference], or null at the neutral stop (see [RELUCTANCE_MEDIUM]). */
    private fun reluctanceFor(preference: WalkPreference): Double? = when (preference) {
        WalkPreference.MINIMUM -> RELUCTANCE_MINIMUM
        WalkPreference.LOW -> RELUCTANCE_LOW
        WalkPreference.MEDIUM -> null
        WalkPreference.HIGH -> RELUCTANCE_HIGH
        WalkPreference.MAXIMUM -> RELUCTANCE_MAXIMUM
    }

    /** The reluctance for [preference], or null at the neutral stop (see [RELUCTANCE_MEDIUM]). */
    private fun reluctanceFor(preference: BikePreference): Double? = when (preference) {
        BikePreference.MINIMUM -> RELUCTANCE_MINIMUM
        BikePreference.LOW -> RELUCTANCE_LOW
        BikePreference.MEDIUM -> null
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
            modes = buildModes(builder.getModeSetId(), BikeshareAvailability.isTripPlanningEnabled(context)),
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
        walkPreference: WalkPreference = WalkPreference.MEDIUM,
        cyclingPreference: CyclingPreference = CyclingPreference.DEFAULT,
        bikePreference: BikePreference = BikePreference.MEDIUM
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
     * `preferences.street` — walk reluctance, bike reluctance and cycling optimization, each omitted
     * when the rider left it on its neutral stop ([WalkPreference.MEDIUM]/[BikePreference.MEDIUM]/
     * [CyclingPreference.DEFAULT]) so the region's own router-config values survive. When *all three*
     * are neutral the whole `street` block is absent rather than sent empty, which is also exactly
     * what this app did before these settings existed.
     *
     * The cycling half is sent regardless of the selected trip mode: it only bears on legs
     * OTP actually plans by bike, so it is inert on a walk-and-transit plan, and gating it on the
     * mode here would just duplicate — and risk disagreeing with — [buildModes].
     */
    internal fun buildStreetPreferences(
        walkPreference: WalkPreference,
        cyclingPreference: CyclingPreference,
        bikePreference: BikePreference = BikePreference.MEDIUM
    ): Optional<PlanStreetPreferencesInput?> {
        val walkReluctance = reluctanceFor(walkPreference)
        val bikeReluctance = reluctanceFor(bikePreference)
        val optimization = when (cyclingPreference) {
            CyclingPreference.DEFAULT -> null
            CyclingPreference.FASTEST -> CyclingOptimizationType.SHORTEST_DURATION
            CyclingPreference.SAFEST -> CyclingOptimizationType.SAFEST_STREETS
            CyclingPreference.FLATTEST -> CyclingOptimizationType.FLAT_STREETS
        }
        if (walkReluctance == null && bikeReluctance == null && optimization == null) {
            return Optional.Absent
        }
        // The two bicycle knobs share one input object, so build it once from whichever are set —
        // choosing an optimization must not pin a reluctance the rider never asked for, or vice versa.
        val bicycle = if (bikeReluctance == null && optimization == null) {
            Optional.Absent
        } else {
            Optional.present(
                BicyclePreferencesInput(
                    reluctance = bikeReluctance?.let { Optional.present(it) } ?: Optional.Absent,
                    // CyclingOptimizationInput is a @oneOf input — exactly one of `type`/`triangle`
                    // may be present, and Apollo's generated `init` asserts it. Only `type` is ever
                    // set here.
                    optimization = optimization?.let {
                        Optional.present(CyclingOptimizationInput(type = Optional.present(it)))
                    } ?: Optional.Absent
                )
            )
        }
        return Optional.present(
            PlanStreetPreferencesInput(
                walk = walkReluctance?.let {
                    Optional.present(WalkPreferencesInput(reluctance = Optional.present(it)))
                } ?: Optional.Absent,
                bicycle = bicycle
            )
        )
    }

    /**
     * Maps [TripModes.*][TripModes] to OTP2's `modes` input, mirroring
     * [TripRequestBuilder.setModeSetById]'s OTP1 mode-string mapping. [TripModes.TRANSIT_ONLY]
     * (and an invalid id, matching that method's fallback) leaves `modes` unset entirely — the
     * schema's own default ("all transit modes usable, WALK for access/egress") already matches
     * that mode's OTP1 semantics, so there's nothing to express. Takes [bikeshareEnabled] rather
     * than a `Context` (see [BikeshareAvailability.isTripPlanningEnabled]'s pure overload) so this mapping is a
     * plain, JVM-unit-testable function; `internal` for `Otp2PlanRequestBuilderTest`.
     */
    internal fun buildModes(modeId: Int, bikeshareEnabled: Boolean): Optional<PlanModesInput?> = when (modeId) {
        TripModes.TRANSIT_ONLY -> Optional.Absent

        TripModes.BUS_ONLY -> onlyTransitModes(TransitMode.BUS)

        TripModes.RAIL_ONLY -> onlyTransitModes(TransitMode.RAIL, TransitMode.TRAM)

        TripModes.TRANSIT_AND_BIKE -> if (bikeshareEnabled) {
            Optional.present(
                PlanModesInput(
                    transit = Optional.present(
                        PlanTransitModesInput(
                            // WALK must accompany BICYCLE_RENTAL in the same access/egress list —
                            // OTP2 rejects a bare BICYCLE_RENTAL leg ("BIKE_RENTAL needs to be
                            // combined with WALK mode for the same leg", BadRequestError), since a
                            // rental trip always walks to/from the vehicle. Verified against the
                            // live OTP 2.x server. #1780.
                            access = Optional.present(listOf(PlanAccessMode.WALK, PlanAccessMode.BICYCLE_RENTAL)),
                            egress = Optional.present(listOf(PlanEgressMode.WALK, PlanEgressMode.BICYCLE_RENTAL))
                        )
                    )
                )
            )
        } else {
            Optional.Absent
        }

        // WALK must accompany BICYCLE_RENTAL here too (see the TRANSIT_AND_BIKE branch above).
        TripModes.BIKESHARE -> Optional.present(
            PlanModesInput(
                direct = Optional.present(listOf(PlanDirectMode.WALK, PlanDirectMode.BICYCLE_RENTAL)),
                directOnly = Optional.present(true)
            )
        )

        // Invalid ids are already logged where they originate (TripRequestBuilder.setModeSetById),
        // and getModeSetId() only ever hands this a value it produced — nothing new to log here.
        else -> Optional.Absent
    }

    /** `modes.transit.transit`, restricted to exactly [modes] — the shared shape behind the
     * [TripModes.BUS_ONLY]/[TripModes.RAIL_ONLY] branches above. */
    private fun onlyTransitModes(vararg modes: TransitMode): Optional<PlanModesInput?> = Optional.present(
        PlanModesInput(
            transit = Optional.present(
                PlanTransitModesInput(
                    transit = Optional.present(modes.map { PlanTransitModePreferenceInput(mode = it) })
                )
            )
        )
    )
}
