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
package org.onebusaway.android.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Utilities to help obtain and process location data
 *
 * @author barbeau
 */
public class LocationUtils {

    public static final String TAG = "LocationUtil";

    public static final int DEFAULT_SEARCH_RADIUS = 40000;

    private static final float FUZZY_EQUALS_THRESHOLD = 15.0f;

    public static final float ACC_THRESHOLD = 50f;  // 50 meters

    public static final long TIME_THRESHOLD = TimeUnit.MINUTES.toMillis(10);  // 10 minutes

    public static Location getDefaultSearchCenter() {
        ObaRegion region = Application.get().getCurrentRegion();
        if (region != null) {
            double results[] = new double[4];
            RegionUtils.getRegionSpan(region, results);
            return LocationUtils.makeLocation(results[2], results[3]);
        } else {
            return null;
        }
    }

    /**
     * Compares Location A to Location B - prefers a non-null location that is more recent.  Does
     * NOT take estimated accuracy into account.
     * @param a first location to compare
     * @param b second location to compare
     * @return true if Location a is "better" than b, or false if b is "better" than a
     */
    public static boolean compareLocationsByTime(Location a, Location b) {
        return (a != null && (b == null || a.getTime() > b.getTime()));
    }

    /**
     * Compares Location A to Location B, considering timestamps and accuracy of locations.
     * Typically
     * this is used to compare a new location delivered by a LocationListener (Location A) to
     * a previously saved location (Location B).
     *
     * @param a location to compare
     * @param b location to compare against
     * @return true if Location a is "better" than b, or false if b is "better" than a
     */
    public static boolean compareLocations(Location a, Location b) {
        if (a == null) {
            // New location isn't valid, return false
            return false;
        }
        // If the new location is the first location, save it
        if (b == null) {
            return true;
        }

        // If the last location is older than TIME_THRESHOLD minutes, and the new location is more recent,
        // save the new location, even if the accuracy for new location is worse
        if (System.currentTimeMillis() - b.getTime() > TIME_THRESHOLD
                && compareLocationsByTime(a, b)) {
            return true;
        }

        // If the new location has an accuracy better than ACC_THRESHOLD and is newer than the last location, save it
        if (a.getAccuracy() < ACC_THRESHOLD && compareLocationsByTime(a, b)) {
            return true;
        }

        // If we get this far, A isn't better than B
        return false;
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
     * Returns true if the user has enabled location services on their device, false if they have
     * not
     *
     * from http://stackoverflow.com/a/22980843/937715
     *
     * @return true if the user has enabled location services on their device, false if they have
     * not
     */
    public static boolean isLocationEnabled(Context context) {
        int locationMode = Settings.Secure.LOCATION_MODE_OFF;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure
                        .getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }

    /**
     * Returns the human-readable details of a Location (provider, lat/long, accuracy, timestamp)
     *
     * @return the details of a Location (provider, lat/long, accuracy, timestamp) in a string
     */
    public static String printLocationDetails(Location loc) {
        if (loc == null) {
            return "";
        }

        long timeDiff;
        double timeDiffSec;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            timeDiff = SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos();
            // Convert to seconds
            timeDiffSec = timeDiff / 1E9;
        } else {
            timeDiff = System.currentTimeMillis() - loc.getTime();
            timeDiffSec = timeDiff / 1E3;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(loc.getProvider());
        sb.append(' ');
        sb.append(loc.getLatitude());
        sb.append(',');
        sb.append(loc.getLongitude());
        if (loc.hasAccuracy()) {
            sb.append(' ');
            sb.append(loc.getAccuracy());
        }
        sb.append(", ");
        sb.append(String.format("%.0f", timeDiffSec) + " second(s) ago");

        return sb.toString();
    }

    /**
     * Returns a new GoogleApiClient which includes LocationServicesCallbacks
     */
    public static GoogleApiClient getGoogleApiClientWithCallbacks(Context context) {
        LocationServicesCallback locCallback = new LocationServicesCallback();
        return new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(locCallback)
                .addOnConnectionFailedListener(locCallback)
                .build();
    }

    /**
     * Checks if string is a valid zip code.
     * E.g, #####-#### or #####.
     * @param zip ZIP Code as String.
     * @return Whether string is valid zip.
     */
    public static boolean isValidZipCode(String zip)
    {
        Pattern pattern = Pattern.compile("^\\d{5}(?:-\\d{4})?$");
        return pattern.matcher(zip).matches();
    }

    /**
     * Attempts to retrieves location from zip code.
     * Note: This method makes server calls & blocks thread. Do not
     * run this on the UI thread.
     * @param context App Context.
     * @param zip Zip Code string.
     * @return Location containing latitude & longitude. Null if fails.
     */
    public static Location getLocationFromZip(Context context, String zip)
    {
        try {
            Geocoder geocoder = new Geocoder(context);
            List<Address> addressList = geocoder.getFromLocationName(zip, 1);
            if (addressList != null && !addressList.isEmpty()) {
                Address addr = addressList.get(0);
                Location l = new Location("zip");
                l.setLatitude(addr.getLatitude());
                l.setLongitude(addr.getLongitude());
                l.setAccuracy(1000);
                return l;
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Class to handle Google Play Location Services callbacks
     */
    public static class LocationServicesCallback
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        private static final String TAG = "LocationServicesCallback";

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "GoogleApiClient.onConnected");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "GoogleApiClient.onConnectionSuspended");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(TAG, "GoogleApiClient.onConnectionFailed");
        }
    }
}
