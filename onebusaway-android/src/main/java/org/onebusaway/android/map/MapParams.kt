/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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

/** Intent keys describing the initial or frozen map state. */
object MapParams {
    const val STOP_ID = ".StopId"
    const val STOP_NAME = ".StopName"
    const val STOP_CODE = ".StopCode"
    const val ROUTE_ID = ".RouteId"

    /**
     * The stop a "show vehicles on map" launch anchored to, so route mode restores its direction
     * filter.
     */
    const val ROUTE_DIRECTION_STOP_ID = ".RouteDirectionStopId"

    /**
     * The user-selected direction (via the route header's switch), restored across process death.
     * Wins over [ROUTE_DIRECTION_STOP_ID] when it is still a valid direction of the restored route.
     */
    const val ROUTE_DIRECTION_ID = ".RouteDirectionId"

    /**
     * The headsign of the direction a launch means, when the caller knows it outright rather than
     * inferring it from a stop — a tracked arrivals row is one direction by definition (#2166), so it
     * can name it instead of leaving the map to work it out from [ROUTE_DIRECTION_STOP_ID].
     */
    const val ROUTE_DIRECTION_HEADSIGN = ".RouteDirectionHeadsign"

    /** The route's display name, for a launch that selects a route row and must label its leg. */
    const val ROUTE_SHORT_NAME = ".RouteShortName"
    const val CENTER_LAT = ".MapCenterLat"
    const val CENTER_LON = ".MapCenterLon"
    const val ZOOM = ".MapZoom"
    const val ZOOM_TO_ROUTE = ".ZoomToRoute"
    const val DEFAULT_ZOOM = 18
}
