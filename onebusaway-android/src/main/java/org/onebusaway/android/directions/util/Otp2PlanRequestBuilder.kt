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
import org.onebusaway.android.api.graphql.type.PlanAccessMode
import org.onebusaway.android.api.graphql.type.PlanCoordinateInput
import org.onebusaway.android.api.graphql.type.PlanDateTimeInput
import org.onebusaway.android.api.graphql.type.PlanDirectMode
import org.onebusaway.android.api.graphql.type.PlanEgressMode
import org.onebusaway.android.api.graphql.type.PlanLabeledLocationInput
import org.onebusaway.android.api.graphql.type.PlanLocationInput
import org.onebusaway.android.api.graphql.type.PlanModesInput
import org.onebusaway.android.api.graphql.type.PlanPreferencesInput
import org.onebusaway.android.api.graphql.type.PlanTransitModePreferenceInput
import org.onebusaway.android.api.graphql.type.PlanTransitModesInput
import org.onebusaway.android.api.graphql.type.TransferPreferencesInput
import org.onebusaway.android.api.graphql.type.TransitMode
import org.onebusaway.android.api.graphql.type.TransitPreferencesInput
import org.onebusaway.android.api.graphql.type.WheelchairPreferencesInput
import org.onebusaway.android.ui.tripplan.TripModes
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
                buildPreferences(builder.getWheelchairAccessible(), builder.getOptimizeTransfers())
            ),
            modes = buildModes(builder.getModeSetId(), BikeshareAvailability.isEnabled(context)),
            numItineraries = NUM_ITINERARIES,
        )
    }

    private fun coordinateLocation(lat: Double, lon: Double): PlanLocationInput =
        PlanLocationInput(coordinate = Optional.present(PlanCoordinateInput(lat, lon)))

    /**
     * @param optimizeTransfers mirrors [TripRequestBuilder.getOptimizeTransfers] — OTP1's
     * `optimize=TRANSFERS` vs. the `QUICK` default; see [OPTIMIZE_TRANSFERS_COST_SECONDS].
     */
    internal fun buildPreferences(
        wheelchairAccessible: Boolean,
        optimizeTransfers: Boolean,
    ): PlanPreferencesInput =
        PlanPreferencesInput(
            accessibility = Optional.present(
                AccessibilityPreferencesInput(
                    wheelchair = Optional.present(
                        WheelchairPreferencesInput(enabled = Optional.present(wheelchairAccessible))
                    )
                )
            ),
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
            },
        )

    /**
     * Maps [TripModes.*][TripModes] to OTP2's `modes` input, mirroring
     * [TripRequestBuilder.setModeSetById]'s OTP1 mode-string mapping. [TripModes.TRANSIT_ONLY]
     * (and an invalid id, matching that method's fallback) leaves `modes` unset entirely — the
     * schema's own default ("all transit modes usable, WALK for access/egress") already matches
     * that mode's OTP1 semantics, so there's nothing to express. Takes [bikeshareEnabled] rather
     * than a `Context` (see [BikeshareAvailability.isEnabled]'s pure overload) so this mapping is a
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
                            access = Optional.present(listOf(PlanAccessMode.BICYCLE_RENTAL)),
                            egress = Optional.present(listOf(PlanEgressMode.BICYCLE_RENTAL)),
                        )
                    )
                )
            )
        } else {
            Optional.Absent
        }

        TripModes.BIKESHARE -> Optional.present(
            PlanModesInput(
                direct = Optional.present(listOf(PlanDirectMode.BICYCLE_RENTAL)),
                directOnly = Optional.present(true),
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
