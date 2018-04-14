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
package org.onebusaway.android.map;

public class MapParams {

    // These specify the initial state of the map, or the frozen state.
    public static final String MODE = ".MapMode";

    public static final String STOP_ID = ".StopId";

    public static final String STOP_NAME = ".StopName";

    public static final String STOP_CODE = ".StopCode";

    public static final String BIKE_STATION_ID = ".BikeStationId";

    public static final String ROUTE_ID = ".RouteId";

    public static final String DO_N0T_CENTER_ON_LOCATION = ".DoNotCenterOnLocation";

    public static final String CENTER_LAT = ".MapCenterLat";

    public static final String CENTER_LON = ".MapCenterLon";

    public static final String ZOOM = ".MapZoom";

    public static final String ZOOM_TO_ROUTE = ".ZoomToRoute";

    public static final String ZOOM_INCLUDE_CLOSEST_VEHICLE = ".ZoomIncludesClosestVehicle";

    public static final String MAP_PADDING_LEFT = ".MapPaddingLeft";

    public static final String MAP_PADDING_TOP = ".MapPaddingTop";

    public static final String MAP_PADDING_RIGHT = ".MapPaddingRight";

    public static final String MAP_PADDING_BOTTOM = ".MapPaddingBottom";

    public static final String SHOW_BIKE = ".ShowBike";

    public static final String MODE_ROUTE = "RouteMode";

    public static final String MODE_STOP = "StopMode";

    public static final String MODE_DIRECTIONS = "DirectionsMode";

    public static final String ITINERARY = ".Itinerary";

    public static final int DEFAULT_ZOOM = 18;

    public static final int DEFAULT_MAP_PADDING = 0;
}
