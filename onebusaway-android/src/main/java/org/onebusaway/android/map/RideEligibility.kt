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

import org.onebusaway.android.models.ObaTripSchedule

/**
 * The **symbolic** route-focus vehicle filter (#2124): whether a vehicle's active trip can still
 * carry the rider to where they leave the ride, decided from the trip's schedule rather than from
 * shape geometry. The exact relation is *"the trip's stop sequence contains the bounding stop at a
 * `distanceAlongTrip` the vehicle's progress hasn't passed"* — both distances are server-computed
 * along the same trip shape (see `ReminderShapeSource` for the shared-metric-space contract), so
 * the comparison needs no projection and no tolerance. Where the relation can't be decided
 * ([RideEligibility.UNKNOWN]) the caller falls back to the geometric [containsRoutePoint] test.
 *
 * Pure JVM logic over [ObaTripSchedule]/[RouteFocusSegment] (like [FocusResolution]), so it stays
 * unit-testable and out of [RouteMapController]'s state plumbing.
 */

/** A single trip-versus-bound comparison ([rideBoundVerdict]). */
internal enum class RideBoundVerdict {
    /** The trip serves the bounding stop ahead of (or at) the vehicle's position. */
    ELIGIBLE,

    /** The trip serves the bounding stop but the vehicle is already past it. */
    PASSED,

    /** The trip's stop sequence does not contain the bounding stop at all. */
    NOT_SERVED,

    /** The relation can't be decided symbolically (missing id, schedule, or progress). */
    UNKNOWN
}

/** The combined per-trip verdict a route's [RideBound]s produce ([rideEligibility]). */
internal enum class RideEligibility {
    /** Keep the vehicle: its trip still reaches an end-of-ride stop. */
    ELIGIBLE,

    /** Drop the vehicle: every bound is passed or provably unserved. */
    INELIGIBLE,

    /** No symbolic answer — fall back to the geometric filter. */
    UNKNOWN
}

/**
 * One candidate end-of-ride stop for a focused route's trips. [restrictive] says whether a trip
 * that doesn't serve [stopId] is thereby ruled out: true for the ridden route itself (leader and
 * stay-aboard continuations — a variant that never reaches the stop can't carry the rider there,
 * e.g. a short-turn), false for an [RouteFocusRelationship.INTERCHANGEABLE] alternative, which may
 * genuinely alight at a different platform's stop id — there "doesn't serve this exact id" is not
 * a safe rejection and degrades to [RideBoundVerdict.UNKNOWN].
 */
internal data class RideBound(
    val stopId: String?,
    val restrictive: Boolean
)

/**
 * Compare one trip against one bounding stop. [NOT_SERVED][RideBoundVerdict.NOT_SERVED] is decided
 * before looking at [statusDistanceAlongTrip]: absence from the stop sequence needs no progress. A
 * stop the trip serves more than once (a loop or out-and-back) is [UNKNOWN][RideBoundVerdict.UNKNOWN]
 * — there is no single "the rider's alighting" to bound at, and guessing one is exactly the class
 * of heuristic this filter exists to remove (same refusal as `ReminderShapeSource.soleOffsetOf`).
 * The comparison is `<=`: a vehicle standing exactly at the bounding stop still carries the rider,
 * matching the geometric bound it replaces.
 */
internal fun rideBoundVerdict(
    schedule: ObaTripSchedule?,
    statusDistanceAlongTrip: Double?,
    boundingStopId: String?
): RideBoundVerdict {
    boundingStopId ?: return RideBoundVerdict.UNKNOWN
    schedule ?: return RideBoundVerdict.UNKNOWN
    val matches = schedule.stopTimes.filter { it.stopId == boundingStopId }
    if (matches.isEmpty()) return RideBoundVerdict.NOT_SERVED
    val bound = matches.singleOrNull() ?: return RideBoundVerdict.UNKNOWN
    statusDistanceAlongTrip ?: return RideBoundVerdict.UNKNOWN
    return if (statusDistanceAlongTrip <= bound.distanceAlongTrip) {
        RideBoundVerdict.ELIGIBLE
    } else {
        RideBoundVerdict.PASSED
    }
}

/**
 * Combine a trip's verdicts over all of its route's [bounds]. Any [RideBoundVerdict.ELIGIBLE] keeps
 * the trip. A rejection needs *every* bound decided against it: any [RideBoundVerdict.UNKNOWN], any
 * non-restrictive [RideBoundVerdict.NOT_SERVED], or an empty [bounds] list leaves the answer to the
 * geometric fallback rather than dropping a vehicle on partial knowledge.
 */
internal fun rideEligibility(
    schedule: ObaTripSchedule?,
    statusDistanceAlongTrip: Double?,
    bounds: List<RideBound>
): RideEligibility {
    if (bounds.isEmpty()) return RideEligibility.UNKNOWN
    // Every bound gets its verdict before any combining: a keep must win even when an earlier
    // sibling is undecided, so no short-circuit may hide a later ELIGIBLE.
    val verdicts = bounds.map { bound ->
        rideBoundVerdict(schedule, statusDistanceAlongTrip, bound.stopId) to bound.restrictive
    }
    if (verdicts.any { (verdict, _) -> verdict == RideBoundVerdict.ELIGIBLE }) return RideEligibility.ELIGIBLE
    val rejectsAll = verdicts.all { (verdict, restrictive) ->
        verdict == RideBoundVerdict.PASSED || (verdict == RideBoundVerdict.NOT_SERVED && restrictive)
    }
    return if (rejectsAll) RideEligibility.INELIGIBLE else RideEligibility.UNKNOWN
}

/**
 * Each focused route's end-of-ride stops. The rider leaves a route where the *next* leg of the ride
 * begins: the leader's bound is the first stay-aboard seam (or [alightStopId] when the ride ends on
 * the leader), each stay-aboard continuation's bound is the following seam, and the last one's is
 * [alightStopId]. Interchangeable alternatives are bounded non-restrictively at [alightStopId].
 * Relies on [extraSegments] listing STAY_ABOARD segments in ride order with each `anchorStopId` the
 * seam stop where that continuation is boarded — the order `TripResultsRepository` builds them in.
 * A null stop id (an OTP→OBA resolution failure) flows through as a null [RideBound.stopId] →
 * [RideBoundVerdict.UNKNOWN] → geometric fallback, never a guess. A self-interline accumulates both
 * its seam and alight bounds under the one route id.
 */
internal fun rideBoundsByRoute(
    leaderRouteId: String,
    extraSegments: List<RouteFocusSegment>,
    alightStopId: String?
): Map<String, List<RideBound>> {
    val stayAboard = extraSegments.filter { it.relationship == RouteFocusRelationship.STAY_ABOARD }
    val bounds = mutableMapOf<String, MutableList<RideBound>>()
    fun add(routeId: String, bound: RideBound) = bounds.getOrPut(routeId) { mutableListOf() }.add(bound)
    add(leaderRouteId, RideBound(stayAboard.firstOrNull()?.anchorStopId ?: alightStopId, restrictive = true))
    stayAboard.forEachIndexed { i, segment ->
        add(segment.routeId, RideBound(stayAboard.getOrNull(i + 1)?.anchorStopId ?: alightStopId, restrictive = true))
    }
    extraSegments.filter { it.relationship == RouteFocusRelationship.INTERCHANGEABLE }.forEach { segment ->
        add(segment.routeId, RideBound(alightStopId, restrictive = false))
    }
    return bounds
}
