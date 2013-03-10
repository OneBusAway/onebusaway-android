/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba.region;

import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.elements.ObaRegion.Bounds;

import android.location.Location;

public class RegionUtils {

    /**
     * Returns the distance from the specified location
     * to the center of the closest bound in this region.
     */
    public static Float getDistanceAway(ObaRegion region, double lat, double lon) {
        Bounds[] bounds = region.getBounds();
        if (bounds == null) {
            return null;
        }
        float[] results = new float[1];
        float minDistance = Float.MAX_VALUE;
        for (Bounds bound: bounds) {
            Location.distanceBetween(lat, lon, bound.getLat(), bound.getLon(), results);
            if (results[0] < minDistance) {
                minDistance = results[0];
            }
        }
        return minDistance;
    }

    public static Float getDistanceAway(ObaRegion region, Location loc) {
        return getDistanceAway(region, loc.getLatitude(), loc.getLongitude());
    }

    /**
     * Returns the center and lat/lon span for the entire region.
     * @param results Array to receive results.
     *              results[0] == latSpan of region
     *              results[1] == lonSpan of region
     *              results[2] == lat center of region
     *              results[3] == lon center of region
     */
    public static void getRegionSpan(ObaRegion region, double[] results) {
        if (results.length < 4) {
            throw new IllegalArgumentException("Results array is < 4");
        }
        double latMin = 90;
        double latMax = -90;
        double lonMin = 180;
        double lonMax = -180;

        // This is fairly simplistic
        for (Bounds bound: region.getBounds()) {
            // Get the top bound
            double lat = bound.getLat();
            double latSpanHalf = bound.getLatSpan() / 2.0;
            double lat1 = lat - latSpanHalf;
            double lat2 = lat + latSpanHalf;
            if (lat1 < latMin) {
                latMin = lat1;
            }
            if (lat2 > latMax) {
                latMax = lat2;
            }

            double lon = bound.getLon();
            double lonSpanHalf = bound.getLonSpan() / 2.0;
            double lon1 = lon - lonSpanHalf;
            double lon2 = lon + lonSpanHalf;
            if (lon1 < lonMin) {
                lonMin = lon1;
            }
            if (lon2 > lonMax) {
                lonMax = lon2;
            }
        }

        results[0] = latMax - latMin;
        results[1] = lonMax - lonMin;
        results[2] = latMin + ((latMax - latMin) / 2.0);
        results[3] = lonMin + ((lonMax - lonMin) / 2.0);
    }
}
