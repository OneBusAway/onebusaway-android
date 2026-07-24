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
package org.onebusaway.android.directions.model

import kotlin.time.Duration
import org.onebusaway.android.util.ROUTE_NAME_ORDER

/**
 * Decides which *other* routes a rider may board in place of a transit leg's planned one — the
 * "interleaved lines" case from #2010: between Lynnwood and downtown the 1 Line and the 2 Line run
 * the same track to the same platforms, so the directions should say "take whichever comes first"
 * rather than naming one of them.
 *
 * The candidate set comes from OTP ([TripLeg.alternatives], `Leg.nextLegs`), which returns upcoming
 * departures between this leg's exact board and alight stops on *any* route. This file supplies the
 * policy on top of it, and the policy is deliberately about **ride time**, never about which vehicle
 * happens to show up first: a local that turns up early must not be offered in place of the express
 * the plan is built around.
 *
 * A candidate route qualifies when
 *
 *     candidate ride time  ≤  planned ride time + [transferSlack]
 *
 * where the slack is the wait the itinerary itself already budgets before the next transit leg
 * departs — the exact amount by which the rider can arrive late at the alight stop and still make the
 * transfer as planned. There is no invented constant anywhere in that rule: every term is read off
 * the itinerary. On the last transit leg there is no transfer left to protect, so the slack is zero
 * and only routes that are no slower than the planned one qualify.
 *
 * Two further conservatisms, both because the drawer's claim is "any of these will do" — a promise
 * that has to hold for whichever vehicle actually arrives, not just the next one:
 *  - a route is represented by its **slowest** candidate trip, so a route with one fast run and one
 *    slow one is judged on the slow one;
 *  - a candidate whose own board/alight stops don't match the leg's is dropped rather than trusted.
 *
 * Pure and `Context`-free, so `InterchangeableRoutesTest` can exercise the rule directly.
 */

/**
 * A route the rider may board instead of a leg's planned route — one that passed the rule above.
 * [headsign] is the candidate trip's, for matching the route's direction group in the live arrivals
 * at the board stop.
 */
data class InterchangeableRoute(
    val routeId: String,
    val shortName: String?,
    val routeColor: String?,
    val agencyId: String?,
    val agencyName: String?,
    val headsign: String?
) {
    /** The name to show for this route, falling back to the OTP id when it has no short name. */
    val displayName: String get() = shortName?.takeIf { it.isNotBlank() } ?: routeId
}

/**
 * The interchangeable routes for each of [TripItinerary.legs], **index-aligned to that list** so a
 * caller can walk legs and alternatives together. Non-transit legs and legs with no qualifying
 * alternative get an empty list, so an OTP1 plan (no candidates at all) yields all-empty and every
 * caller keeps its existing behaviour.
 */
fun TripItinerary.interchangeableRoutes(): List<List<InterchangeableRoute>> = legs.indices.map { index -> interchangeableRoutesForLeg(index) }

private fun TripItinerary.interchangeableRoutesForLeg(index: Int): List<InterchangeableRoute> {
    val leg = legs[index]
    if (leg.mode?.isTransit != true) return emptyList()
    val budget = leg.duration + transferSlack(index)
    return leg.alternatives
        .asSequence()
        // A candidate that isn't a different route, or that doesn't run between this leg's own two
        // stops, is not an alternative to it.
        .filter { it.routeId != null && it.routeId != leg.routeId }
        .filter { it.fromStopId == leg.from.stopId && it.toStopId == leg.to.stopId }
        .groupBy { requireNotNull(it.routeId) }
        // Judge each route on its slowest run: the rider boards whichever of them turns up.
        .mapValues { (_, trips) -> trips.maxBy { it.duration } }
        .values
        .filter { it.duration <= budget }
        .map { candidate ->
            InterchangeableRoute(
                routeId = requireNotNull(candidate.routeId),
                shortName = candidate.routeShortName,
                routeColor = candidate.routeColor,
                agencyId = candidate.agencyId,
                agencyName = candidate.agencyName,
                headsign = candidate.headsign
            )
        }
        .sortedWith(compareBy(ROUTE_NAME_ORDER) { it.displayName })
}

/**
 * How late the rider may reach leg [index]'s alight stop and still make the itinerary's next transit
 * leg: that leg's departure, minus this leg's planned arrival, minus everything the plan has the
 * rider doing in between (the transfer walk, or a stay-put transfer's zero). In other words the wait
 * already built into the plan at the transfer point.
 *
 * [Duration.ZERO] when no transit leg follows — the last ride has no connection left to protect, so
 * an alternative has to be no slower than the planned vehicle to qualify. Also zero (never negative)
 * if the plan leaves no wait at all, e.g. a timed transfer boarded the moment it arrives.
 */
private fun TripItinerary.transferSlack(index: Int): Duration {
    val nextTransit = (index + 1..legs.lastIndex).firstOrNull { legs[it].mode?.isTransit == true }
        ?: return Duration.ZERO
    val betweenLegs = (index + 1 until nextTransit).fold(Duration.ZERO) { total, i -> total + legs[i].duration }
    val gap = legs[nextTransit].startTime - legs[index].endTime
    return (gap - betweenLegs).coerceAtLeast(Duration.ZERO)
}
