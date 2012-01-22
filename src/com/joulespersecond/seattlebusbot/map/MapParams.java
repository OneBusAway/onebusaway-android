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

    public static final int DEFAULT_ZOOM = 17;
}
