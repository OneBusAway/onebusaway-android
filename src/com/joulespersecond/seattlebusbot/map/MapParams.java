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
package com.joulespersecond.seattlebusbot.map;

public class MapParams {
    // These specify the initial state of the map, or the frozen state.
    public static final String MODE = ".MapMode";
    public static final String STOP_ID = ".StopId";
    public static final String ROUTE_ID = ".RouteId";
    public static final String CENTER_LAT = ".MapCenterLat";
    public static final String CENTER_LON = ".MapCenterLon";
    public static final String ZOOM = ".MapZoom";
    public static final String ZOOM_TO_ROUTE = ".ZoomToRoute";

    public static final String MODE_ROUTE = "RouteMode";
    public static final String MODE_STOP = "StopMode";

    public static final int DEFAULT_ZOOM = 18;
}
