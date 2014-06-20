/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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

import com.google.android.maps.GeoPoint;

import android.location.Location;

/**
 * Utilities to help process data for Android Maps API v1
 */
public class MapHelp {

    /**
     * Converts a latitude/longitude to a GeoPoint.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A GeoPoint representing this latitude/longitude.
     */
    public static final GeoPoint makeGeoPoint(double lat, double lon) {
        return new GeoPoint((int) (lat * 1E6), (int) (lon * 1E6));
    }

    /**
     * Converts a Location to a GeoPoint.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A GeoPoint representing this latitude/longitude.
     */
    public static final GeoPoint makeGeoPoint(Location l) {
        return makeGeoPoint(l.getLatitude(), l.getLongitude());
    }

    /**
     * Converts a GeoPoint to a Location.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A Location representing this latitude/longitude.
     */
    public static final Location makeLocation(GeoPoint p) {
        Location l = new Location("");
        l.setLatitude(p.getLatitudeE6() / 1E6);
        l.setLongitude(p.getLongitudeE6() / 1E6);
        return l;
    }
}
