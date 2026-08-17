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
package org.onebusaway.android.demo

import kotlinx.serialization.Serializable
import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.ShapeEntry
import org.onebusaway.android.api.contract.StopReference

/**
 * The **static** half of the scripted tutorial's demo transit system (#2164): the agency, routes,
 * stops and route geometry the tour runs on, captured once from a real King County Metro deployment
 * (stop `1_11140`, E Pine St & Summit Ave, and routes 10 / 12 / 49) and bundled as
 * `res/raw/demo_transit.json`.
 *
 * **Only facts that don't move live here.** Everything time-varying — arrivals, predictions, vehicle
 * positions, trip schedules — is *generated* against the current clock by [DemoScenario] rather than
 * captured, because a captured arrival is stale the moment it's bundled and re-dating one means
 * guessing which of its fields are times. Splitting it this way keeps the fixture small, keeps the
 * tour's ETAs genuinely live-looking however long after the release it runs, and makes the states the
 * tutorial *teaches* (on time / early / late / scheduled, and a stop with a service alert) authored
 * facts rather than whatever the network happened to be doing on capture day.
 *
 * The wire types are reused verbatim so [DemoObaWebService] can serve this straight back through the
 * same models a real response decodes into — no parallel model hierarchy, and no adapter of its own.
 */
@Serializable
data class DemoTransitFixture(
    val agency: AgencyReference = AgencyReference(),
    /** The stop the tour opens on — the one the map spotlights and whose arrivals the script narrates. */
    val anchorStopId: String = "",
    val routes: List<RouteReference> = emptyList(),
    val stops: List<StopReference> = emptyList(),
    /** Per-route travel geometry, keyed by route id. Every route in [routes] has an entry. */
    val routeStops: Map<String, DemoRouteStops> = emptyMap()
) {
    val stopById: Map<String, StopReference> by lazy { stops.associateBy { it.id } }
    val routeById: Map<String, RouteReference> by lazy { routes.associateBy { it.id } }

    /** The tour's anchor stop. */
    val anchorStop: StopReference? get() = stopById[anchorStopId]
}

/**
 * One route's stops and shape **in a single direction of travel** — the only direction the demo
 * system runs, so a demo route is unambiguously "the 49 towards U-District Station" and the tour never
 * has to explain a direction picker it hasn't reached yet.
 *
 * [stopIds] is ordered along the direction of travel and [stopDistances] holds each stop's distance in
 * metres along [polyline], projected onto the shape when the fixture was built. Precomputing the
 * projection keeps the app from having to re-derive it (and keeps the two lists index-aligned by
 * construction); [DemoScenario] reads them to place a vehicle between stops and to date a trip's
 * schedule.
 */
@Serializable
data class DemoRouteStops(
    /** The GTFS direction id this group travels ("0"/"1"), as the source deployment published it. */
    val directionId: String = "0",
    /** The direction's display name, e.g. "U-District Station Capitol Hill" — used as the headsign. */
    val name: String = "",
    val stopIds: List<String> = emptyList(),
    /** Metres along [polyline] for each id in [stopIds], same index, strictly increasing. */
    val stopDistances: List<Double> = emptyList(),
    val polyline: ShapeEntry = ShapeEntry(),
    /** The full length of [polyline] in metres. */
    val totalDistance: Double = 0.0
) {
    /** The index of [stopId] along this direction, or null when the route doesn't serve it. */
    fun indexOf(stopId: String): Int? = stopIds.indexOf(stopId).takeIf { it >= 0 }

    /** How far along the shape [stopId] sits, in metres, or null when the route doesn't serve it. */
    fun distanceTo(stopId: String): Double? = indexOf(stopId)?.let(stopDistances::get)
}
