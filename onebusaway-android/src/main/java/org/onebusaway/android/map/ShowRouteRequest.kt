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

/**
 * The intent to show a route on the map — the single payload every "show route on map" launcher builds
 * and both UI transports carry opaquely (the navigation reveal in `MapReveal` and the home
 * `MapDirective.ShowRoute`), unwrapped once at [MapViewModel.toRoute]. Bundling the parameters here
 * (rather than threading them one-by-one through the callback + directive + reveal hops) keeps adding a
 * new "show route on map" parameter a change to this one type instead of every layer in between.
 *
 * @property routeId the route to show.
 * @property directionStopId when non-null (the arrivals "show vehicles on map" launch), the stop whose
 *   direction the map narrows to; null shows the whole route. It's also the *originating stop* framed
 *   alongside the vehicle by [focusTripId].
 * @property focusTripId when non-null (the arrivals **ETA-pill** tap), the trip whose live vehicle the
 *   map fits into view together with the originating stop ([directionStopId]) — a one-shot framing of the
 *   vehicle↔stop relationship. When no live vehicle is running that trip, the map shows the route and
 *   raises the "vehicle isn't on the map" toast. A plain arrival-row tap leaves this null (frame the whole
 *   route); only the ETA pill sets it.
 * @property initialDirectionId when non-null (a route-continuation or adjacency-badge tap), the GTFS
 *   direction to show instead of the route's default — validated against the loaded route's directions
 *   by [RouteMapController], falling back to the default when it doesn't match.
 * @property riddenSpans when non-empty (a trip-plan transit leg drilled into route focus), the board→alight
 *   portion the user rides — drawn as a thick line over the full route so the traveled segment stands out.
 *   One [RiddenSpan] per route the ride is taken on, which is one for an ordinary leg and several across a
 *   stay-aboard interline. Empty for every non-directions "show route" caller.
 * @property extraSegments route/directions shown alongside the primary route: stay-aboard continuations
 *   (#2000) and interchangeable routes (#2042). Each relationship controls whether vehicles remain
 *   visible across a seam or are filtered to the segment's resolved direction.
 * @property alightStopId when non-null (a directions leg focus), the OBA id of the stop where the
 *   rider leaves the ride. The one bound the queue-driven vehicle selection needs (#2124): admission
 *   comes from the boarding stop's live arrivals, so this only has to answer "is this ride over yet",
 *   and a trip is dropped only once its own progress is provably past this stop. Null on an OTP→OBA
 *   resolution failure, or for a non-directions caller, which simply never retires a vehicle.
 * @property directionHeadsign the planned leg's headsign, used to pick the ridden direction group
 *   among the boarding stop's arrival rows — the same pick the leg card's ETA strip makes.
 */
data class ShowRouteRequest(
    val routeId: String,
    val directionStopId: String? = null,
    val focusTripId: String? = null,
    val initialDirectionId: Int? = null,
    val riddenSpans: List<RiddenSpan> = emptyList(),
    val extraSegments: List<RouteFocusSegment> = emptyList(),
    val alightStopId: String? = null,
    val directionHeadsign: String? = null
)
