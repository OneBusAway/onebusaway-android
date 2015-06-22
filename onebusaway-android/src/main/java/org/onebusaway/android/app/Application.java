/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
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
package org.onebusaway.android.app;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.LocationUtil;
import org.onebusaway.android.util.PreferenceHelp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.google.android.gms.location.LocationServices.FusedLocationApi;

public class Application extends android.app.Application {

    public static final String APP_UID = "app_uid";

    // Region preference (long id)
    private static final String TAG = "Application";

    private SharedPreferences mPrefs;

    private static Application mApp;

    /**
     * We centralize location tracking in the Application class to allow all objects to make
     * use of the last known location that we've seen.  This is more reliable than using the
     * getLastKnownLocation() method of the location providers, and allows us to track both
     * Location
     * API v1 and fused provider.  It allows us to avoid strange behavior like animating a map view
     * change when opening a new Activity, even when the previous Activity had a current location.
     */
    private static Location mLastKnownLocation = null;

    // Magnetic declination is based on location, so track this centrally too.
    static GeomagneticField mGeomagneticField = null;

    /**
     * Google analytics tracker configs
     */
    public enum TrackerName {
        APP_TRACKER, // Tracker used only in this app.
        GLOBAL_TRACKER, // Tracker used by all the apps from a company. eg: roll-up tracking.
    }

    HashMap<TrackerName, Tracker> mTrackers = new HashMap<TrackerName, Tracker>();

    @Override
    public void onCreate() {
        super.onCreate();

        mApp = this;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        initOba();
        initObaRegion();

        ObaAnalytics.initAnalytics(this);
        reportAnalytics();
    }

    /**
     * Per http://developer.android.com/reference/android/app/Application.html#onTerminate(),
     * this code is only executed in emulated process environments - it will never be called
     * on a production Android device.
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
        mApp = null;
    }

    //
    // Public helpers
    //
    public static Application get() {
        return mApp;
    }

    public static SharedPreferences getPrefs() {
        return get().mPrefs;
    }

    /**
     * Returns the last known location that the application has seen, or null if we haven't seen a
     * location yet.  When trying to get a most recent location in one shot, this method should
     * always be called.
     *
     * @param cxt The Context being used, or null if one isn't available
     * @param client The GoogleApiClient being used to obtain fused provider updates, or null if one isn't available
     * @return the last known location that the application has seen, or null if we haven't seen a
     * location yet
     */
    public static synchronized Location getLastKnownLocation(Context cxt, GoogleApiClient client) {
        if (mLastKnownLocation == null) {
            // Try to get a last known location from the location providers
            mLastKnownLocation = getLocation2(cxt, client);
        }
        // Pass back last known saved location, hopefully from past location listener updates
        return mLastKnownLocation;
    }

    /**
     * Sets the last known location observed by the application via an instance of LocationHelper
     *
     * @param l a location received by a LocationHelper instance
     */
    public static synchronized void setLastKnownLocation(Location l) {
        // If the new location is better than the old one, save it
        if (LocationUtil.compareLocations(l, mLastKnownLocation)) {
            if (mLastKnownLocation == null) {
                mLastKnownLocation = new Location("Last known location");
            }
            mLastKnownLocation.set(l);
            mGeomagneticField = new GeomagneticField(
                    (float) l.getLatitude(),
                    (float) l.getLongitude(),
                    (float) l.getAltitude(),
                    System.currentTimeMillis());
            // Log.d(TAG, "Newest best location: " + mLastKnownLocation.toString());
        }
    }

    /**
     * Returns the declination of the horizontal component of the magnetic field from true north,
     * in
     * degrees (i.e. positive means the magnetic field is rotated east that much from true north).
     *
     * @return declination of the horizontal component of the magnetic field from true north, in
     * degrees (i.e. positive means the magnetic field is rotated east that much from true north),
     * or null if its not available
     */
    public static Float getMagneticDeclination() {
        if (mGeomagneticField != null) {
            return mGeomagneticField.getDeclination();
        } else {
            return null;
        }
    }

    /**
     * We need to provide the API for a location used to disambiguate stop IDs in case of
     * collision,
     * or to provide multiple results in the case multiple agencies. But we really don't need it to
     * be very accurate.
     * <p/>
     * Note that the GoogleApiClient must already have been initialized and connected prior to
     * calling
     * this method, since GoogleApiClient.connect() is asynchronous and doesn't connect before it
     * returns,
     * which requires additional initialization time (prior to calling this method)
     *
     * @param client an initialized and connected GoogleApiClient, or null if Google Play Services
     *               isn't available
     * @return a recent location, considering both Google Play Services (if available) and the
     * Android Location API
     */
    private static Location getLocation(Context cxt, GoogleApiClient client) {
        Location last = getLocation2(cxt, client);
        if (last != null) {
            return last;
        } else {
            return LocationUtil.getDefaultSearchCenter();
        }
    }

    /**
     * Returns a location, considering both Google Play Services (if available) and the Android
     * Location API
     * <p/>
     * Note that the GoogleApiClient must already have been initialized and connected prior to
     * calling
     * this method, since GoogleApiClient.connect() is asynchronous and doesn't connect before it
     * returns,
     * which requires additional initialization time (prior to calling this method)
     *
     * @param client an initialized and connected GoogleApiClient, or null if Google Play Services
     *               isn't available
     * @return a recent location, considering both Google Play Services (if available) and the
     * Android Location API
     */
    private static Location getLocation2(Context cxt, GoogleApiClient client) {
        Location playServices = null;
        if (client != null &&
                cxt != null &&
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(cxt)
                        == ConnectionResult.SUCCESS
                && client.isConnected()) {
            playServices = FusedLocationApi.getLastLocation(client);
            Log.d(TAG, "Got location from Google Play Services, testing against API v1...");
        }
        Location apiV1 = getLocationApiV1(cxt);

        if (LocationUtil.compareLocationsByTime(playServices, apiV1)) {
            Log.d(TAG, "Using location from Google Play Services");
            return playServices;
        } else {
            Log.d(TAG, "Using location from Location API v1");
            return apiV1;
        }
    }

    private static Location getLocationApiV1(Context cxt) {
        if (cxt == null) {
            return null;
        }
        LocationManager mgr = (LocationManager) cxt.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mgr.getProviders(true);
        Location last = null;
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            Location loc = mgr.getLastKnownLocation(i.next());
            // If this provider has a last location, and either:
            // 1. We don't have a last location,
            // 2. Our last location is older than this location.
            if (LocationUtil.compareLocationsByTime(loc, last)) {
                last = loc;
            }
        }
        return last;
    }

    //
    // Helper to get/set the regions
    //
    public synchronized ObaRegion getCurrentRegion() {
        return ObaApi.getDefaultContext().getRegion();
    }

    public synchronized void setCurrentRegion(ObaRegion region) {
        if (region != null) {
            // First set it in preferences, then set it in OBA.
            ObaApi.getDefaultContext().setRegion(region);
            PreferenceHelp
                    .saveLong(mPrefs, getString(R.string.preference_key_region), region.getId());
            //We're using a region, so clear the custom API URL preference
            setCustomApiUrl(null);
        } else {
            //User must have just entered a custom API URL via Preferences, so clear the region info
            ObaApi.getDefaultContext().setRegion(null);
            PreferenceHelp.saveLong(mPrefs, getString(R.string.preference_key_region), -1);
        }
    }

    /**
     * Gets the date at which the region information was last updated, in the number of
     * milliseconds
     * since January 1, 1970, 00:00:00 GMT
     * Default value is 0 if the region info has never been updated.
     *
     * @return the date at which the region information was last updated, in the number of
     * milliseconds since January 1, 1970, 00:00:00 GMT.  Default value is 0 if the region info has
     * never been updated.
     */
    public long getLastRegionUpdateDate() {
        SharedPreferences preferences = getPrefs();
        return preferences.getLong(getString(R.string.preference_key_last_region_update), 0);
    }

    /**
     * Sets the date at which the region information was last updated
     *
     * @param date the date at which the region information was last updated, in the number of
     *             milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public void setLastRegionUpdateDate(long date) {
        PreferenceHelp
                .saveLong(mPrefs, getString(R.string.preference_key_last_region_update), date);
    }

    /**
     * Returns the custom URL if the user has set a custom API URL manually via Preferences, or
     * null
     * if it has not been set
     *
     * @return the custom URL if the user has set a custom API URL manually via Preferences, or null
     * if it has not been set
     */
    public String getCustomApiUrl() {
        SharedPreferences preferences = getPrefs();
        return preferences.getString(getString(R.string.preference_key_oba_api_url), null);
    }

    /**
     * Sets the custom URL used to reach a OBA REST API server that is not available via the
     * Regions
     * REST API
     *
     * @param url the custom URL
     */
    public void setCustomApiUrl(String url) {
        PreferenceHelp.saveString(getString(R.string.preference_key_oba_api_url), url);
    }

    private static final String HEXES = "0123456789abcdef";

    public static String getHex(byte[] raw) {
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4))
                    .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    private String getAppUid() {
        try {
            final TelephonyManager telephony =
                    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            final String id = telephony.getDeviceId();
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(id.getBytes());
            return getHex(digest.digest());
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private void initOba() {
        String uuid = mPrefs.getString(APP_UID, null);
        if (uuid == null) {
            // Generate one and save that.
            uuid = getAppUid();
            PreferenceHelp.saveString(APP_UID, uuid);
        }

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        ObaApi.getDefaultContext().setAppInfo(appInfo.versionCode, uuid);
    }

    private void initObaRegion() {
        // Read the region preference, look it up in the DB, then set the region.
        long id = mPrefs.getLong(getString(R.string.preference_key_region), -1);
        if (id < 0) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Regions preference ID is less than 0, returning...");
            }
            return;
        }

        ObaRegion region = ObaContract.Regions.get(this, (int) id);
        if (region == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Regions preference is null, returning...");
            }
            return;
        }


        ObaApi.getDefaultContext().setRegion(region);
    }

    public synchronized Tracker getTracker(TrackerName trackerId) {
        if (!mTrackers.containsKey(trackerId)) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            Tracker t = (trackerId == TrackerName.APP_TRACKER) ? analytics.newTracker(R.xml.app_tracker)
                    : (trackerId == TrackerName.GLOBAL_TRACKER) ? analytics.newTracker(R.xml.global_tracker)
                    : analytics.newTracker(R.xml.global_tracker);
            mTrackers.put(trackerId, t);
        }
        return mTrackers.get(trackerId);
    }

    private void reportAnalytics() {
        if (getCustomApiUrl() == null && getCurrentRegion() != null) {
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                    getString(R.string.analytics_action_configured_region), getString(R.string.analytics_label_region)
                            + getCurrentRegion().getName());
        } else if (Application.get().getCustomApiUrl() != null) {
            String customUrl = null;
            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-1");
                digest.update(getCustomApiUrl().getBytes());
                customUrl = getString(R.string.analytics_label_custom_url) +
                        ": " + getHex(digest.digest());
            } catch (Exception e) {
                customUrl = Application.get().getString(R.string.analytics_label_custom_url);
            }
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                    getString(R.string.analytics_action_configured_region), getString(R.string.analytics_label_region)
                            + customUrl);
        }
        Boolean experimentalRegions = getPrefs().getBoolean(getString(R.string.preference_key_experimental_regions),
                Boolean.FALSE);
        Boolean autoRegion = getPrefs().getBoolean(getString(R.string.preference_key_auto_select_region),
                Boolean.FALSE);
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                getString(R.string.analytics_action_edit_general), getString(R.string.analytics_label_experimental)
                        + (experimentalRegions ? "YES" : "NO"));
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                getString(R.string.analytics_action_edit_general), getString(R.string.analytics_label_region_auto)
                        + (autoRegion ? "YES" : "NO"));
    }
}
