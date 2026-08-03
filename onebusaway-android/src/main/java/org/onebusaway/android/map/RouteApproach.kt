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

import org.onebusaway.android.extrapolation.soleOffsetOf
import org.onebusaway.android.util.GeoPoint

/**
 * The **symbolic** upstream approach for a focused ride: where the vehicle comes from before the
 * rider boards, taken from a trip's own shape clipped at the boarding stop's server-computed
 * `distanceAlongTrip`, rather than by projecting the boarding point onto `stops-for-route` geometry.
 *
 * This is [rideEligibility]'s twin. That one decides which vehicles belong to the ride; this one
 * decides which line they run along to reach it. Both relate a stop id to a trip through the same
 * pair of server-computed quantities along the same shape, so neither needs a projection or a
 * tolerance, and the two can't disagree about where the ride begins and ends.
 *
 * Why it replaces the projection as the primary answer: `stops-for-route` hands a direction its
 * geometry as **several polylines**, and — contrary to what [upstreamTo] assumes — they are not
 * always complete alternative shape variants. A real case (King County route 3, direction "Summit
 * Downtown Seattle") returns the inbound run from Madrona and the outbound run to Summit as separate
 * pieces. A rider boarding downtown sits 80 m from the inbound piece and 7 m from the outbound one,
 * so the projection matched the piece the ride *leaves* on, and drew its first 80 m as the
 * "approach" — an invisible stub where 4.9 km of inbound line belonged, while the vehicle running
 * that inbound line was (correctly) still shown. Clipping the vehicle's own trip shape at the
 * boarding stop's offset can't make that mistake: no piece has to be chosen, because the trip
 * already names its own path.
 *
 * Everything here is already fetched — `DefaultTripObservationRepository.prefetchSchedulesAndShapes`
 * backfills the schedule and shape of every active trip in each `trips-for-route` poll — so this
 * costs no request, only a read of what the poll already warmed.
 *
 * Pure JVM logic over [RideTrip]'s schedule and shape, like [rideSelection], so it stays unit-testable
 * and out of [RouteMapController]'s state plumbing.
 */

/**
 * One trip's approach: its shape from the start through [boardStopId]'s own offset along it.
 *
 * Null at every step that can't be decided exactly, so the caller falls back to the geometric
 * [upstreamTo] rather than drawing a guess: the trip doesn't serve the stop (a different direction's
 * trip, or a short-turn), it serves it more than once (a loop — no single boarding to clip at, the
 * same refusal [soleOffsetOf] and [rideBoundEligibility] make), or the schedule/shape hasn't been
 * backfilled yet.
 *
 * A trip that *starts* at the boarding stop clips to a pair of coincident points — [subPolyline]
 * always returns both ends — so the result must be drawable *and* actually go somewhere. Dropping it
 * is correct: there is no upstream to draw, because the rider boards where the trip begins.
 */
internal fun tripApproach(trip: RideTrip, boardStopId: String): List<GeoPoint>? {
    val offset = trip.schedule?.soleOffsetOf(boardStopId) ?: return null
    val shape = trip.shape ?: return null
    return shape.subPolyline(0.0, offset)?.takeIf { it.isDrawableSegment() && it.first() != it.last() }
}

/**
 * Every distinct approach the currently-active [trips] make to [boardStopId], one per shape.
 *
 * Deduplicated by shape id because trips on a route overwhelmingly share one — a dozen active buses
 * would otherwise stack a dozen identical lines — while genuinely different variants each keep their
 * own approach, which is what a rider watching two branches converge should see.
 *
 * No direction filter is applied or needed: a trip running the other way doesn't serve this boarding
 * stop id, so it yields no approach on its own. That is the same property that lets the vehicle
 * filter drop the `directionId` prefilter this replaces.
 */
internal fun approachPolylines(trips: List<RideTrip>, boardStopId: String?): List<List<GeoPoint>> {
    boardStopId ?: return emptyList()
    return trips.distinctBy { it.shapeId }.mapNotNull { tripApproach(it, boardStopId) }
}
