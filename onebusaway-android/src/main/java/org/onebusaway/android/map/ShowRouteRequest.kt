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

import org.onebusaway.android.util.GeoPoint

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
 * @property highlightedSegment when non-empty (a trip-plan transit leg drilled into route focus), the
 *   polyline of the board→alight portion the user rides — drawn as a thick line over the full route so
 *   the traveled segment stands out. Empty for every non-directions "show route" caller.
 * @property extraSegments the *additional* ridden legs of a stay-aboard interline (#2000), beyond the
 *   leader ([routeId] + [directionStopId]). Non-empty only for a folded interline card: a self-interline
 *   (12→12) carries a segment with the same [routeId] (its other direction); a cross-route interline
 *   (45→75) carries a different route to load and draw alongside. The route focus draws each segment's
 *   shape + stops and shows the shared block vehicle across them all. Empty for every ordinary caller.
 */
data class ShowRouteRequest(
    val routeId: String,
    val directionStopId: String? = null,
    val focusTripId: String? = null,
    val initialDirectionId: Int? = null,
    val highlightedSegment: List<GeoPoint> = emptyList(),
    val extraSegments: List<RiddenSegment> = emptyList()
)
