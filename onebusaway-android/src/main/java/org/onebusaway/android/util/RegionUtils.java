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
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRegionElement;
import org.onebusaway.android.io.request.ObaRegionsRequest;
import org.onebusaway.android.io.request.ObaRegionsResponse;
import org.onebusaway.android.provider.ObaContract;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
    public static ObaRegion getClosestRegion(ArrayList<ObaRegion> regions, Location loc,
            boolean enforceThreshold) {
        if (loc == null) {
            return null;
        }
        float minDist = Float.MAX_VALUE;
        ObaRegion closestRegion = null;
        Float distToRegion;

        NumberFormat fmt = NumberFormat.getInstance();
        if (fmt instanceof DecimalFormat) {
            ((DecimalFormat) fmt).setMaximumFractionDigits(1);
        }
        double miles;

        Log.d(TAG, "Finding region closest to " + loc.getLatitude() + "," + loc.getLongitude());

        for (ObaRegion region : regions) {
            if (!isRegionUsable(region)) {
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
    public static String getObaRegionName() {
        String regionName = null;
        ObaRegion region = Application.get().getCurrentRegion();
        if (region != null && region.getName() != null) {
            regionName = region.getName();
        } else if (Application.get().getCustomApiUrl() != null) {
            regionName = createHashCode(Application.get().getCustomApiUrl().getBytes());
        }
        return regionName;
    }

    private static String createHashCode(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
            digest.update(bytes);
            return Application.get().getString(R.string.analytics_label_custom_url) +
                    ": " + Application.getHex(digest.digest());
        } catch (Exception e) {
            return Application.get().getString(R.string.analytics_label_custom_url);
        }
    }

    /**
     * Returns the distance from the specified location
     * to the center of the closest bound in this region.
     *
     * @return distance from the specified location to the center of the closest bound in this
     * region, in meters
     */
    public static Float getDistanceAway(ObaRegion region, double lat, double lon) {
        ObaRegion.Bounds[] bounds = region.getBounds();
        if (bounds == null) {
            return null;
        }
        float[] results = new float[1];
        float minDistance = Float.MAX_VALUE;
        for (ObaRegion.Bounds bound : bounds) {
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
     *
     * @param results Array to receive results.
     *                results[0] == latSpan of region
     *                results[1] == lonSpan of region
     *                results[2] == lat center of region
     *                results[3] == lon center of region
     */
    public static void getRegionSpan(ObaRegion region, double[] results) {
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
        for (ObaRegion.Bounds bound : region.getBounds()) {
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
    public static boolean isLocationWithinRegion(Location location, ObaRegion region) {
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
    public static boolean isRegionUsable(ObaRegion region) {
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
        if (region.getExperimental() && !Application.getPrefs().getBoolean(
                Application.get().getString(R.string.preference_key_experimental_regions), false)) {
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

    /**
     * Gets regions from either the server, local provider, or if both fails the regions file
     * packaged
     * with the APK.  Includes fail-over logic to prefer sources in above order, with server being
     * the first preference.
     *
     * @param forceReload true if a reload from the server should be forced, false if it should not
     * @return a list of regions from either the server, the local provider, or the packaged
     * resource file
     */
    public synchronized static ArrayList<ObaRegion> getRegions(Context context,
            boolean forceReload) {
        ArrayList<ObaRegion> results;
        if (!forceReload) {
            //
            // Check the DB
            //
            results = RegionUtils.getRegionsFromProvider(context);
            if (results != null) {
                Log.d(TAG, "Retrieved regions from database.");
                return results;
            }
            Log.d(TAG, "Regions list retrieved from database was null.");
        }

        results = RegionUtils.getRegionsFromServer(context);
        if (results == null || results.isEmpty()) {
            Log.d(TAG, "Regions list retrieved from server was null or empty.");

            if (forceReload) {
                //If we tried to force a reload from the server, then we haven't tried to reload from local provider yet
                results = RegionUtils.getRegionsFromProvider(context);
                if (results != null) {
                    Log.d(TAG, "Retrieved regions from database.");
                    return results;
                } else {
                    Log.d(TAG, "Regions list retrieved from database was null.");
                }
            }

            //If we reach this point, the call to the Regions REST API failed and no results were
            //available locally from a prior server request.        
            //Fetch regions from local resource file as last resort (otherwise user can't use app)
            results = RegionUtils.getRegionsFromResources(context);

            if (results == null) {
                //This is a complete failure to load region info from all sources, app will be useless
                Log.d(TAG, "Regions list retrieved from local resource file was null.");
                return results;
            }

            Log.d(TAG, "Retrieved regions from local resource file.");
        } else {
            Log.d(TAG, "Retrieved regions list from server.");
            //Update local time for when the last region info was retrieved from the server
            Application.get().setLastRegionUpdateDate(new Date().getTime());
        }

        //If the region info came from the server or local resource file, we need to save it to the local provider
        RegionUtils.saveToProvider(context, results);
        return results;
    }

    public static ArrayList<ObaRegion> getRegionsFromProvider(Context context) {
        // Prefetch the bounds to limit the number of DB calls.
        HashMap<Long, ArrayList<ObaRegionElement.Bounds>> allBounds = getBoundsFromProvider(
                context);

        HashMap<Long, ArrayList<ObaRegionElement.Open311Server>> allOpen311Servers =
                getOpen311ServersFromProvider(context);

        Cursor c = null;
        try {
            final String[] PROJECTION = {
                    ObaContract.Regions._ID,
                    ObaContract.Regions.NAME,
                    ObaContract.Regions.OBA_BASE_URL,
                    ObaContract.Regions.SIRI_BASE_URL,
                    ObaContract.Regions.LANGUAGE,
                    ObaContract.Regions.CONTACT_EMAIL,
                    ObaContract.Regions.SUPPORTS_OBA_DISCOVERY,
                    ObaContract.Regions.SUPPORTS_OBA_REALTIME,
                    ObaContract.Regions.SUPPORTS_SIRI_REALTIME,
                    ObaContract.Regions.TWITTER_URL,
                    ObaContract.Regions.EXPERIMENTAL,
                    ObaContract.Regions.STOP_INFO_URL,
                    ObaContract.Regions.OTP_BASE_URL,
                    ObaContract.Regions.OTP_CONTACT_EMAIL,
                    ObaContract.Regions.SUPPORTS_OTP_BIKESHARE,
                    ObaContract.Regions.SUPPORTS_EMBEDDED_SOCIAL
            };

            ContentResolver cr = context.getContentResolver();
            c = cr.query(
                    ObaContract.Regions.CONTENT_URI, PROJECTION, null, null,
                    ObaContract.Regions._ID);
            if (c == null) {
                return null;
            }
            if (c.getCount() == 0) {
                c.close();
                return null;
            }
            ArrayList<ObaRegion> results = new ArrayList<ObaRegion>();

            c.moveToFirst();
            do {
                long id = c.getLong(0);
                ArrayList<ObaRegionElement.Bounds> bounds = allBounds.get(id);
                ObaRegionElement.Bounds[] bounds2 = (bounds != null) ?
                        bounds.toArray(new ObaRegionElement.Bounds[]{}) :
                        null;

                ArrayList<ObaRegionElement.Open311Server> open311Servers = allOpen311Servers.get(id);
                ObaRegionElement.Open311Server[] open311Servers2 = (open311Servers != null) ?
                        open311Servers.toArray(new ObaRegionElement.Open311Server[]{}) :
                        null;

                results.add(new ObaRegionElement(id,   // id
                        c.getString(1),             // Name
                        true,                       // Active
                        c.getString(2),             // OBA Base URL
                        c.getString(3),             // SIRI Base URL
                        bounds2,                    // Bounds
                        open311Servers2,            // Open311 servers
                        c.getString(4),             // Lang
                        c.getString(5),             // Contact Email
                        c.getInt(6) > 0,            // Supports Oba Discovery
                        c.getInt(7) > 0,            // Supports Oba Realtime
                        c.getInt(8) > 0,            // Supports Siri Realtime
                        c.getString(9),              // Twitter URL
                        c.getInt(10) > 0,            // Experimental
                        c.getString(11),             // StopInfoUrl
                        c.getString(12),             // OTP Base URL
                        c.getString(13),              // OTP Contact Email
                        c.getInt(14) > 0,           // Supports Otp Bikeshare
                        c.getInt(15) > 0            // Supports Embedded Social
                ));

            } while (c.moveToNext());

            return results;

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private static HashMap<Long, ArrayList<ObaRegionElement.Bounds>> getBoundsFromProvider(
            Context context) {
        // Prefetch the bounds to limit the number of DB calls.
        Cursor c = null;
        try {
            final String[] PROJECTION = {
                    ObaContract.RegionBounds.REGION_ID,
                    ObaContract.RegionBounds.LATITUDE,
                    ObaContract.RegionBounds.LONGITUDE,
                    ObaContract.RegionBounds.LAT_SPAN,
                    ObaContract.RegionBounds.LON_SPAN
            };
            HashMap<Long, ArrayList<ObaRegionElement.Bounds>> results
                    = new HashMap<Long, ArrayList<ObaRegionElement.Bounds>>();

            ContentResolver cr = context.getContentResolver();
            c = cr.query(ObaContract.RegionBounds.CONTENT_URI, PROJECTION, null, null, null);
            if (c == null) {
                return results;
            }
            if (c.getCount() == 0) {
                c.close();
                return results;
            }
            c.moveToFirst();
            do {
                long regionId = c.getLong(0);
                ArrayList<ObaRegionElement.Bounds> bounds = results.get(regionId);
                ObaRegionElement.Bounds b = new ObaRegionElement.Bounds(
                        c.getDouble(1),
                        c.getDouble(2),
                        c.getDouble(3),
                        c.getDouble(4));
                if (bounds != null) {
                    bounds.add(b);
                } else {
                    bounds = new ArrayList<ObaRegionElement.Bounds>();
                    bounds.add(b);
                    results.put(regionId, bounds);
                }

            } while (c.moveToNext());

            return results;

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private static HashMap<Long, ArrayList<ObaRegionElement.Open311Server>> getOpen311ServersFromProvider(
            Context context) {
        // Prefetch the bounds to limit the number of DB calls.
        Cursor c = null;
        try {
            final String[] PROJECTION = {
                    ObaContract.RegionOpen311Servers.REGION_ID,
                    ObaContract.RegionOpen311Servers.JURISDICTION,
                    ObaContract.RegionOpen311Servers.API_KEY,
                    ObaContract.RegionOpen311Servers.BASE_URL
            };
            HashMap<Long, ArrayList<ObaRegionElement.Open311Server>> results
                    = new HashMap<Long, ArrayList<ObaRegionElement.Open311Server>>();

            ContentResolver cr = context.getContentResolver();
            c = cr.query(ObaContract.RegionOpen311Servers.CONTENT_URI, PROJECTION, null, null, null);
            if (c == null) {
                return results;
            }
            if (c.getCount() == 0) {
                c.close();
                return results;
            }
            c.moveToFirst();
            do {
                long regionId = c.getLong(0);
                ArrayList<ObaRegionElement.Open311Server> open311Servers = results.get(regionId);
                ObaRegionElement.Open311Server b = new ObaRegionElement.Open311Server(
                        c.getString(1),
                        c.getString(2),
                        c.getString(3));
                if (open311Servers != null) {
                    open311Servers.add(b);
                } else {
                    open311Servers = new ArrayList<ObaRegionElement.Open311Server>();
                    open311Servers.add(b);
                    results.put(regionId, open311Servers);
                }

            } while (c.moveToNext());

            return results;

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private synchronized static ArrayList<ObaRegion> getRegionsFromServer(Context context) {
        ObaRegionsResponse response = ObaRegionsRequest.newRequest(context).call();
        return new ArrayList<ObaRegion>(Arrays.asList(response.getRegions()));
    }

    /**
     * Retrieves region information from a regions file bundled within the app APK
     *
     * IMPORTANT - this should be a last resort, and we should always try to pull regions
     * info from the local provider or Regions REST API instead of from the bundled file.
     *
     * This method is only intended to be a fail-safe in case the Regions REST API goes
     * offline and a user downloads and installs OBA Android during that period
     * (i.e., local OBA servers are available, but Regions REST API failure would block initial
     * execution of the app).  This avoids a potential central point of failure for OBA
     * Android installations on devices in multiple regions.
     *
     * @return list of regions retrieved from the regions file in app resources
     */
    public static ArrayList<ObaRegion> getRegionsFromResources(Context context) {
        final Uri.Builder builder = new Uri.Builder();
        builder.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE);
        builder.authority(context.getPackageName());
        builder.path(Integer.toString(R.raw.regions_v3));
        ObaRegionsResponse response = ObaRegionsRequest.newRequest(context, builder.build()).call();
        return new ArrayList<ObaRegion>(Arrays.asList(response.getRegions()));
    }

    /**
     * Retrieves hard-coded region information from the build flavor defined in build.gradle.
     * If a fixed region is defined in a build flavor, it does not allow region roaming.
     *
     * @return hard-coded region information from the build flavor defined in build.gradle
     */
    public static ObaRegion getRegionFromBuildFlavor() {
        final int regionId = Integer.MAX_VALUE; // This doesn't get used, but needs to be positive
        ObaRegionElement.Bounds[] boundsArray = new ObaRegionElement.Bounds[1];
        ObaRegionElement.Bounds bounds = new ObaRegionElement.Bounds(
                BuildConfig.FIXED_REGION_BOUNDS_LAT, BuildConfig.FIXED_REGION_BOUNDS_LON,
                BuildConfig.FIXED_REGION_BOUNDS_LAT_SPAN, BuildConfig.FIXED_REGION_BOUNDS_LON_SPAN);
        boundsArray[0] = bounds;

        ObaRegionElement.Open311Server[] open311Array = new ObaRegionElement.Open311Server[1];
        ObaRegionElement.Open311Server open311Server;

        if (BuildConfig.FIXED_REGION_OPEN311_BASE_URL != null) {
            open311Server = new ObaRegionElement.Open311Server (
                    BuildConfig.FIXED_REGION_OPEN311_JURISDICTION_ID,
                    BuildConfig.FIXED_REGION_OPEN311_API_KEY,
                    BuildConfig.FIXED_REGION_OPEN311_BASE_URL);
            open311Array[0] = open311Server;
        } else {
            open311Array = null;
        }

        ObaRegionElement region = new ObaRegionElement(regionId,
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
                BuildConfig.FIXED_REGION_SUPPORTS_EMBEDDEDSOCIAL);
        return region;
    }

    //
    // Saving
    //
    public synchronized static void saveToProvider(Context context, List<ObaRegion> regions) {
        // Delete all the existing regions
        ContentResolver cr = context.getContentResolver();
        cr.delete(ObaContract.Regions.CONTENT_URI, null, null);
        // Should be a no-op?
        cr.delete(ObaContract.RegionBounds.CONTENT_URI, null, null);
        // Delete all existing open311 endpoints
        cr.delete(ObaContract.RegionOpen311Servers.CONTENT_URI, null, null);

        for (ObaRegion region : regions) {
            if (!isRegionUsable(region)) {
                Log.d(TAG, "Skipping insert of '" + region.getName() + "' to provider...");
                continue;
            }

            cr.insert(ObaContract.Regions.CONTENT_URI, toContentValues(region));
            Log.d(TAG, "Saved region '" + region.getName() + "' to provider");
            long regionId = region.getId();
            // Bulk insert the bounds
            ObaRegion.Bounds[] bounds = region.getBounds();
            if (bounds != null) {
                ContentValues[] values = new ContentValues[bounds.length];
                for (int i = 0; i < bounds.length; ++i) {
                    values[i] = toContentValues(regionId, bounds[i]);
                }
                cr.bulkInsert(ObaContract.RegionBounds.CONTENT_URI, values);
            }

            ObaRegion.Open311Server[] open311Servers = region.getOpen311Servers();

            if (open311Servers != null) {
                ContentValues[] values = new ContentValues[open311Servers.length];
                for (int i = 0; i < open311Servers.length; ++i) {
                    values[i] = toContentValues(regionId, open311Servers[i]);
                }
                cr.bulkInsert(ObaContract.RegionOpen311Servers.CONTENT_URI, values);
            }
        }
    }

    private static ContentValues toContentValues(ObaRegion region) {
        ContentValues values = new ContentValues();
        values.put(ObaContract.Regions._ID, region.getId());
            values.put(ObaContract.Regions.NAME, region.getName());
        String obaUrl = region.getObaBaseUrl();
        values.put(ObaContract.Regions.OBA_BASE_URL, obaUrl != null ? obaUrl : "");
        String siriUrl = region.getSiriBaseUrl();
        values.put(ObaContract.Regions.SIRI_BASE_URL, siriUrl != null ? siriUrl : "");
        values.put(ObaContract.Regions.LANGUAGE, region.getLanguage());
        values.put(ObaContract.Regions.CONTACT_EMAIL, region.getContactEmail());
        values.put(ObaContract.Regions.SUPPORTS_OBA_DISCOVERY,
                region.getSupportsObaDiscoveryApis() ? 1 : 0);
        values.put(ObaContract.Regions.SUPPORTS_OBA_REALTIME,
                region.getSupportsObaRealtimeApis() ? 1 : 0);
        values.put(ObaContract.Regions.SUPPORTS_SIRI_REALTIME,
                region.getSupportsSiriRealtimeApis() ? 1 : 0);
        values.put(ObaContract.Regions.TWITTER_URL, region.getTwitterUrl());
        values.put(ObaContract.Regions.EXPERIMENTAL, region.getExperimental());
        values.put(ObaContract.Regions.STOP_INFO_URL, region.getStopInfoUrl());
        values.put(ObaContract.Regions.OTP_BASE_URL, region.getOtpBaseUrl());
        values.put(ObaContract.Regions.OTP_CONTACT_EMAIL, region.getOtpContactEmail());
        values.put(ObaContract.Regions.SUPPORTS_OTP_BIKESHARE,
                region.getSupportsOtpBikeshare() ? 1 : 0);
        values.put(ObaContract.Regions.SUPPORTS_EMBEDDED_SOCIAL,
                region.getSupportsEmbeddedSocial() ? 1 : 0);
        return values;
    }

    private static ContentValues toContentValues(long region, ObaRegion.Bounds bounds) {
        ContentValues values = new ContentValues();
        values.put(ObaContract.RegionBounds.REGION_ID, region);
        values.put(ObaContract.RegionBounds.LATITUDE, bounds.getLat());
        values.put(ObaContract.RegionBounds.LONGITUDE, bounds.getLon());
        values.put(ObaContract.RegionBounds.LAT_SPAN, bounds.getLatSpan());
        values.put(ObaContract.RegionBounds.LON_SPAN, bounds.getLonSpan());
        return values;
    }

    private static ContentValues toContentValues(long region, ObaRegion.Open311Server open311Server) {
        ContentValues values = new ContentValues();
        values.put(ObaContract.RegionOpen311Servers.REGION_ID, region);
        values.put(ObaContract.RegionOpen311Servers.BASE_URL, open311Server.getBaseUrl());
        values.put(ObaContract.RegionOpen311Servers.JURISDICTION, open311Server.getJuridisctionId());
        values.put(ObaContract.RegionOpen311Servers.API_KEY, open311Server.getApiKey());
        return values;
    }
}
