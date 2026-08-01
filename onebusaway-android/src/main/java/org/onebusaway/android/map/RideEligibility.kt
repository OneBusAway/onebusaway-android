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
package org.onebusaway.android.map

import org.onebusaway.android.extrapolation.offsetsOf
import org.onebusaway.android.models.ObaTripSchedule

/**
 * The **symbolic** route-focus vehicle filter (#2124): whether a vehicle's active trip can still
 * carry the rider to where they leave its route, decided from the trip's schedule rather than from
 * shape geometry. The exact relation is *"the trip's stop sequence contains the bounding stop at a
 * `distanceAlongTrip` the vehicle's progress hasn't passed"* — both distances are server-computed
 * along the same trip shape (see `ReminderShapeSource` for the shared-metric-space contract), so
 * the comparison needs no projection and no tolerance. Where the relation can't be decided
 * ([RideEligibility.UNKNOWN]) the caller falls back to the geometric [containsRoutePoint] test.
 *
 * Pure JVM logic over [ObaTripSchedule]/[RouteFocusSegment] (like [FocusResolution]), so it stays
 * unit-testable and out of [RouteMapController]'s state plumbing.
 */
internal enum class RideEligibility {
    /** Keep the vehicle: its trip still reaches an end-of-ride stop. */
    ELIGIBLE,

    /** Drop the vehicle: every bound is passed or provably unserved. */
    INELIGIBLE,

    /** No symbolic answer — fall back to the geometric filter. */
    UNKNOWN
}

/**
 * Where the rider leaves one focused route, and whether a trip that never reaches [stopId] is thereby
 * ruled out. [restrictive] is true for the ridden route itself (leader and stay-aboard continuations
 * — a variant that never reaches the stop can't carry the rider there, e.g. a short-turn), false for
 * an [RouteFocusRelationship.INTERCHANGEABLE] alternative, which may genuinely alight at a different
 * platform's stop id: there "doesn't serve this exact id" is not a safe rejection.
 */
internal data class RideBound(
    val stopId: String?,
    val restrictive: Boolean
)

/**
 * Judge one trip against one end-of-ride bound. Absence from the stop sequence is decided before
 * [statusDistanceAlongTrip] is consulted — it needs no progress — and rejects only for a restrictive
 * bound. A stop the trip serves more than once (a loop or out-and-back) has no single "the rider's
 * alighting" to bound at, so it refuses rather than guessing, the same stance `soleOffsetOf` takes
 * and the class of heuristic this filter exists to remove. The comparison is `<=`: a vehicle standing
 * exactly at the bounding stop still carries the rider, matching the geometric bound it replaces.
 */
internal fun rideBoundEligibility(
    schedule: ObaTripSchedule?,
    statusDistanceAlongTrip: Double?,
    bound: RideBound
): RideEligibility {
    val stopId = bound.stopId ?: return RideEligibility.UNKNOWN
    schedule ?: return RideEligibility.UNKNOWN
    val offsets = schedule.offsetsOf(stopId)
    if (offsets.isEmpty()) {
        return if (bound.restrictive) RideEligibility.INELIGIBLE else RideEligibility.UNKNOWN
    }
    val boundOffset = offsets.singleOrNull() ?: return RideEligibility.UNKNOWN
    val progress = statusDistanceAlongTrip ?: return RideEligibility.UNKNOWN
    return if (progress <= boundOffset) RideEligibility.ELIGIBLE else RideEligibility.INELIGIBLE
}

/**
 * Combine a trip's verdicts over all of its route's [bounds] — more than one only for a self-interline,
 * which rides the same route through two phases. A rejection needs *every* bound decided against the
 * trip; anything undecided leaves the answer to the geometric fallback rather than dropping a vehicle
 * on partial knowledge.
 */
internal fun rideEligibility(
    schedule: ObaTripSchedule?,
    statusDistanceAlongTrip: Double?,
    bounds: List<RideBound>
): RideEligibility {
    if (bounds.isEmpty()) return RideEligibility.UNKNOWN
    // Every bound is decided before any combining: a keep must win even when a sibling is undecided,
    // so no short-circuit may hide a later ELIGIBLE.
    val verdicts = bounds.map { rideBoundEligibility(schedule, statusDistanceAlongTrip, it) }
    return when {
        RideEligibility.ELIGIBLE in verdicts -> RideEligibility.ELIGIBLE
        verdicts.all { it == RideEligibility.INELIGIBLE } -> RideEligibility.INELIGIBLE
        else -> RideEligibility.UNKNOWN
    }
}

/**
 * Each focused route's end-of-ride stops. Every segment states where its own part of the ride ends
 * ([RouteFocusSegment.endStopId], resolved by the producer from its leg), and [leaderEndStopId] does
 * the same for the primary route — so nothing here infers one route's bound from another's, and a
 * segment the producer had to drop costs only its own. A null stop id (an OTP→OBA resolution failure)
 * flows through as a null [RideBound.stopId] → [RideEligibility.UNKNOWN] → geometric fallback, never
 * a guess. A self-interline accumulates both of its phases' bounds under the one route id.
 */
internal fun rideBoundsByRoute(
    leaderRouteId: String,
    extraSegments: List<RouteFocusSegment>,
    leaderEndStopId: String?
): Map<String, List<RideBound>> {
    val leader = leaderRouteId to RideBound(leaderEndStopId, restrictive = true)
    val extras = extraSegments.map { segment ->
        segment.routeId to RideBound(
            segment.endStopId,
            // A continuation is the rider's own vehicle; an alternative is merely boardable.
            restrictive = segment.relationship == RouteFocusRelationship.STAY_ABOARD
        )
    }
    return (listOf(leader) + extras).groupBy({ it.first }, { it.second })
}
