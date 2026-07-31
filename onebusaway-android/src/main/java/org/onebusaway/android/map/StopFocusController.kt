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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.runCatchingCancellable

/**
 * Everything a focused stop contributes to the route map's render plan, read as one immutable value so
 * [RouteMapController]'s publish can't observe a half-swapped focus. [trips] is null when no stop is
 * focused — the "focus inactive" signal the presentation assembler branches on.
 */
internal data class StopFocusPresentation(
    val trips: Set<FocusedTrip>?,
    val geometry: FocusedTripGeometry,
    val stops: FocusedTripStops,
    val routes: List<ObaRoute>,
    val routeColors: Map<RouteDirectionKey, Int>
) {
    /** The focused trips' scheduled stops snapped onto their own shapes; empty when no stop is focused. */
    fun projectedStops(): Map<String, GeoPoint> = trips?.let { projectFocusedStops(it, geometry, stops) }.orEmpty()
}

/**
 * Owns the "show the exact trips currently arriving at this stop" layer. Extracted from
 * [RouteMapController] so the focus session, its load job, and the geometry it resolves live together
 * instead of as six more fields in the route controller's state.
 *
 * A focus is a stop id plus the set of trips the arrivals sheet is showing for it. Focusing resolves two
 * things independently — each trip's shape ([FocusedTripRepository.getGeometry]) and its scheduled stops
 * ([FocusedTripRepository.getStops]) — and publishes through [onPresentationChanged] as they land.
 *
 * **Handoff.** A *first* focus publishes each half as soon as it resolves, so something appears quickly.
 * A focus that *replaces* an existing one stages both halves and swaps atomically ([apply]), so the
 * complete old presentation stays on screen instead of blinking through a half-loaded state. An empty
 * trip set is a valid focus.
 *
 * This controller does not draw: it exposes [presentation] and lets [RouteMapController] merge it with
 * the route's own geometry. It does drive [StopsMapController]'s start/stop, since whether the ordinary
 * nearby-stops loader should run depends on whether a stop focus (or a route) owns the stop layer.
 */
internal class StopFocusController(
    private val focusedTripRepository: FocusedTripRepository,
    private val stopsController: StopsMapController,
    private val scope: CoroutineScope,
    private val isRouteActive: () -> Boolean,
    private val onPresentationChanged: () -> Unit
) {

    /** Which focus this is. Compared to decide whether an incoming request is the same one re-sent. */
    private data class Session(
        val stopId: String,
        val trips: Set<FocusedTrip>
    )

    /**
     * The live focus and everything resolved for it so far. One value rather than four fields, since
     * they only ever move together — [clear] is one assignment, and a reader can't catch a half-swap.
     */
    private data class Focus(
        val session: Session,
        val routes: List<ObaRoute>,
        val geometry: FocusedTripGeometry = FocusedTripGeometry.EMPTY,
        val stops: FocusedTripStops = FocusedTripStops.EMPTY
    )

    private var focus: Focus? = null
    private var job: Job? = null
    private val _routeColors = MutableStateFlow<Map<RouteDirectionKey, Int>>(emptyMap())

    /**
     * The synthesized hue per focused route/direction, so adjacent routes at one stop stay
     * distinguishable. Published (rather than folded into [presentation]) because the arrivals UI colours
     * its rows from the same assignment the map draws with.
     */
    val routeColors: StateFlow<Map<RouteDirectionKey, Int>> = _routeColors.asStateFlow()

    /** The stop whose trips are drawn, or null when no stop is focused. */
    val focusedStopId: String? get() = focus?.session?.stopId

    /** The current focus as one immutable snapshot for the render plan. */
    val presentation: StopFocusPresentation
        get() {
            val current = focus
            return StopFocusPresentation(
                trips = current?.session?.trips,
                geometry = current?.geometry ?: FocusedTripGeometry.EMPTY,
                stops = current?.stops ?: FocusedTripStops.EMPTY,
                routes = current?.routes.orEmpty(),
                // The StateFlow's own map instance, not a copy: RouteMapController's per-frame
                // vehicle-colour memo invalidates on identity, so a fresh map here would clear it on
                // every frame.
                routeColors = _routeColors.value
            )
        }

    /** Show the exact trips currently displayed for [stopId] — see the handoff note on the class. */
    fun focus(
        stopId: String,
        trips: Set<FocusedTrip>,
        routes: List<ObaRoute>
    ) {
        val next = Session(stopId, LinkedHashSet(trips))
        val current = focus
        if (current?.session == next) {
            // Route wrappers may be recreated on every arrivals poll; refresh marker metadata without
            // restarting unchanged shape/schedule work.
            focus = current.copy(routes = routes)
            onPresentationChanged()
            return
        }
        job?.cancel()
        val retainCurrentPresentation = current != null
        if (!retainCurrentPresentation) {
            apply(Focus(next, routes))
        }
        job = scope.launch {
            supervisorScope {
                val geometry = async {
                    runCatchingCancellable { focusedTripRepository.getGeometry(next.trips) }
                        .getOrElse { FocusedTripGeometry.EMPTY }
                        .also { resolved ->
                            if (!retainCurrentPresentation) publishHalf(next) { it.copy(geometry = resolved) }
                        }
                }
                val stops = async {
                    runCatchingCancellable { focusedTripRepository.getStops(next.trips) }
                        .getOrElse { FocusedTripStops.EMPTY }
                        .also { resolved ->
                            if (!retainCurrentPresentation) publishHalf(next) { it.copy(stops = resolved) }
                        }
                }
                val nextGeometry = geometry.await()
                val nextStops = stops.await()
                if (retainCurrentPresentation) {
                    // A stop-to-stop handoff keeps the old complete presentation visible until both
                    // halves of the replacement are ready, then swaps once. Unrelated focus changes
                    // clear the old session before reaching here and retain the eager first-load path.
                    apply(Focus(next, routes, nextGeometry, nextStops))
                }
            }
        }
    }

    /**
     * Fold one resolved half into the live focus and publish — the eager first-load path, where each
     * half appears as soon as it lands. Dropped if [session] is no longer the focus being shown.
     */
    private fun publishHalf(session: Session, fold: (Focus) -> Focus) {
        val live = focus?.takeIf { it.session == session } ?: return
        focus = fold(live)
        onPresentationChanged()
    }

    private fun apply(next: Focus) {
        focus = next
        _routeColors.value = adjacencyRouteColors(
            next.session.trips.map(FocusedTrip::routeDirection),
            retained = _routeColors.value
        )
        stopsController.start()
        onPresentationChanged()
    }

    /** Clear focused-stop trips and reveal the base route (or ordinary nearby stops). */
    fun clear() {
        if (focus == null) return
        focus = null
        job?.cancel()
        job = null
        _routeColors.value = emptyMap()
        if (isRouteActive()) stopsController.stop() else stopsController.start()
        onPresentationChanged()
    }
}
