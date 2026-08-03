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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.data.forEachActiveTrip
import org.onebusaway.android.models.RouteTrips

/**
 * Owns "which live vehicles belong to the focused directions leg" (#2124). Extracted from
 * [RouteMapController] so the queue, the admitted set, the continuation walk and the guards that keep
 * all of it off the frame loop live together, instead of as nine more fields in the route controller.
 *
 * The rider's options are decided by **selection**, not by filtering: the boarding stop's live arrivals
 * ([setArrivals]) say which trips could carry them, and the route poll ([refresh]) only says where those
 * trips currently are.
 *
 * ```
 * admitted = (previously admitted, still running) + queued + continuations
 * visible  = admitted − (trips provably past the ride's alighting stop)
 * ```
 *
 * **Cadence is the whole point.** Both inputs the decision reads — each trip's schedule and its reported
 * progress — change only when a poll is replaced, so [refresh] identity-compares its inputs and does
 * nothing on the frames in between. `RouteMapController.sampleVehicles` calls it at 20 Hz and reads
 * [visibility] as a hash-set membership test; the walk behind it runs once per 10 s vehicle poll and
 * once per 60 s arrivals load.
 *
 * The trip-observation cache enters as the two lambdas this actually calls ([lookupTripState],
 * [neighbourRouteOf]) rather than the whole repository, so every rule here is reachable from a JVM test
 * without a map host — the same shape [StopFocusController] takes its nearby-stops loader in.
 */
internal class RideSelectionController(
    private val lookupTripState: (String) -> TripState?,
    private val neighbourRouteOf: suspend (String) -> String?,
    private val scope: CoroutineScope,
    private val onSelectionChanged: () -> Unit
) {

    /** The boarding stop's arrivals as the ride's candidates; Pending until the first load reports. */
    private var queue: RideQueue = RideQueue.Pending

    /** Trips admitted so far, carried across passes so a vehicle stays drawn once it leaves the queue. */
    private var admitted: Set<String> = emptySet()

    /** Stay-aboard continuations, resolved off the sampler because the neighbour lookup can cost a request. */
    private var continuations: Set<String> = emptySet()

    private var continuationJob: Job? = null

    /**
     * The explicitly tapped ETA pill, admitted before the queue's first load lands so a pill tap can
     * still frame its vehicle. It replaces #2099's filter exemption: once the queue arrives the tapped
     * trip is in it by construction, so this only covers the window before that.
     */
    private var seed: Set<String> = emptySet()

    // What [refresh] was last run against. Identity compares for the polls, like RouteMapController's
    // colour memo: a landed poll replaces its VehiclePoll (and the extras map) wholesale rather than
    // mutating it. [lastSeed] compares by value — it is at most one id, and a pill tap changes it in
    // place, between polls. Every input the selection reads has to be here: a tap seeds and then
    // rebuilds the vehicle set in the same breath, so a guard that missed the seed would hand the
    // camera a layer without the very vehicle the rider asked for, and the focus would drop silently.
    private var lastPoll: VehiclePoll? = null
    private var lastExtras: Map<String, VehiclePoll>? = null
    private var lastQueue: RideQueue? = null
    private var lastSeed: Set<String>? = null

    /** Which vehicles to draw. [RideVisibility.All] whenever no ride is focused, or the stop can't answer. */
    var visibility: RideVisibility = RideVisibility.All
        private set

    /** The selected trips joined to their cached schedule/shape — what the drawn approach clips. */
    var visibleTrips: List<RideTrip> = emptyList()
        private set

    /** Begin a focus. [focusTripId] is the ETA pill this focus was entered from, if any. */
    fun start(focusTripId: String?) {
        reset()
        seed = setOfNotNull(focusTripId)
    }

    /** The ETA-pill trip to keep visible until the queue can speak for it. */
    fun seed(tripId: String) {
        seed = setOf(tripId)
    }

    /** Abandon the pill focus, without disturbing the ride itself (a deliberate direction switch). */
    fun clearSeed() {
        seed = emptySet()
    }

    /**
     * The focus moved to a different ride. Nothing admitted under the old one may carry over, and the
     * in-flight continuation walk belongs to it, so it is cancelled rather than left to report into the
     * new ride. The queue is re-derived from the next arrivals load for the new boarding stop.
     */
    fun rideChanged() {
        queue = RideQueue.Pending
        admitted = emptySet()
        continuations = emptySet()
        lastQueue = null
        continuationJob?.cancel()
        continuationJob = null
    }

    /** Leave route mode: forget the ride entirely and stop drawing a selection. */
    fun stop() = reset()

    private fun reset() {
        queue = RideQueue.Pending
        admitted = emptySet()
        continuations = emptySet()
        seed = emptySet()
        visibility = RideVisibility.All
        visibleTrips = emptyList()
        lastPoll = null
        lastExtras = null
        lastQueue = null
        lastSeed = null
        continuationJob?.cancel()
        continuationJob = null
    }

    /** Fresh arrivals for the ride's boarding stop: re-derive the queue against [ride]. */
    fun setArrivals(groups: List<RideRouteGroup>, ride: RideFocus) {
        queue = rideQueueFrom(groups, ride)
    }

    /**
     * Re-decide the selection, at most once per landed poll or arrivals load.
     *
     * [ride] is null outside a focused leg, which draws everything — a plain route view has no ride to
     * select against, and must not fall through to the seed-only branch and hide every vehicle.
     */
    fun refresh(
        ride: RideFocus?,
        leaderRouteId: String,
        poll: VehiclePoll,
        extras: Map<String, VehiclePoll>
    ) {
        if (ride == null) {
            visibility = RideVisibility.All
            return
        }
        if (poll === lastPoll && extras === lastExtras && queue === lastQueue && seed == lastSeed) return
        lastPoll = poll
        lastExtras = extras
        lastQueue = queue
        lastSeed = seed
        val pollTrips = pollRideTrips(leaderRouteId, poll, extras)
        val selection = rideSelection(
            queue = queue,
            seedTripIds = seed,
            previouslyAdmitted = admitted,
            pollTrips = pollTrips,
            continuationTripIds = continuations,
            ride = ride
        )
        visibility = selection.visibility
        admitted = selection.admitted
        visibleTrips = when (val current = selection.visibility) {
            RideVisibility.All -> pollTrips
            is RideVisibility.Only -> pollTrips.filter { it.tripId in current.tripIds }
        }
        refreshContinuations(ride, pollTrips)
    }

    /** Every active trip across the leader and extra polls, joined to what the observation cache holds. */
    private fun pollRideTrips(
        leaderRouteId: String,
        poll: VehiclePoll,
        extras: Map<String, VehiclePoll>
    ): List<RideTrip> = buildList {
        fun collect(routeId: String, response: RouteTrips) {
            response.forEachActiveTrip { tripId, status, activeTrip ->
                val state = lookupTripState(tripId)
                add(
                    RideTrip(
                        tripId = tripId,
                        routeId = response.trip(tripId)?.routeId ?: routeId,
                        shapeId = state?.shapeId ?: activeTrip.shapeId,
                        schedule = state?.schedule,
                        shape = state?.polyline,
                        distanceAlongTrip = status.distanceAlongTrip
                    )
                )
            }
        }
        collect(leaderRouteId, poll.response)
        extras.forEach { (extraRouteId, extraPoll) -> collect(extraRouteId, extraPoll.response) }
    }

    /**
     * Resolve the admitted trips' stay-aboard continuations, off the sampler.
     *
     * The neighbour lookup can miss its cache and cost a request, so it cannot run inline with the
     * selection; the result lands in [continuations] and is picked up by the next pass. That lag is
     * harmless — a continuation only matters once the vehicle reaches the seam, minutes in, by which
     * time many polls have landed.
     */
    private fun refreshContinuations(ride: RideFocus, pollTrips: List<RideTrip>) {
        if (ride.stayAboardHops == 0 || admitted.isEmpty()) {
            continuations = emptySet()
            return
        }
        val schedules = pollTrips.associate { it.tripId to it.schedule }
        continuationJob?.cancel()
        continuationJob = scope.launch {
            val resolved = rideContinuations(
                seed = admitted,
                ride = ride,
                scheduleOf = { schedules[it] },
                neighbourRouteOf = neighbourRouteOf
            )
            if (resolved != continuations) {
                continuations = resolved
                onSelectionChanged()
            }
        }
    }
}
