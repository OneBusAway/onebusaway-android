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

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;

import android.location.Location;
import android.util.Log;

import java.util.HashSet;

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

    public static LatLngBounds getRegionBounds(ObaRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("Region is null");
        }
        double latMin = 90;
        double latMax = -90;
        double lonMin = 180;
        double lonMax = -180;

        for (ObaRegion.Bounds bound : region.getBounds()) {
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

    /**
     * Gets the location of the vehicle closest to the provided location running the provided
     * routes.
     */
    public static LatLng getClosestVehicle(ObaTripsForRouteResponse response,
            HashSet<String> routeIds, Location loc) {
        if (loc == null) {
            return null;
        }
        float minDist = Float.MAX_VALUE;
        ObaTripStatus closestVehicle = null;
        Location closestVehicleLocation = null;

        for (ObaTripDetails detail : response.getTrips()) {
            Location vehicleLocation;
            ObaTripStatus status = detail.getStatus();
            if (status == null) {
                continue;
            }
            String activeRoute = response.getTrip(status.getActiveTripId()).getRouteId();
            if (!routeIds.contains(activeRoute)) {
                continue;
            }
            if (status.getLastKnownLocation() != null) {
                vehicleLocation = status.getLastKnownLocation();
            } else if (status.getPosition() != null) {
                vehicleLocation = status.getPosition();
            } else {
                continue;
            }
            float distToVehicle = vehicleLocation.distanceTo(loc);

            if (distToVehicle < minDist) {
                closestVehicleLocation = vehicleLocation;
                closestVehicle = status;
                minDist = distToVehicle;
            }
        }

        if (closestVehicleLocation == null) {
            return null;
        }

        Log.d(TAG, "Closest vehicle is vehicleId=" + closestVehicle.getVehicleId()
                + ", tripId=" + closestVehicle.getActiveTripId()
                + " at " + closestVehicleLocation.getLatitude()
                + "," + closestVehicleLocation.getLongitude());

        return makeLatLng(closestVehicleLocation);
    }
}
