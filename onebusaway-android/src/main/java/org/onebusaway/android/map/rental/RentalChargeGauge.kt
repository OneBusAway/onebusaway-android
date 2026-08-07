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
package org.onebusaway.android.map.rental

import org.onebusaway.android.directions.model.RentalFormFactor

/**
 * How full to draw a rental marker's charge ring (#2168).
 *
 * ---
 * ## ⚠ This file contains a SIGNED-OFF HEURISTIC (CLAUDE.md "No unsanctioned heuristics")
 *
 * **The assumption.** When a feed publishes a vehicle's remaining `range` but not its charge
 * `percent`, we treat a form factor's *assumed maximum range* ([ASSUMED_MAX_RANGE_METERS]) as a full
 * battery and draw `range / max` as the ring's fill.
 *
 * **Why no exact source exists.** A ring renders a fraction, and neither of the two candidate sources
 * is reachable:
 *  - OTP2's `RentalVehicleFuel.percent` is documented as "expressed from 0 to 1" and *is* the exact
 *    fact — but the only OTP2 deployment this app can reach (Puget Sound, the sole region publishing
 *    an `otpBaseGraphqlUrl`) returns it **null for every vehicle**: 3,034 of 3,034 in a downtown
 *    Seattle bbox, checked against the live server on 2026-08-07. GBFS makes `current_fuel_percent`
 *    optional, and neither operator there publishes it.
 *  - GBFS *does* define the exact denominator, `max_range_meters` on `vehicle_types.json`, and makes
 *    it **required** for motorized vehicles — but OTP2's schema does not expose it. `RentalVehicleType`
 *    carries `formFactor` and `propulsionType` and nothing else, so it cannot be queried.
 *
 * So the numbers below are inferred from the observed fleet rather than read — fitted to 27,064 Puget
 * Sound vehicles on 2026-08-07.
 *
 * | form factor | n | p50 | p99 | observed max | assumed max |
 * |---|---|---|---|---|---|
 * | scooter (electric) | 19,978 | 19.6 km | 38.2 km | 38.7 km | 38 km |
 * | bicycle (electric assist) | 7,086 | 37.3 km | 61.5 km | 82.5 km | 62 km |
 *
 * **Both are fitted to p99, not to the observed maximum**, which is the one calibration lesson this
 * exercise actually taught. Fitting the e-bike to its largest range put the denominator at 85 km, and
 * a histogram of the resulting fills showed the ring flatly refusing to fill: 4 vehicles of 7,086
 * reached a full ring, because that 82.5 km was a lone outlier over a p99 of 61.5 km, so a *fully
 * charged* bike drew about 70% and looked two-thirds spent forever. Fitting to p99 instead puts a full
 * battery at the top of the ring, and [rentalChargeFraction]'s clamp absorbs whatever sits above it.
 *
 * The gap between p99 and max is the tell to check when adding a form factor here: the scooters' is
 * 500 m, which is what a real fleet ceiling looks like, while the bikes' is 21 km, which is what one
 * unusual vehicle looks like.
 *
 * **Failure mode.** Every ring reads wrong wherever the real fleet maximum differs — another city,
 * another operator, or Lime swapping scooter models here. It fails *quietly*: a fleet with half the
 * range draws every vehicle at half a ring and looks like a flat battery; one with double draws every
 * vehicle full. Nothing detects the drift, and no user-visible number is affected — the range text
 * beside the ring stays exact, so the ring can be misleading while the label is right.
 *
 * **Signed off** by the repo owner on 2026-08-07, after being shown the alternative (drop the gauge
 * entirely) and this failure mode. Flag it in the PR description.
 *
 * **How to retire this.** The moment either exact source becomes available, delete
 * [ASSUMED_MAX_RANGE_METERS] and this fallback: if a feed starts publishing `fuel.percent`,
 * [rentalChargeFraction] already prefers it with no code change; if OTP exposes `max_range_meters`,
 * select it in `Rentals.graphql` and divide by the published value.
 * ---
 */

/**
 * The range a full battery is assumed to give, per form factor — the inferred denominator this file's
 * header documents and signs off.
 *
 * Deliberately covers **only the two form factors actually measured**. A moped, a car or a cargo bike
 * gets no entry and therefore no gauge, rather than borrowing a scooter's number: the point of a
 * measured constant is that it was measured, and extending it by analogy to a vehicle class nobody
 * looked at would be a second, unsigned guess stacked on the first.
 */
private val ASSUMED_MAX_RANGE_METERS = mapOf(
    RentalFormFactor.SCOOTER to 38_000,
    RentalFormFactor.SCOOTER_SEATED to 38_000,
    RentalFormFactor.SCOOTER_STANDING to 38_000,
    RentalFormFactor.BICYCLE to 62_000,
    RentalFormFactor.CARGO_BICYCLE to 62_000
)

/**
 * How full [place]'s charge ring should be drawn, 0..1 — or null to draw no gauge at all (an unfilled
 * ring), which is what a dock, a pedal bike, and any vehicle the app can say nothing about all get.
 *
 * **Exact data wins.** A published `fuel.percent` is used as-is whenever present; the inferred
 * denominator above is consulted only when it is absent. So a feed that starts publishing the real
 * field silently stops using the heuristic, with no code change.
 */
fun rentalChargeFraction(place: RentalPlace): Float? {
    place.fuelPercent?.let { return it.toFloat().coerceIn(0f, 1f) }
    val range = place.rangeMeters ?: return null
    // Single form factor only: a dock counting several types has no one vehicle's battery to show.
    val max = place.formFactors.singleOrNull()?.let { ASSUMED_MAX_RANGE_METERS[it] } ?: return null
    return (range.toFloat() / max).coerceIn(0f, 1f)
}

/**
 * The colour band a charge ring is drawn in — the battery-style red/amber/green scale.
 *
 * The bands are **contiguous by construction**: [LOW] runs to [CHARGE_LOW_MAX], [MEDIUM] from there to
 * [CHARGE_MEDIUM_MAX], and [HIGH] above. There is no "between the bands" state, because a gauge with a
 * gap in it draws nothing for the vehicles that land there.
 */
enum class RentalChargeBand { LOW, MEDIUM, HIGH }

/** Below this the ring is red: not enough charge to count on for a trip of any length. */
private const val CHARGE_LOW_MAX = 0.15f

/**
 * Below this the ring is amber, above it green.
 *
 * The original specification named three numbers — red under 15%, amber under 35%, green over 55% —
 * which leaves 35–55% belonging to no band at all. Amber is extended to cover that span rather than
 * green, so a half-charged vehicle reads "usable" instead of "good"; a rider who acts on green and
 * finds a half-flat scooter is worse served than one who acts on amber and finds a better vehicle than
 * expected.
 */
private const val CHARGE_MEDIUM_MAX = 0.55f

/** Which band [fraction] (0..1) falls in. */
fun rentalChargeBand(fraction: Float): RentalChargeBand = when {
    fraction < CHARGE_LOW_MAX -> RentalChargeBand.LOW
    fraction < CHARGE_MEDIUM_MAX -> RentalChargeBand.MEDIUM
    else -> RentalChargeBand.HIGH
}
