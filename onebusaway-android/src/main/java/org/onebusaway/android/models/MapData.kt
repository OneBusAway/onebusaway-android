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
 * into its render `GeoPoint`s.
 */
data class RouteMapData(
    val route: ObaRoute?,
    val agencyName: String?,
    val stops: List<ObaStop>,
    val routes: List<ObaRoute>,
    val polylines: List<List<Location>>,
)
