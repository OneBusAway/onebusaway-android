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
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.io.elements.ObaRegion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import edu.usf.cutr.pelias.AutocompleteRequest;
import edu.usf.cutr.pelias.PeliasRequest;
import edu.usf.cutr.pelias.PeliasResponse;

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

    private static final int GEOCODER_MAX_RESULTS = 5;
    //in meters
    private static final int GEOCODING_MAX_ERROR = 100;


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
     * Check if two locations are the exact same by comparing their timestamp, lat & lng.
     * @param a First location
     * @param b Second location
     * @return true if same, false otherwise.
     */
    public static boolean isDuplicate(Location a, Location b) {
        if (a.getTime() != b.getTime())
            return false;

        if (a.getLatitude() != b.getLatitude())
            return false;

        if (a.getLongitude() != b.getLongitude())
            return false;

        return true;
    }

    /**
     * Converts a latitude/longitude to a Location.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A Location representing this latitude/longitude.
     */
    public static Location makeLocation(double lat, double lon) {
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
        return getLocationMode(context) != Settings.Secure.LOCATION_MODE_OFF;
    }
    
    /**
     * This method is used to get Integer representation of location mode
     *
     * @param context
     * @return location mode for passed context
     */
    public static int getLocationMode(Context context) {
        int locationMode = Settings.Secure.LOCATION_MODE_OFF;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure
                        .getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED);

            if (TextUtils.isEmpty(locationProviders)) {
                locationMode = Settings.Secure.LOCATION_MODE_OFF;
            } else if (locationProviders.contains(LocationManager.GPS_PROVIDER)) {
                locationMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
            } else if (locationProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                locationMode = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
            }
        }
        return locationMode;
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
     * Class to handle Google Play Location Services callbacks
     */
    public static class LocationServicesCallback
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        private static final String TAG = "LocationServicesCallbck";

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

    public static List<CustomAddress> processGooglePlacesGeocoding(Context context, ObaRegion region,
                                                                   String... reqs) {
        return processGeocoding(context, region, false, reqs);
    }

    public static List<CustomAddress> processGeocoding(Context context, ObaRegion region, boolean geocodingForMarker, String... reqs) {
        ArrayList<CustomAddress> addressesReturn = new ArrayList<CustomAddress>();

        String address = reqs[0];

        if (address == null || address.equalsIgnoreCase("")) {
            return null;
        }

        double latitude = 0, longitude = 0;
        boolean latLngSet = false;

        try {
            if (reqs.length >= 3) {
                latitude = Double.parseDouble(reqs[1]);
                longitude = Double.parseDouble(reqs[2]);
                latLngSet = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoding without reference latitude/longitude");
        }

        if (address.equalsIgnoreCase(context.getString(R.string.tripplanner_current_location))) {
            if (latLngSet) {
                CustomAddress addressReturn = new CustomAddress(context.getResources().getConfiguration().locale);
                addressReturn.setLatitude(latitude);
                addressReturn.setLongitude(longitude);
                addressReturn.setAddressLine(addressReturn.getMaxAddressLineIndex() + 1,
                        context.getString(R.string.tripplanner_current_location));

                addressesReturn.add(addressReturn);

                return addressesReturn;
            }
            return null;
        }

        List<CustomAddress> addresses = new ArrayList<>();

        // Originally checks app preferences. Could add this as a preference.

        Geocoder gc = new Geocoder(context);
        try {
            List<Address> androidTypeAddresses;
            if (region != null) {

                double[] regionSpan = new double[4];
                RegionUtils.getRegionSpan(region, regionSpan);
                double minLat = regionSpan[2] - (regionSpan[0] / 2);
                double minLon = regionSpan[3] - (regionSpan[1] / 2);
                double maxLat = regionSpan[2] + (regionSpan[0] / 2);
                double maxLon = regionSpan[3] + (regionSpan[1] / 2);

                androidTypeAddresses = gc.getFromLocationName(address,
                        GEOCODER_MAX_RESULTS, minLat, minLon, maxLat, maxLon);
            } else {
                androidTypeAddresses = gc.getFromLocationName(address,
                        GEOCODER_MAX_RESULTS);
            }
            for (Address androidTypeAddress : androidTypeAddresses) {
                addresses.add(new CustomAddress(androidTypeAddress));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        addresses = filterAddressesBBox(region, addresses);

        boolean resultsCloseEnough = true;

        if (geocodingForMarker && latLngSet) {
            float results[] = new float[1];
            resultsCloseEnough = false;

            for (CustomAddress addressToCheck : addresses) {
                Location.distanceBetween(latitude, longitude,
                        addressToCheck.getLatitude(), addressToCheck.getLongitude(), results);
                if (results[0] < GEOCODING_MAX_ERROR) {
                    resultsCloseEnough = true;
                    break;
                }
            }
        }

        if ((addresses == null) || addresses.isEmpty() || !resultsCloseEnough) {
            if (addresses == null) {
                addresses = new ArrayList<CustomAddress>();
            }
            Log.e(TAG, "Geocoder did not find enough addresses: " + addresses);
        }

        addresses = filterAddressesBBox(region, addresses);

        if (geocodingForMarker && latLngSet && addresses != null && !addresses.isEmpty()) {
            float results[] = new float[1];
            float minDistanceToOriginalLatLon = Float.MAX_VALUE;
            CustomAddress closestAddress = addresses.get(0);

            for (CustomAddress addressToCheck : addresses) {
                Location.distanceBetween(latitude, longitude,
                        addressToCheck.getLatitude(), addressToCheck.getLongitude(), results);
                if (results[0] < minDistanceToOriginalLatLon) {
                    closestAddress = addressToCheck;
                    minDistanceToOriginalLatLon = results[0];
                }
            }
            addressesReturn.add(closestAddress);
        } else {
            addressesReturn.addAll(addresses);
        }

        return addressesReturn;
    }

    public static List<CustomAddress> processPeliasGeocoding(Context context, ObaRegion region,
            String... reqs) {
        return processPeliasGeocoding(context, region, false, reqs);
    }

    public static List<CustomAddress> processPeliasGeocoding(Context context, ObaRegion region, boolean geocodingForMarker, String... reqs) {
        ArrayList<CustomAddress> addressesReturn = new ArrayList<CustomAddress>();

        String address = reqs[0];

        if (address == null || address.equalsIgnoreCase("")) {
            return null;
        }

        double latitude = 0, longitude = 0;
        boolean latLngSet = false;

        try {
            if (reqs.length >= 3) {
                latitude = Double.parseDouble(reqs[1]);
                longitude = Double.parseDouble(reqs[2]);
                latLngSet = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoding without reference latitude/longitude");
        }

        if (address.equalsIgnoreCase(context.getString(R.string.tripplanner_current_location))) {
            if (latLngSet) {
                CustomAddress addressReturn = new CustomAddress(context.getResources().getConfiguration().locale);
                addressReturn.setLatitude(latitude);
                addressReturn.setLongitude(longitude);
                addressReturn.setAddressLine(addressReturn.getMaxAddressLineIndex() + 1,
                        context.getString(R.string.tripplanner_current_location));

                addressesReturn.add(addressReturn);

                return addressesReturn;
            }
            return null;
        }

        List<CustomAddress> addresses = new ArrayList<>();
        try {
            String apiKey = BuildConfig.PELIAS_API_KEY;
            PeliasRequest.Builder requestBuilder = new AutocompleteRequest.Builder(apiKey, address)
                    .setApiEndpoint(Application.get().getString(R.string.pelias_api_url));

            if (region != null) {
                double[] regionSpan = new double[4];
                RegionUtils.getRegionSpan(region, regionSpan);
                double minLat = regionSpan[2] - (regionSpan[0] / 2);
                double minLon = regionSpan[3] - (regionSpan[1] / 2);
                double maxLat = regionSpan[2] + (regionSpan[0] / 2);
                double maxLon = regionSpan[3] + (regionSpan[1] / 2);

                requestBuilder.setBoundaryRect(minLat, minLon, maxLat, maxLon);
            }

            // Call the Pelias API
            PeliasResponse response = requestBuilder.build().call();
            for (org.geojson.Feature feature : response.getFeatures()) {
                addresses.add(new CustomAddress(feature));
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        addresses = filterAddressesBBox(region, addresses);

        boolean resultsCloseEnough = true;

        if (geocodingForMarker && latLngSet) {
            float results[] = new float[1];
            resultsCloseEnough = false;

            for (CustomAddress addressToCheck : addresses) {
                Location.distanceBetween(latitude, longitude,
                        addressToCheck.getLatitude(), addressToCheck.getLongitude(), results);
                if (results[0] < GEOCODING_MAX_ERROR) {
                    resultsCloseEnough = true;
                    break;
                }
            }
        }

        if ((addresses == null) || addresses.isEmpty() || !resultsCloseEnough) {
            if (addresses == null) {
                addresses = new ArrayList<CustomAddress>();
            }
            Log.e(TAG, "Geocoder did not find enough addresses: " + addresses);
        }

        addresses = filterAddressesBBox(region, addresses);

        if (geocodingForMarker && latLngSet && addresses != null && !addresses.isEmpty()) {
            float results[] = new float[1];
            float minDistanceToOriginalLatLon = Float.MAX_VALUE;
            CustomAddress closestAddress = addresses.get(0);

            for (CustomAddress addressToCheck : addresses) {
                Location.distanceBetween(latitude, longitude,
                        addressToCheck.getLatitude(), addressToCheck.getLongitude(), results);
                if (results[0] < minDistanceToOriginalLatLon) {
                    closestAddress = addressToCheck;
                    minDistanceToOriginalLatLon = results[0];
                }
            }
            addressesReturn.add(closestAddress);
        } else {
            addressesReturn.addAll(addresses);
        }

        return addressesReturn;
    }

    /**
     * Filters the addresses obtained in geocoding process, removing the
     * results outside server limits.
     *
     * @param addresses list of addresses to filter
     * @return a new list filtered
     */
    private static List<CustomAddress> filterAddressesBBox(ObaRegion region, List<CustomAddress> addresses) {
        if ((!(addresses == null || addresses.isEmpty())) && region != null) {
            for (Iterator<CustomAddress> it = addresses.iterator(); it.hasNext(); ) {
                CustomAddress address = it.next();

                Location loc = new Location("");
                loc.setLatitude(address.getLatitude());
                loc.setLongitude(address.getLongitude());

                if (!RegionUtils.isLocationWithinRegion(loc, region)) {
                    it.remove();
                }
            }
        }
        return addresses;
    }

}
