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
import kotlinx.coroutines.yield
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
/**
 * Everything the ride selection reads that can change between passes, as one value.
 *
 * The guard in [RideSelectionController.refresh] compares this whole, and the pass that follows takes
 * its inputs from nowhere else — so "an input the guard forgot" is not a mistake that can be made here.
 * It was made twice before this existed: a tapped ETA pill was seeded but not admitted until the next
 * poll, and a resolved stay-aboard continuation likewise waited a poll to be drawn. Both were one
 * hand-written `&&` short of correct, and neither was visible at the call site.
 *
 * **Adding a field here is the whole change.** It joins the guard and the pass together; forgetting one
 * of the two stops compiling. `RideSelectionControllerTest` pins the field count so a new input also
 * has to arrive with a test proving it invalidates.
 *
 * Compared by value, not identity: [poll]/[extras] hold instances a landed poll replaces wholesale, so
 * value and identity agree for them, while [ride] is rebuilt from the controller's fields on every
 * frame and [seed]/[queue] change in place between polls — for those, identity would never match and
 * the guard would never hold.
 */
internal data class RideSelectionInputs(
    val ride: RideFocus,
    val poll: VehiclePoll,
    val extras: Map<String, VehiclePoll>,
    val queue: RideQueue,
    val seed: Set<String>,
    val continuations: Set<String>
)

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

    /** What [refresh] was last run against; see [RideSelectionInputs] for why it is one value. */
    private var lastInputs: RideSelectionInputs? = null

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
        visibleTrips = emptyList()
        lastInputs = null
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
        lastInputs = null
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
        val inputs = RideSelectionInputs(ride, poll, extras, queue, seed, continuations)
        if (inputs == lastInputs) return
        lastInputs = inputs
        // Everything below reads `inputs`, never the fields it was gathered from — that is what keeps
        // the guard and the pass in step.
        val pollTrips = pollRideTrips(leaderRouteId, inputs.poll, inputs.extras)
        val selection = rideSelection(
            queue = inputs.queue,
            seedTripIds = inputs.seed,
            previouslyAdmitted = admitted,
            pollTrips = pollTrips,
            continuationTripIds = inputs.continuations,
            ride = inputs.ride
        )
        visibility = selection.visibility
        admitted = selection.admitted
        visibleTrips = when (val current = selection.visibility) {
            RideVisibility.All -> pollTrips
            is RideVisibility.Only -> pollTrips.filter { it.tripId in current.tripIds }
        }
        refreshContinuations(inputs.ride, pollTrips)
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
     *
     * **The walk is seeded only from trips this poll reports** (#2206). The seam of the cycle that
     * wedged the app is that [admitted] carries the *previous* walk's continuations forward
     * ([admitRideTrips] deliberately doesn't intersect them with the poll, so a continuation is drawn
     * the moment its first poll lands), so seeding from [admitted] wholesale fed each answer back in as
     * the next question. A trip the poll is silent about — which a continuation is by definition, until
     * the vehicle rolls onto it — has no schedule in [schedules] and so can never *extend* the walk; all
     * it can do is suppress a result through `rideContinuations`' visited guard. That made the answer
     * alternate (`{t2}`, then `{}` because `t2` now suppressed itself, then `{t2}` again once it fell
     * out of the poll intersection), and since every alternation is a change, each one republished and
     * re-entered this pass — an unbounded synchronous cycle on the main thread.
     *
     * Intersecting with the poll makes the seed a function of the queue and the poll alone. While one
     * poll stands the seed can only *grow* (each pass unions the previous admitted set with the queue),
     * and it is bounded by the trips that poll reports — so the walk reaches its fixed point in at most
     * that many passes and the republish chain ends. Multi-hop rides are unaffected: `rideContinuations`
     * walks its own frontier up to [RideFocus.stayAboardHops], so a chain is resolved inside one call
     * rather than across republishes.
     */
    private fun refreshContinuations(ride: RideFocus, pollTrips: List<RideTrip>) {
        if (ride.stayAboardHops == 0) return clearContinuations()
        val schedules = pollTrips.associate { it.tripId to it.schedule }
        val seed = admitted.filterTo(LinkedHashSet()) { it in schedules }
        if (seed.isEmpty()) return clearContinuations()
        continuationJob?.cancel()
        continuationJob = scope.launch {
            val resolved = rideContinuations(
                seed = seed,
                ride = ride,
                scheduleOf = { schedules[it] },
                neighbourRouteOf = neighbourRouteOf
            )
            if (resolved != continuations) {
                // Hand the republish back to the event loop before making it. The scope is
                // `Main.immediate`, and `onSelectionChanged` lands back in `refresh` (the renderer must
                // see a resolved continuation without waiting a poll, #2149) — so without this the whole
                // cycle runs as one synchronous stack, and a non-convergent pair of inputs spins the main
                // thread to an ANR rather than merely flickering (#2206).
                yield()
                continuations = resolved
                onSelectionChanged()
            }
        }
    }

    /**
     * Nothing to walk from. The in-flight walk is cancelled as well as the answer cleared: it belongs to
     * a state this pass has just answered for, and letting it report would reinstate continuations
     * nothing is riding on.
     */
    private fun clearContinuations() {
        continuationJob?.cancel()
        continuationJob = null
        continuations = emptySet()
    }
}
