/* Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com) */
package org.onebusaway.android.map

/** Intent keys describing the initial or frozen map state. */
object MapParams {
    const val STOP_ID = ".StopId"
    const val STOP_NAME = ".StopName"
    const val STOP_CODE = ".StopCode"
    const val ROUTE_ID = ".RouteId"
    const val ROUTE_DIRECTION_STOP_ID = ".RouteDirectionStopId"
    const val ROUTE_DIRECTION_ID = ".RouteDirectionId"
    const val CENTER_LAT = ".MapCenterLat"
    const val CENTER_LON = ".MapCenterLon"
    const val ZOOM = ".MapZoom"
    const val ZOOM_TO_ROUTE = ".ZoomToRoute"
    const val DEFAULT_ZOOM = 18
}
