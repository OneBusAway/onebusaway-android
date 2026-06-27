/*
 * Copyright (C) 2014-2024 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.map.maplibre;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;

import org.onebusaway.android.region.Region;

import android.location.Location;
import android.util.Log;


/**
 * Utilities to help process data for MapLibre maps
 */
public class MapHelpMapLibre {

    public static final String TAG = "MapHelpMapLibre";

    public static LatLng makeLatLng(double lat, double lon) {
        return new LatLng(lat, lon);
    }

    public static LatLng makeLatLng(Location l) {
        return makeLatLng(l.getLatitude(), l.getLongitude());
    }

    public static Location makeLocation(LatLng latLng) {
        Location l = new Location("FromLatLng");
        l.setLatitude(latLng.getLatitude());
        l.setLongitude(latLng.getLongitude());
        return l;
    }

    public static LatLngBounds getRegionBounds(Region region) {
        if (region == null) {
            throw new IllegalArgumentException("Region is null");
        }
        double latMin = 90;
        double latMax = -90;
        double lonMin = 180;
        double lonMax = -180;

        for (Region.Bounds bound : region.getBounds()) {
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

        return new LatLngBounds.Builder()
                .include(makeLatLng(latMin, lonMin))
                .include(makeLatLng(latMax, lonMax))
                .build();
    }

    /**
     * MapLibre is bundled with the app, so it is always "installed".
     */
    public static boolean isMapsInstalled() {
        return true;
    }

}
