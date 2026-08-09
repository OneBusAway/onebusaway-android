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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.onebusaway.android.extrapolation.soleOffsetOf
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.util.Polyline

/**
 * Which live trips belong to a focused directions leg, decided by **selection from the board stop's
 * arrivals queue** rather than by filtering the route's whole vehicle population (#2124).
 *
 * The queue is the authoritative answer to "what could carry the rider from here": it is the same set
 * the leg card's ETA strip renders — literally the same arrivals session, hoisted so the map and the
 * strip share one poll — and every entry is by definition an upcoming departure of one of the ride's
 * routes at the stop the rider boards. So instead of polling `trips-for-route` and reconstructing
 * eligibility from shape projections and schedule bounds, the map takes the queue's trip ids and keeps
 * the route poll only for *where* those trips currently are.
 *
 * The arrivals layer has already grouped its rows by (route, direction), so this consumes those groups
 * rather than re-deriving the grouping — one less place for the two sides to disagree.
 *
 * Pure JVM logic over [ObaTripSchedule] and plain ids, so it stays unit-testable (no `Context`, which
 * building an `ArrivalInfo` requires) and out of [RouteMapController]'s state plumbing.
 */

/** One route the ride may be taken on: the planned route, or an interchangeable alternative (#2010). */
internal data class RideRoute(
    val routeId: String,
    /** OTP's headsign for the leg, used to pick among the route's direction groups at the stop. */
    val headsign: String? = null
)

/**
 * The focused ride, as everything below needs to see it.
 *
 * Carried as one value rather than as separate boardable-routes / route-ids / continuation-count /
 * alighting-stop parameters, because those are four views of one ride and a caller deriving them
 * independently can skew them — admit a continuation on a route the queue never considered, or walk
 * more hops than the ride actually contains. Each is derived here, once, from the ride's own
 * structure. (The filter this replaces took [segments] the same way, and it is the reason that layer
 * stayed testable.)
 */
internal data class RideFocus(
    val leaderRouteId: String,
    /** The planned leg's headsign, for picking the leader's direction group at the boarding stop. */
    val leaderHeadsign: String? = null,
    val segments: List<RouteFocusSegment> = emptyList(),
    /** Where the rider leaves the ride; null when the OTP→OBA resolution failed. */
    val alightStopId: String? = null
) {
    /**
     * The routes a rider could board at the boarding stop: the planned route plus its interchangeable
     * alternatives. Stay-aboard continuations are deliberately absent — they are boarded mid-ride, so
     * they never appear in the boarding stop's arrivals.
     */
    val boardableRoutes: List<RideRoute>
        get() = listOf(RideRoute(leaderRouteId, leaderHeadsign)) +
            segments.filter { it.relationship == RouteFocusRelationship.INTERCHANGEABLE }
                .map { RideRoute(it.routeId, it.directionHeadsign) }

    /** Every route any part of the ride is travelled on — what a continuation is allowed to run. */
    val routeIds: Set<String> get() = segments.mapTo(linkedSetOf(leaderRouteId)) { it.routeId }

    /** How many scheduled trips the rider stays aboard for past the one they board. */
    val stayAboardHops: Int get() = segments.count { it.relationship == RouteFocusRelationship.STAY_ABOARD }
}

/**
 * What the board stop's arrivals were able to say about the ride.
 *
 * [Unserved] is deliberately distinct from an empty [Known]: it means the stop's arrivals name none of
 * the ride's routes at all — an OTP→OBA boarding-stop resolution that landed on a sibling platform —
 * and the caller must then draw every vehicle rather than none. An empty [Known] means the stop *does*
 * serve the ride's routes and simply has nothing upcoming, which correctly draws nothing.
 */
internal sealed interface RideQueue {
    /** No successful load yet (including a failed fetch): the map falls back to its seed. */
    data object Pending : RideQueue

    /** The stop's arrivals name none of the ride's routes — the queue cannot answer for this ride. */
    data object Unserved : RideQueue

    data class Known(val tripIds: List<String>) : RideQueue
}

/**
 * One trip the route poll currently reports, joined to what the observation cache knows about it.
 *
 * The single projection of a polled trip: ride selection reads [schedule]/[distanceAlongTrip], the
 * drawn approach reads [shapeId]/[schedule]/[shape] (see [tripApproach]). Kept as one type so the poll
 * is walked once per pass and a field added for one consumer can't drift out of the other's view.
 */
internal data class RideTrip(
    val tripId: String,
    val routeId: String,
    val shapeId: String?,
    val schedule: ObaTripSchedule?,
    val shape: Polyline?,
    /** The vehicle's server-computed progress along this trip, or null before it reports one. */
    val distanceAlongTrip: Double?
)

/** Which vehicles the focused ride draws. */
internal sealed interface RideVisibility {
    /** Draw everything the poll reports — the queue could not answer ([RideQueue.Unserved]). */
    data object All : RideVisibility

    data class Only(val tripIds: Set<String>) : RideVisibility
}

/** [visibility] for this pass, plus the [admitted] set to carry into the next one. */
internal data class RideSelection(
    val visibility: RideVisibility,
    val admitted: Set<String>
)

/**
 * One of the arrivals list's (route, direction) rows, reduced to what ride selection needs.
 *
 * Public because it rides a [org.onebusaway.android.ui.home.MapDirective] from HOME's hoisted arrivals
 * session to the map; everything else here stays internal to the map layer.
 */
data class RideRouteGroup(
    val routeId: String,
    val headsign: String?,
    val tripIds: List<String>
)

/**
 * The one group of [routeId] the ride is travelled in: the one whose headsign matches [headsign],
 * else that route's first group.
 *
 * Shared with the ETA strip's route pick (`DirectionStopEtaStrip.pickRoute`, which delegates here), so
 * the strip and the map cannot disagree about which direction of a route the rider is riding. Generic
 * over the accessors rather than over a common interface because the strip holds `RouteRowGroup`s and
 * this side holds plain data; neither shares a supertype declaring both properties.
 */
internal fun <T> List<T>.pickRideDirection(
    routeId: String?,
    headsign: String?,
    routeIdOf: (T) -> String,
    headsignOf: (T) -> String?
): T? {
    if (routeId == null) return null
    // Two scans of the receiver rather than one filtered copy: this also runs from the strip's
    // composable body, where an intermediate list would be rebuilt on every recomposition.
    return firstOrNull {
        routeIdOf(it) == routeId && headsign != null && headsignOf(it).equals(headsign, ignoreCase = true)
    } ?: firstOrNull { routeIdOf(it) == routeId }
}

/**
 * The ride's candidate departures among the boarding stop's arrival [groups].
 *
 * No time is consulted: the map shows exactly what the strip lists, including departures ruled out by
 * the plan's reach-the-stop marker (#2125). Those are dimmed in the strip, not hidden, and a rider
 * looking at the map should see the same vehicles.
 */
internal fun rideQueueFrom(groups: List<RideRouteGroup>, ride: RideFocus): RideQueue {
    val tripIds = LinkedHashSet<String>()
    var anyRouteServed = false
    for (route in ride.boardableRoutes) {
        val chosen = groups.pickRideDirection(
            routeId = route.routeId,
            headsign = route.headsign,
            routeIdOf = { it.routeId },
            headsignOf = { it.headsign }
        ) ?: continue
        anyRouteServed = true
        // A set, so a route listed as both the planned leg and one of its own alternatives contributes
        // each departure once.
        tripIds += chosen.tripIds
    }
    return if (anyRouteServed) RideQueue.Known(tripIds.toList()) else RideQueue.Unserved
}

/**
 * The trips the rider's vehicle continues onto while they stay aboard, followed one scheduled trip at
 * a time from each trip already admitted.
 *
 * The walk is bounded by [RideFocus.stayAboardHops] — the continuations the *plan* contains — so it is
 * the ride that limits it rather than the data. That bound is the whole point: a block is a service
 * day, not a ride —
 * KCM block `1_8128824` holds eleven consecutive route-40 trips — so admitting a vehicle's block would
 * keep it drawn for hours after the rider is gone, and the alighting rule could not retire it (the
 * return trip serves the opposite direction's stop ids, so it never contains the alighting stop).
 * `isRouteContinuation` makes the same point for the drawn continuation arrow: "does the block
 * continue" is true at nearly every trip boundary and is not the question.
 *
 * A neighbour is kept only when its own resolved route is one the ride is travelled on. Unlike
 * `isRouteContinuation` — which asks whether a continuation is worth *drawing* as a different route —
 * a same-route neighbour is accepted here, because a self-interline rides one route through two
 * phases and the rider stays aboard across it.
 *
 * [neighbourRouteOf] resolves the neighbour trip's route (a cached lookup); a trip that resolves to
 * none terminates the walk, as does a schedule with no next trip — OBA's block-end sentinel is blanked
 * to null at the wire boundary (#2003), so it arrives here simply as "no next trip".
 *
 * **[seed] is narrowed to the trips [scheduleOf] can actually answer for** (#2206). It plays two roles
 * — the walk's first frontier, and the visited set that stops a self-linking block looping — and a
 * member with no schedule is inert as a frontier but was still live as a suppressor: it silently
 * deleted a continuation the walk had every right to report. That is what let the answer feed back into
 * the next question, since the caller seeds from a set that carries the *previous* answer forward
 * (`admitRideTrips` deliberately doesn't intersect continuations with the poll, so a continuation is
 * drawn the moment its first poll lands). A trip you cannot walk from cannot loop, so it has no
 * business in the visited set, and dropping it makes the result a function of what the caller can
 * currently see rather than of what it last concluded.
 */
internal suspend fun rideContinuations(
    seed: Set<String>,
    ride: RideFocus,
    scheduleOf: (String) -> ObaTripSchedule?,
    neighbourRouteOf: suspend (String) -> String?
): Set<String> {
    val hops = ride.stayAboardHops
    val rideRouteIds = ride.routeIds
    if (hops <= 0 || seed.isEmpty()) return emptySet()
    val walkable = seed.filterTo(LinkedHashSet()) { scheduleOf(it) != null }
    if (walkable.isEmpty()) return emptySet()
    val found = LinkedHashSet<String>()
    var frontier: Set<String> = walkable
    repeat(hops) {
        // Collect the hop's candidates with the cheap synchronous guards first, then resolve them
        // together: each neighbour lookup can miss its cache and cost a round trip, and they are
        // independent, so a serial loop would stall the whole hop behind the slowest one.
        val candidates = frontier.mapNotNullTo(LinkedHashSet()) { tripId ->
            scheduleOf(tripId)?.nextTripId
                // Visited guard: a feed that links a block back on itself must not loop forever.
                ?.takeIf { it !in walkable && it !in found }
        }
        val next = coroutineScope {
            candidates.map { async { it to neighbourRouteOf(it) } }
                .awaitAll()
                .filterTo(LinkedHashSet()) { (_, routeId) -> !routeId.isNullOrEmpty() && routeId in rideRouteIds }
                .mapTo(LinkedHashSet()) { it.first }
        }
        if (next.isEmpty()) return found
        found += next
        frontier = next
    }
    return found
}

/**
 * Whether this trip has demonstrably carried the rider past where they leave the ride.
 *
 * Deliberately one-sided: it answers "can we *prove* the ride is over", and every unknown reads as
 * false (still drawn). A trip that doesn't serve the alighting stop is not evidence the ride ended —
 * the leading trip of an interlined ride alights on its continuation, and a short-turn simply never
 * gets there — and a stop the trip serves twice has no single alighting to compare against, the same
 * refusal [soleOffsetOf] makes elsewhere.
 *
 * Both quantities are server-computed along the same trip shape, so the comparison needs no projection
 * and no tolerance. `>` not `>=`: a vehicle standing exactly at the alighting stop still carries the
 * rider.
 */
internal fun provablyPastAlight(trip: RideTrip, alightStopId: String?): Boolean {
    alightStopId ?: return false
    val alightOffset = trip.schedule?.soleOffsetOf(alightStopId) ?: return false
    val progress = trip.distanceAlongTrip ?: return false
    return progress > alightOffset
}

/**
 * Admit the ride's trips: everything currently queued, everything admitted before that is still
 * running, and the stay-aboard continuations of both.
 *
 * Carrying [previouslyAdmitted] forward is what keeps a vehicle drawn after it leaves the queue —
 * an arrival drops off the board stop's list the moment the vehicle passes it, but the rider is on
 * board by then. Intersecting with [pollTripIds] is what bounds that memory: a finished trip stops
 * being reported and is forgotten. [continuationTripIds] is *not* intersected, so a continuation is
 * already admitted when its first poll arrives.
 */
internal fun admitRideTrips(
    previouslyAdmitted: Set<String>,
    queueTripIds: Collection<String>,
    pollTripIds: Set<String>,
    continuationTripIds: Set<String>
): Set<String> = buildSet {
    previouslyAdmitted.filterTo(this) { it in pollTripIds }
    addAll(queueTripIds)
    addAll(continuationTripIds)
}

/**
 * One pass of the ride's vehicle selection: admit, then retire.
 *
 * Retirement is applied after admission so a continuation can't resurrect a trip already proven past
 * the alighting stop. [seedTripIds] carries the explicitly tapped ETA pill (`focusTripId`): a pill tap
 * reaches the map before the queue's first load lands, and without it the requested vehicle would be
 * filtered out before the camera ever framed it.
 */
internal fun rideSelection(
    queue: RideQueue,
    seedTripIds: Set<String>,
    previouslyAdmitted: Set<String>,
    pollTrips: List<RideTrip>,
    continuationTripIds: Set<String>,
    ride: RideFocus
): RideSelection = when (queue) {
    RideQueue.Pending -> RideSelection(RideVisibility.Only(seedTripIds), previouslyAdmitted)
    // The stop cannot answer for this ride; hiding everything would leave a permanently empty map.
    RideQueue.Unserved -> RideSelection(RideVisibility.All, previouslyAdmitted)
    is RideQueue.Known -> {
        val admitted = admitRideTrips(
            previouslyAdmitted = previouslyAdmitted,
            queueTripIds = queue.tripIds,
            pollTripIds = pollTrips.mapTo(HashSet()) { it.tripId },
            continuationTripIds = continuationTripIds + seedTripIds
        )
        val retired = pollTrips.mapNotNullTo(HashSet()) {
            it.tripId.takeIf { _ -> provablyPastAlight(it, ride.alightStopId) }
        }
        // One subtraction: what is drawn now is exactly what is carried into the next pass.
        val visible = admitted - retired
        RideSelection(RideVisibility.Only(visible), visible)
    }
}
