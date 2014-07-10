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
package com.joulespersecond.seattlebusbot.util;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.region.RegionUtils;
import com.joulespersecond.seattlebusbot.Application;

import java.util.Iterator;
import java.util.List;

/**
 * Utilities to help obtain and process location data
 *
 * @author barbeau
 */
public class LocationHelp {

    public static final String TAG = "LocationHelp";
    public static final int DEFAULT_SEARCH_RADIUS = 15000;
    private static final float FUZZY_EQUALS_THRESHOLD = 15.0f;

    public static Location getDefaultSearchCenter() {
        ObaRegion region = Application.get().getCurrentRegion();
        if (region != null) {
            double results[] = new double[4];
            RegionUtils.getRegionSpan(region, results);
            return LocationHelp.makeLocation(results[2], results[3]);
        } else {
            return null;
        }
    }

    /**
     * We need to provide the API for a location used to disambiguate
     * stop IDs in case of collision, or to provide multiple results
     * in the case multiple agencies. But we really don't need it to be very
     * accurate.
     * <p/>
     * Note that the LocationClient must already have been initialized and connected prior to calling
     * this method, since LocationClient.connect() is asynchronous and doesn't connect before it returns,
     * which requires additional initialization time (prior to calling this method)
     *
     * @param cxt
     * @param client an initialized and connected LocationClient, or null if Google Play Services
     *               isn't available
     * @return a recent location, considering both Google Play Services (if available) and the Android Location API
     */
    public static Location getLocation(Context cxt, LocationClient client) {
        Location last = getLocation2(cxt, client);
        if (last != null) {
            return last;
        } else {
            return getDefaultSearchCenter();
        }
    }

    /**
     * Returns a location, considering both Google Play Services (if available) and the Android Location API
     * <p/>
     * Note that the LocationClient must already have been initialized and connected prior to calling
     * this method, since LocationClient.connect() is asynchronous and doesn't connect before it returns,
     * which requires additional initialization time (prior to calling this method)
     *
     * @param cxt
     * @param client an initialized and connected LocationClient, or null if Google Play Services
     *               isn't available
     * @return a recent location, considering both Google Play Services (if available) and the Android Location API
     */
    public static Location getLocation2(Context cxt, LocationClient client) {
        Location playServices = null;
        if (client != null &&
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(cxt) == ConnectionResult.SUCCESS
                && client.isConnected()) {
            playServices = client.getLastLocation();
            Log.d(TAG, "Got location from Google Play Services, testing against API v1...");
        }
        Location apiV1 = getLocationApiV1(cxt);

        if (compareLocations(playServices, apiV1)) {
            Log.d(TAG, "Using location from Google Play Services");
            return playServices;
        } else {
            Log.d(TAG, "Using location from Location API v1");
            return apiV1;
        }
    }

    private static Location getLocationApiV1(Context cxt) {
        LocationManager mgr = (LocationManager) cxt.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mgr.getProviders(true);
        Location last = null;
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            Location loc = mgr.getLastKnownLocation(i.next());
            // If this provider has a last location, and either:
            // 1. We don't have a last location,
            // 2. Our last location is older than this location.
            if (compareLocations(loc, last)) {
                last = loc;
            }
        }
        return last;
    }

    /**
     * Compares Location A to Location B - prefers a non-null location that is more recent.  Does
     * NOT take estimated accuracy into account.
     *
     * @param a
     * @param b
     * @return true if Location a is "better" than b, or false if b is "better" than a
     */
    public static boolean compareLocations(Location a, Location b) {
        return (a != null && (b == null || a.getTime() > b.getTime()));
    }

    /**
     * Converts a latitude/longitude to a Location.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A Location representing this latitude/longitude.
     */
    public static final Location makeLocation(double lat, double lon) {
        Location l = new Location("");
        l.setLatitude(lat);
        l.setLongitude(lon);
        return l;
    }

    /**
     * Returns true if the locations are approximately equal (i.e., within a certain distance
     * threshold)
     *
     * @param a first location
     * @param b second location
     * @return true if the locations are approximately equal, false if they are not
     */
    public static boolean fuzzyEquals(Location a, Location b) {
        return a.distanceTo(b) <= FUZZY_EQUALS_THRESHOLD;
    }

    /**
     * Class to handle Google Play Location Services callbacks
     */
    public static class LocationServicesCallback implements GooglePlayServicesClient.ConnectionCallbacks,
            GooglePlayServicesClient.OnConnectionFailedListener {
        private static final String TAG = "LocationServicesCallback";

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "GooglePlayServicesClient.onConnected");
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "GooglePlayServicesClient.onDisconnected");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(TAG, "GooglePlayServicesClient.onConnectionFailed");
        }
    }
}
