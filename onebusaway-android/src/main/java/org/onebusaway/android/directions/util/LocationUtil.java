/*
 * Copyright 2012 University of South Florida
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package org.onebusaway.android.directions.util;


import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.RegionUtils;


/**
 * Various utilities related to location data
 *
 * @author Khoa Tran
 * @author Simon Jacobs (integration for onebusaway-android)
 */


public class LocationUtil {

    private static final String TAG = "LocationUtil";
    private static final int GEOCODER_MAX_RESULTS = 5;
    //in meters
    private static final int GEOCODING_MAX_ERROR = 100;

    public static ArrayList<CustomAddress> processGeocoding(Context context, ObaRegion region,
                                                            String... reqs) {
        return processGeocoding(context, region, false, reqs);
    }

    public static ArrayList<CustomAddress> processGeocoding(Context context, ObaRegion region, boolean geocodingForMarker, String... reqs) {
        ArrayList<CustomAddress> addressesReturn = new ArrayList<CustomAddress>();

        String address = reqs[0];
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

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
            Log.d(TAG, "Geocoding without reference latitude/longitude");
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

        List<CustomAddress> addresses = new ArrayList<CustomAddress>();

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

    public static LatLngBounds getRegionBounds(ObaRegion region) {

        double[] regionSpan = new double[4];
        RegionUtils.getRegionSpan(region, regionSpan);
        double minLat = regionSpan[2] - (regionSpan[0] / 2);
        double minLon = regionSpan[3] - (regionSpan[1] / 2);
        double maxLat = regionSpan[2] + (regionSpan[0] / 2);
        double maxLon = regionSpan[3] + (regionSpan[1] / 2);

        LatLng sw = new LatLng(minLat, minLon);
        LatLng ne = new LatLng(maxLat, maxLon);

        return new LatLngBounds(sw, ne);
    }
}
