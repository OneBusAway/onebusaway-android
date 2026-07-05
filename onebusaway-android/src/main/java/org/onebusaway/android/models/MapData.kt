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
package org.onebusaway.android.models

import android.location.Location

/** The stops visible in a viewport + the routes serving them (for the marker route-type icons). */
data class NearbyStops(
    val stops: List<ObaStop>,
    val routes: List<ObaRoute>,
    val outOfRange: Boolean,
    val limitExceeded: Boolean,
)

/**
 * A route's stops, the serving routes (for stop-marker icons), the route + agency name, and its
 * decoded shape. Polylines are [Location] points (the neutral geo type); the map layer turns them
 * into its render `GeoPoint`s. Each [RouteMapStop] carries the direction(s) it serves, so the overlay
 * can be narrowed to a single stop-relevant direction without a separate id index. [directions]
 * enumerates the route's selectable directions (id + headsign) so the header can offer a switch.
 *
 * [polylines] is the whole-route (merged, undirected) shape drawn when no direction is selected;
 * [polylinesByDirection] holds each direction's own shape (keyed by GTFS [RouteMapDirection.directionId],
 * travel-ordered, possibly several branches), drawn once that direction is selected. A direction absent
 * from the map (no per-direction shape on the wire) falls back to [polylines].
 */
data class RouteMapData(
    val route: ObaRoute?,
    val agencyName: String?,
    val stops: List<RouteMapStop>,
    val routes: List<ObaRoute>,
    val directions: List<RouteMapDirection>,
    val polylines: List<List<Location>>,
    val polylinesByDirection: Map<Int, List<List<Location>>>,
)

/**
 * One selectable direction of a route: its GTFS [directionId] (0/1/…, what a vehicle's trip
 * `directionId` matches) and the headsign [label] the header/picker shows. [label] may be blank
 * when the stop group carried no display name; the UI supplies a fallback.
 */
data class RouteMapDirection(
    val directionId: Int,
    val label: String,
)

/**
 * A [stop] as it appears on a route, tagged with the GTFS [directionIds] whose stop groups list it —
 * usually a single direction, a two-element set for a stop shared between both directions, and empty
 * when the route has no (numeric) direction grouping.
 */
data class RouteMapStop(
    val stop: ObaStop,
    val directionIds: Set<Int>,
)
