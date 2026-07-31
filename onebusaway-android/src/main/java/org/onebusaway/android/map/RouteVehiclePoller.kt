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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.time.ElapsedTime

/**
 * The latest trips-for-route [response] and the monotonic-clock reading ([loadTime]) when it landed.
 * Monotonic rather than wall-clock because the only thing measured against it is a real elapsed
 * interval — how much of the refresh period a backgrounded map has already served out.
 */
internal data class VehiclePoll(val response: RouteTrips, val loadTime: ElapsedTime)

/**
 * Keeps a route session's real-time vehicle responses fresh, and nothing else. Extracted from
 * [RouteMapController] so the poll's jobs, cadence and retained responses are one small unit rather
 * than four mutable fields threaded through the controller's route/direction/focus state.
 *
 * It polls trips-for-route on a fixed cadence and retains the latest response per route; it does not
 * know about directions, focus, or how a response becomes markers. [RouteMapController] samples
 * [leaderPoll]/[extraPolls] to build the vehicle layer, and is told to re-sample via [onPoll].
 *
 * **Extra routes.** A cross-route interline (#2000) polls each *other* route alongside the leader, so
 * the one shared block vehicle shows through every phase of the ride; the controller merges those polls
 * when it samples. A self-interline segment reuses the leader route and so is not polled separately.
 *
 * The poll is suspended and resumed with the map via [pause]/[resume], and torn down with the route
 * session via [stop].
 */
internal class RouteVehiclePoller(
    private val tripObservationRepository: TripObservationRepository,
    private val scope: CoroutineScope,
    private val onPoll: () -> Unit
) {

    // The long-running periodic polls: the leader route's, plus one per extra route. Started, paused and
    // resumed together; [resume] checks all of them rather than treating the leader as a proxy, so a
    // poll that ended on its own is relaunched instead of staying dead for the rest of the session.
    private var leaderJob: Job? = null
    private var extraJobs: List<Job> = emptyList()

    /**
     * The most recent leader-route poll and when it landed. The controller's per-frame sampler reads the
     * response to dead-reckon every vehicle to the frame's clock; the load time lets a resume mid-period
     * wait only the remainder. Null until the first poll lands.
     */
    var leaderPoll: VehiclePoll? = null
        private set

    /**
     * The latest poll per extra route id — a cross-route interline polls each route so the one shared
     * block vehicle shows through every phase. Merged with [leaderPoll] when the controller samples;
     * empty otherwise.
     *
     * Replaced wholesale (never mutated in place) on each landed poll, because
     * [RouteMapController]'s per-frame vehicle-colour memo invalidates on the map's *reference*
     * identity — an in-place update would leave the memo holding colours resolved from a stale poll.
     */
    var extraPolls: Map<String, VehiclePoll> = emptyMap()
        private set

    /**
     * Begin a new route session: drop the previous session's responses so the controller can't sample
     * them during this one's load window, then poll from now.
     */
    fun start(routeId: String, extraRouteIds: List<String>) {
        leaderPoll = null
        extraPolls = emptyMap()
        launchPolls(routeId, extraRouteIds, initialDelayMs = 0L)
    }

    /**
     * Restart the polls if they aren't already running (the map's onResume), waiting out only the
     * remainder of the current period so a brief background trip doesn't cost a full extra poll. The
     * retained responses survive, so the map redraws from them immediately. [extraRouteIds] is re-read
     * from the caller rather than remembered, since a reframe can change the session's extra segments
     * without restarting the poll.
     *
     * "Already running" means *every* poll, not just the leader's: an extra route's poll that ended on
     * its own would otherwise never be noticed, and that route's vehicles would stop refreshing for the
     * rest of the session with no way back but leaving and re-entering the route.
     */
    fun resume(routeId: String, extraRouteIds: List<String>) {
        if (leaderJob?.isActive == true && extraJobs.all { it.isActive }) return
        launchPolls(routeId, extraRouteIds, nextVehicleDelay(leaderPoll?.loadTime, ElapsedTime.now()))
    }

    /**
     * Suspend polling while the map is paused (the map's onPause) — every route's, not just the leader's.
     * An off-screen map must not keep hitting the network; an interlined ride polls one job per extra
     * route (#2000), so cancelling the leader alone would leave those running until the next [resume].
     */
    fun pause() = cancelJobs()

    /** Tear down with the route session: cancel every poll and drop the retained responses. */
    fun stop() {
        cancelJobs()
        leaderJob = null
        extraJobs = emptyList()
        leaderPoll = null
        extraPolls = emptyMap()
    }

    /**
     * (Re)start every poll after [initialDelayMs]. Each route reloads every [VEHICLE_REFRESH_PERIOD_MS]
     * measured from its own load's completion (so network time is excluded), matching the legacy
     * `postDelayed`-after-`onLoadFinished` cadence, and continues on that cadence even if a load fails.
     */
    private fun launchPolls(routeId: String, extraRouteIds: List<String>, initialDelayMs: Long) {
        cancelJobs()
        leaderJob = pollRoute(routeId, initialDelayMs) { leaderPoll = it }
        extraJobs = extraRouteIds.map { extraRouteId ->
            pollRoute(extraRouteId, initialDelayMs) { extraPolls = extraPolls + (extraRouteId to it) }
        }
    }

    /**
     * One route's poll loop: the repository polls trips-for-route (backfilling each active trip's
     * schedule + shape into the store) and records each response. Each emission is a fresh poll — hand
     * it to [retain] for the motion sampler, stamped with the time it landed so a resume mid-period
     * waits only the remainder, then let the controller push the new vehicle set so the renderer
     * reconciles its markers. The renderer's frame loop dead-reckons every vehicle between polls.
     */
    private fun pollRoute(routeId: String, initialDelayMs: Long, retain: (VehiclePoll) -> Unit): Job = scope.launch {
        if (initialDelayMs > 0L) {
            delay(initialDelayMs)
        }
        tripObservationRepository.routeVehiclesStream(routeId, VEHICLE_REFRESH_PERIOD_MS).collect { response ->
            retain(VehiclePoll(response, ElapsedTime.now()))
            onPoll()
        }
    }

    private fun cancelJobs() {
        leaderJob?.cancel()
        extraJobs.forEach { it.cancel() }
    }
}
