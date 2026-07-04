/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.app.di.PreferencesEntryPoint;
import org.onebusaway.android.app.di.RegionEntryPoint;
import org.onebusaway.android.api.bridge.RegionsClient;
import org.onebusaway.android.region.Region;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * A class containing utility methods related to handling multiple regions in OneBusAway
 */
public class RegionUtils {

    private static final String TAG = "RegionUtils";

    public static final int TAMPA_REGION_ID = 0;

    public static final int PUGET_SOUND_REGION_ID = 1;

    public static final int ATLANTA_REGION_ID = 3;

    public static final double METERS_TO_MILES = 0.000621371;

    public static final double METERS_TO_FEET = 3.28084;

    private static final int DISTANCE_LIMITER = 100;  // miles

    /**
     * Get the closest region from a list of regions and a given location
     *
     * This method also enforces the constraints in isRegionUsable() to
     * ensure the returned region is actually usable by the app
     *
     * @param regions list of regions
     * @param loc     location
     * @param enforceThreshold true if the DISTANCE_LIMITER threshold should be enforced, false if
     *                         it should not
     * @return the closest region to the given location from the list of regions, or null if a
     * enforceThreshold is true and the closest region exceeded DISTANCE_LIMITER threshold or a
     * region couldn't be found
     */
    public static Region getClosestRegion(Context context, List<Region> regions, Location loc,
            boolean enforceThreshold) {
        if (loc == null) {
            return null;
        }
        float minDist = Float.MAX_VALUE;
        Region closestRegion = null;
        Float distToRegion;

        NumberFormat fmt = NumberFormat.getInstance();
        if (fmt instanceof DecimalFormat) {
            ((DecimalFormat) fmt).setMaximumFractionDigits(1);
        }
        double miles;

        Log.d(TAG, "Finding region closest to " + loc.getLatitude() + "," + loc.getLongitude());

        for (Region region : regions) {
            if (!isRegionUsable(context, region)) {
                Log.d(TAG,
                        "Excluding '" + region.getName() + "' from 'closest region' consideration");
                continue;
            }

            distToRegion = getDistanceAway(region, loc.getLatitude(), loc.getLongitude());
            if (distToRegion == null) {
                Log.e(TAG, "Couldn't measure distance to region '" + region.getName() + "'");
                continue;
            }
            miles = distToRegion * METERS_TO_MILES;
            Log.d(TAG, "Region '" + region.getName() + "' is " + fmt.format(miles) + " miles away");
            if (distToRegion < minDist) {
                closestRegion = region;
                minDist = distToRegion;
            }
        }

        if (enforceThreshold) {
            if (minDist * METERS_TO_MILES < DISTANCE_LIMITER) {
                return closestRegion;
            } else {
                return null;
            }
        }
        return closestRegion;
    }

    /**
     * Get the region name if it is available. If there is a custom url instead of a region from
     * the region api, then hash the custom url and return it.
     *
     * @return regionName
     */
    public static String getObaRegionName(Context context) {
        String regionName = null;
        Region region = RegionEntryPoint.get(context).currentRegion();
        if (region != null && region.getName() != null) {
            regionName = region.getName();
        } else {
            String customApiUrl = PreferencesEntryPoint.get(context)
                    .getString(context.getString(R.string.preference_key_oba_api_url), (String) null);
            if (customApiUrl != null) {
                regionName = createHashCode(context, customApiUrl.getBytes());
            }
        }
        return regionName;
    }

    private static String createHashCode(Context context, byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
            digest.update(bytes);
            return context.getString(R.string.analytics_label_custom_url) +
                    ": " + Application.getHex(digest.digest());
        } catch (Exception e) {
            return context.getString(R.string.analytics_label_custom_url);
        }
    }

    /**
     * Returns the distance from the specified location
     * to the center of the closest bound in this region.
     *
     * @return distance from the specified location to the center of the closest bound in this
     * region, in meters
     */
    public static Float getDistanceAway(Region region, double lat, double lon) {
        Region.Bounds[] bounds = region.getBounds();
        if (bounds == null) {
            return null;
        }
        float[] results = new float[1];
        float minDistance = Float.MAX_VALUE;
        for (Region.Bounds bound : bounds) {
            Location.distanceBetween(lat, lon, bound.getLat(), bound.getLon(), results);
            if (results[0] < minDistance) {
                minDistance = results[0];
            }
        }
        return minDistance;
    }

    public static Float getDistanceAway(Region region, Location loc) {
        return getDistanceAway(region, loc.getLatitude(), loc.getLongitude());
    }

    /**
     * Returns the center and lat/lon span for the entire region.
     *
     * @param results Array to receive results.
     *                results[0] == latSpan of region
     *                results[1] == lonSpan of region
     *                results[2] == lat center of region
     *                results[3] == lon center of region
     */
    public static void getRegionSpan(Region region, double[] results) {
        if (results.length < 4) {
            throw new IllegalArgumentException("Results array is < 4");
        }
        if (region == null) {
            throw new IllegalArgumentException("Region is null");
        }
        double latMin = 90;
        double latMax = -90;
        double lonMin = 180;
        double lonMax = -180;

        // This is fairly simplistic
        for (Region.Bounds bound : region.getBounds()) {
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

    /**
     * Determines if the provided location is within the provided region span
     *
     * Note: This does not handle cases when the region span crosses the
     * International Date Line properly
     *
     * @param location   that will be compared to the provided regionSpan
     * @param regionSpan span information for the region
     *                   regionSpan[0] == latSpan of region
     *                   regionSpan[1] == lonSpan of region
     *                   regionSpan[2] == lat center of region
     *                   regionSpan[3] == lon center of region
     * @return true if the location is within the region span, false if it is not
     */
    public static boolean isLocationWithinRegion(Location location, double[] regionSpan) {
        if (regionSpan == null || regionSpan.length < 4) {
            throw new IllegalArgumentException("regionSpan is null or has length < 4");
        }

        if (location == null || location.getLongitude() > 180.0 || location.getLongitude() < -180.0
                ||
                location.getLatitude() > 90 || location.getLatitude() < -90) {
            throw new IllegalArgumentException("Location must be a valid location");
        }

        double minLat = regionSpan[2] - (regionSpan[0] / 2);
        double minLon = regionSpan[3] - (regionSpan[1] / 2);
        double maxLat = regionSpan[2] + (regionSpan[0] / 2);
        double maxLon = regionSpan[3] + (regionSpan[1] / 2);

        return minLat <= location.getLatitude() && location.getLatitude() <= maxLat
                && minLon <= location.getLongitude() && location.getLongitude() <= maxLon;
    }

    /**
     * Determines if the provided location is within the provided region
     *
     * Note: This does not handle cases when the region span crosses the
     * International Date Line properly
     *
     * @param location that will be compared to the provided region
     * @param region   provided region
     * @return true if the location is within the region, false if it is not
     */
    public static boolean isLocationWithinRegion(Location location, Region region) {
        double[] regionSpan = new double[4];
        getRegionSpan(region, regionSpan);
        return isLocationWithinRegion(location, regionSpan);
    }

    /**
     * Checks if the given region is usable by the app, based on what this app supports
     * - Is the region active?
     * - Does the region support the OBA Discovery APIs?
     * - Does the region support the OBA Realtime APIs?
     * - Is the region experimental, and if so, did the user opt-in via preferences?
     *
     * @param region region to be checked
     * @return true if the region is usable by this application, false if it is not
     */
    public static boolean isRegionUsable(Context context, Region region) {
        if (!region.getActive()) {
            Log.d(TAG, "Region '" + region.getName() + "' is not active.");
            return false;
        }
        if (!region.getSupportsObaDiscoveryApis()) {
            Log.d(TAG, "Region '" + region.getName() + "' does not support OBA Discovery APIs.");
            return false;
        }
        if (!region.getSupportsObaRealtimeApis()) {
            Log.d(TAG, "Region '" + region.getName() + "' does not support OBA Realtime APIs.");
            return false;
        }
        if (region.getExperimental() && !PreferenceUtils.getBoolean(
                context.getString(R.string.preference_key_experimental_regions), false)) {
            Log.d(TAG,
                    "Region '" + region.getName() + "' is experimental and user hasn't opted in.");
            return false;
        }

        return true;
    }

    /**
     * Format the OTP base URL so query parameters can be added safely.
     *
     * @param baseUrl OpenTripPlanner base URL from the Region
     * @return OTP server URL with trailing slash trimmed.
     */
    public static String formatOtpBaseUrl(String baseUrl) {
        return baseUrl.replaceFirst("/$", "");
    }



    public synchronized static ArrayList<Region> getRegionsFromServer(Context context) {
        return new ArrayList<Region>(RegionsClient.fetchRegionsFromServer(context));
    }

    /**
     * Retrieves region information from a regions file bundled within the app APK
     *
     * IMPORTANT - this should be a last resort, and we should always try to pull regions
     * info from the cached regions or the Regions REST API instead of from the bundled file.
     *
     * This method is only intended to be a fail-safe in case the Regions REST API goes
     * offline and a user downloads and installs OBA Android during that period
     * (i.e., local OBA servers are available, but Regions REST API failure would block initial
     * execution of the app).  This avoids a potential central point of failure for OBA
     * Android installations on devices in multiple regions.
     *
     * @return list of regions retrieved from the regions file in app resources
     */
    public static ArrayList<Region> getRegionsFromResources(Context context) {
        return new ArrayList<Region>(RegionsClient.parseBundledRegions(context));
    }

    /**
     * Retrieves hard-coded region information from the build flavor defined in build.gradle.
     * If a fixed region is defined in a build flavor, it does not allow region roaming.
     *
     * @return hard-coded region information from the build flavor defined in build.gradle
     */
    public static Region getRegionFromBuildFlavor() {
        final int regionId = Integer.MAX_VALUE; // This doesn't get used, but needs to be positive
        Region.Bounds[] boundsArray = new Region.Bounds[1];
        Region.Bounds bounds = new Region.Bounds(
                BuildConfig.FIXED_REGION_BOUNDS_LAT, BuildConfig.FIXED_REGION_BOUNDS_LON,
                BuildConfig.FIXED_REGION_BOUNDS_LAT_SPAN, BuildConfig.FIXED_REGION_BOUNDS_LON_SPAN);
        boundsArray[0] = bounds;

        Region.Open311Server[] open311Array = new Region.Open311Server[1];
        Region.Open311Server open311Server;

        if (BuildConfig.FIXED_REGION_OPEN311_BASE_URL != null) {
            open311Server = new Region.Open311Server (
                    BuildConfig.FIXED_REGION_OPEN311_JURISDICTION_ID,
                    BuildConfig.FIXED_REGION_OPEN311_API_KEY,
                    BuildConfig.FIXED_REGION_OPEN311_BASE_URL);
            open311Array[0] = open311Server;
        } else {
            open311Array = null;
        }

        Region region = new Region(regionId,
                BuildConfig.FIXED_REGION_NAME, true,
                BuildConfig.FIXED_REGION_OBA_BASE_URL, BuildConfig.FIXED_REGION_SIRI_BASE_URL,
                boundsArray, open311Array, BuildConfig.FIXED_REGION_LANG,
                BuildConfig.FIXED_REGION_CONTACT_EMAIL,
                BuildConfig.FIXED_REGION_SUPPORTS_OBA_DISCOVERY_APIS,
                BuildConfig.FIXED_REGION_SUPPORTS_OBA_REALTIME_APIS,
                BuildConfig.FIXED_REGION_SUPPORTS_SIRI_REALTIME_APIS,
                BuildConfig.FIXED_REGION_TWITTER_URL, false,
                BuildConfig.FIXED_REGION_STOP_INFO_URL,
                BuildConfig.FIXED_REGION_OTP_BASE_URL,
                BuildConfig.FIXED_REGION_OTP_CONTACT_EMAIL,
                BuildConfig.FIXED_REGION_SUPPORTS_OTP_BIKESHARE,
                false,
                BuildConfig.FIXED_REGION_PAYMENT_ANDROID_APP_ID,
                BuildConfig.FIXED_REGION_PAYMENT_WARNING_TITLE,
                BuildConfig.FIXED_REGION_PAYMENT_WARNING_BODY,
                BuildConfig.FIXED_REGION_SIDECAR_BASE_URL,
                BuildConfig.FIXED_REGION_PLAUSIBLE_ANALYTICS_SERVER_URL,
                null);   // No Umami config for the fixed-region build flavor
        return region;
    }

}
