/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com), 
 * University of South Florida, Microsoft Corporation.
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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;

import com.onebusaway.plausible.android.Plausible;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.donations.DonationsManager;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.app.di.LocationEntryPoint;
import org.onebusaway.android.app.di.RegionEntryPoint;
import org.onebusaway.android.region.RegionSubsystems;
import org.onebusaway.android.travelbehavior.TravelBehaviorManager;
import org.onebusaway.android.util.BuildFlavorUtils;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.ThemeUtils;
import org.onebusaway.android.widealerts.GtfsAlerts;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.UUID;

import edu.usf.cutr.open311client.Open311Manager;
import edu.usf.cutr.open311client.models.Open311Option;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;
import java.nio.charset.StandardCharsets;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class Application extends android.app.Application {

    public static final String APP_UID = "app_uid";

    // Region preference (long id)
    private static final String TAG = "Application";

    public static final String CHANNEL_TRIP_PLAN_UPDATES_ID = "trip_plan_updates";
    public static final String CHANNEL_ARRIVAL_REMINDERS_ID = "arrival_reminders";
    public static final String CHANNEL_DESTINATION_ALERT_ID = "destination_alerts";

    private DonationsManager mDonationsManager;

    private GtfsAlerts mGtfsAlerts;

    private static Application mApp;

    // Magnetic declination is based on location, so track this centrally too. (The last-known location
    // itself lives in the reactive LocationRepository singleton.)
    static GeomagneticField mGeomagneticField = null;

    private FirebaseAnalytics mFirebaseAnalytics;

    private Plausible mPlausible;

    private org.onebusaway.android.io.UmamiAnalytics mUmami;

    @Override
    public void onCreate() {
        super.onCreate();

        mApp = this;

        initOba();
        initObaRegion();
        // The region and location repositories (which own region/location state) are now Hilt
        // @Singletons, constructed lazily on first injection after onCreate. The
        // region repo seeds itself from the region initObaRegion just loaded; the location repo starts
        // empty and fills from setLastKnownLocation (listener updates) / its lazy provider poll. The
        // legacy setCurrentRegion / setLastKnownLocation writers reach them via their EntryPoints. So
        // nothing to construct here.
        // The region-derived subsystems (Plausible, Open311) now observe the region flow (A7) — this
        // performs their initial init (the StateFlow replays its seeded region) and re-inits on change,
        // replacing the former explicit initOpen311(getCurrentRegion()) call. Started after initObaRegion
        // so the repo seeds from the region just loaded.
        RegionSubsystems.observe(this);

        reportAnalytics();

        createNotificationChannels();

        TravelBehaviorManager.startCollectingData(getApplicationContext());

        incrementAppLaunchCount();

        initFirebaseMessaging();

        mDonationsManager = new DonationsManager(mFirebaseAnalytics, getResources(), getAppLaunchCount());

        mGtfsAlerts = new GtfsAlerts(getApplicationContext());
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

    public static DonationsManager getDonationsManager() { return get().mDonationsManager; }

    public static GtfsAlerts getGtfsAlerts() {
        return get().mGtfsAlerts;
    }


    private static String appLaunchCountPreferencesKey = "appLaunchCountPreferencesKey";

    private void incrementAppLaunchCount() {
        int count = PreferenceUtils.getInt(appLaunchCountPreferencesKey, 0);
        count += 1;
        PreferenceUtils.saveInt(appLaunchCountPreferencesKey, count);
    }

    public int getAppLaunchCount() {
        return PreferenceUtils.getInt(appLaunchCountPreferencesKey, 0);
    }

    /**
     * Returns the last known location that the application has seen, or null if we haven't seen a
     * location yet.  When trying to get a most recent location in one shot, this method should
     * always be called.
     *
     * <p>The location lives in the reactive {@code LocationRepository}; this is a thin delegate kept for
     * the {@code LocationHelper} listener read-back and the io/* instrumented tests. Injectable
     * production readers inject {@code LocationRepository} directly. The {@code cxt} is used only to
     * resolve the singleton graph (any context's application works), so a null one falls back to the
     * Application itself.
     *
     * @param cxt    The Context being used, or null if one isn't available
     * @return the last known location that the application has seen, or null if we haven't seen a
     * location yet
     */
    public static synchronized Location getLastKnownLocation(Context cxt) {
        Context ctx = cxt != null ? cxt : mApp;
        if (ctx == null) {
            return null;
        }
        return LocationEntryPoint.get(ctx).lastKnownLocation();
    }

    /**
     * Sets the last known location observed by the application via an instance of LocationHelper. The
     * location itself is stored in the {@code LocationRepository} (which applies the "is it better?"
     * gate); when it accepts the update we refresh the location-derived magnetic declination here.
     *
     * @param l a location received by a LocationHelper instance
     */
    public static synchronized void setLastKnownLocation(Location l) {
        Application app = mApp;
        if (app == null) {
            return;
        }
        if (LocationEntryPoint.getSink(app).update(l)) {
            mGeomagneticField = new GeomagneticField(
                    (float) l.getLatitude(),
                    (float) l.getLongitude(),
                    (float) l.getAltitude(),
                    System.currentTimeMillis());
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

    //
    // Helper to get/set the regions
    //
    public synchronized ObaRegion getCurrentRegion() {
        return ObaApi.getDefaultContext().getRegion();
    }

    /**
     * Sets the current region directly. The production region writers all route
     * through {@code RegionRepository} ({@code refresh}/{@code choose}/{@code clear}); this remains only
     * as the instrumented-test seam (the io/* request tests that pin a known region synchronously). It
     * delegates the canonical region write to {@code RegionRepository.applyRegion}.
     */
    public synchronized void setCurrentRegion(ObaRegion region) {
        setCurrentRegion(region, true);
    }

    public synchronized void setCurrentRegion(ObaRegion region, boolean regionChanged) {
        // The canonical region write lives in RegionRepository as of A7; the region-derived subsystems
        // (Plausible, Umami, Open311) re-init reactively via RegionSubsystems observing the published flow.
        RegionEntryPoint.get(this).applyRegion(region, regionChanged);
    }

    /**
     * Re-initializes the region-*derived* subsystems — the Plausible/Umami analytics instances and the
     * Open311 reporting endpoints — for [region]. Driven reactively by
     * {@link org.onebusaway.android.region.RegionSubsystems}, which observes the region flow (A7), rather
     * than poked imperatively by a region write transaction.
     */
    public void onRegionChanged(ObaRegion region) {
        buildPlausibleInstance(region);
        buildUmamiInstance(region);
        initOpen311(region);
    }

    /**
     * Return Plausible instance for the application
     * @return Plausible instance
     */
    public Plausible getPlausibleInstance() {
        if(mPlausible == null) {
            buildPlausibleInstance(getCurrentRegion());
        }
        return mPlausible;
    }

    /**
     * Build the Plausible instance for the application
     * Include the domain and the plausible server url for the current region
     * @param region
     */
    private void buildPlausibleInstance(ObaRegion region) {
        mPlausible = null;
        if (region == null || region.getObaBaseUrl() == null || region.getPlausibleAnalyticsServerUrl() == null) return;
        String domain;
        try {
            domain = new URI(region.getObaBaseUrl()).getHost();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        mPlausible = new Plausible(this, domain, region.getPlausibleAnalyticsServerUrl());
    }

    public org.onebusaway.android.io.UmamiAnalytics getUmamiInstance() {
        if (mUmami == null) {
            buildUmamiInstance(getCurrentRegion());
        }
        return mUmami;
    }

    private void buildUmamiInstance(ObaRegion region) {
        mUmami = null;
        if (region == null
                || region.getObaBaseUrl() == null
                || region.getUmamiAnalyticsUrl() == null
                || region.getUmamiAnalyticsId() == null) {
            return;
        }
        String host;
        try {
            host = new URI(region.getObaBaseUrl()).getHost();
        } catch (URISyntaxException e) {
            // Fire-and-forget telemetry must never throw on a malformed URL.
            return;
        }
        if (host == null) {
            return;
        }
        mUmami = new org.onebusaway.android.io.UmamiAnalytics(
                region.getUmamiAnalyticsUrl(), region.getUmamiAnalyticsId(), host);
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
        return PreferenceUtils.getLong(getString(R.string.preference_key_last_region_update), 0);
    }

    /**
     * Sets the date at which the region information was last updated
     *
     * @param date the date at which the region information was last updated, in the number of
     *             milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public void setLastRegionUpdateDate(long date) {
        PreferenceUtils.saveLong(getString(R.string.preference_key_last_region_update), date);
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
        return PreferenceUtils.getString(getString(R.string.preference_key_oba_api_url));
    }

    /**
     * Sets the custom URL used to reach a OBA REST API server that is not available via the
     * Regions
     * REST API
     *
     * @param url the custom URL
     */
    public void setCustomApiUrl(String url) {
        PreferenceUtils.saveString(getString(R.string.preference_key_oba_api_url), url);
    }

    /**
     * Returns the custom OTP URL if the user has set a custom API URL manually via Preferences, or
     * null
     * if it has not been set
     *
     * @return the custom URL if the user has set a custom API URL manually via Preferences, or null
     * if it has not been set
     */
    public String getCustomOtpApiUrl() {
        return PreferenceUtils.getString(getString(R.string.preference_key_otp_api_url));
    }

    /**
     * Sets the custom OTP URL used to reach a OBA REST API server that is not available via the
     * Regions
     * REST API
     *
     * @param url the custom URL
     */
    public void setCustomOtpApiUrl(String url) {
        PreferenceUtils.saveString(getString(R.string.preference_key_otp_api_url), url);
    }

    /**
     * @return true if the OTP url version is old, or false  if it has not been set
     */
    public boolean getUseOldOtpApiUrlVersion() {
        return PreferenceUtils.getBoolean(getString(R.string.preference_key_otp_api_url_version), false);
    }

    /**
     * Sets the OTP Api url version
     *
     * @param useOldOtpApiUrlVersion indicates that if otp url structure belongs to older version
     */
    public void setUseOldOtpApiUrlVersion(boolean useOldOtpApiUrlVersion) {
        PreferenceUtils.saveBoolean(getString(R.string.preference_key_otp_api_url_version),
                useOldOtpApiUrlVersion);
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
        return UUID.randomUUID().toString();
    }

    private void initOba() {
        String uuid = PreferenceUtils.getString(APP_UID);
        if (uuid == null) {
            // Generate one and save that.
            uuid = getAppUid();
            PreferenceUtils.saveString(APP_UID, uuid);
        }

        checkArrivalStylePreferenceDefault();
        checkDarkMode();

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

    private void checkArrivalStylePreferenceDefault() {
        String arrivalInfoStylePrefKey = getResources()
                .getString(R.string.preference_key_arrival_info_style);
        String arrivalInfoStylePref = PreferenceUtils.getString(arrivalInfoStylePrefKey);
        if (arrivalInfoStylePref == null) {
            // First execution of app - set the default arrival info style based on the BuildConfig value
            switch (BuildConfig.ARRIVAL_INFO_STYLE) {
                case BuildFlavorUtils.ARRIVAL_INFO_STYLE_A:
                    // Use OBA classic style for default
                    PreferenceUtils.saveString(arrivalInfoStylePrefKey, BuildFlavorUtils
                            .getPreferenceOptionForArrivalInfoBuildFlavorStyle(this,
                                    BuildFlavorUtils.ARRIVAL_INFO_STYLE_A));
                    Log.d(TAG, "Using arrival info style A (OBA Classic) as default preference");
                    break;
                case BuildFlavorUtils.ARRIVAL_INFO_STYLE_B:
                    // Use a card-styled footer for default
                    PreferenceUtils.saveString(arrivalInfoStylePrefKey, BuildFlavorUtils
                            .getPreferenceOptionForArrivalInfoBuildFlavorStyle(this,
                                    BuildFlavorUtils.ARRIVAL_INFO_STYLE_B));
                    Log.d(TAG, "Using arrival info style B (Cards) as default preference");
                    break;
                default:
                    // Use a card-styled footer for default
                    PreferenceUtils.saveString(arrivalInfoStylePrefKey, BuildFlavorUtils
                            .getPreferenceOptionForArrivalInfoBuildFlavorStyle(this,
                                    BuildFlavorUtils.ARRIVAL_INFO_STYLE_B));
                    Log.d(TAG, "Using arrival info style B (Cards) as default preference");
                    break;
            }
        }
    }

    private void checkDarkMode() {
        String appThemePrefKey = getResources()
                .getString(R.string.preference_key_app_theme);
        String appThemePref = PreferenceUtils.getString(appThemePrefKey);
        if (appThemePref != null) {
            ThemeUtils.setAppTheme(appThemePref);
        }
    }

    private void initObaRegion() {
        // Read the region preference, look it up in the DB, then set the region.
        long id = PreferenceUtils.getLong(getString(R.string.preference_key_region), -1);
        if (id < 0) {
            Log.d(TAG, "Regions preference ID is less than 0, returning...");
            return;
        }

        ObaRegion region = ObaContract.Regions.get(this, (int) id);
        if (region == null) {
            Log.d(TAG, "Regions preference is null, returning...");
            return;
        }


        ObaApi.getDefaultContext().setRegion(region);
    }

    private void initOpen311(ObaRegion region) {
        if (BuildConfig.DEBUG) {
            Open311Manager.getSettings().setDebugMode(true);
            Open311Manager.getSettings().setDryRun(true);
            Log.w(TAG,
                    "Open311 issue reporting is in debug/dry run mode - no issues will be submitted.");
        }

        // Clear all open311 endpoints
        Open311Manager.clearOpen311();

        // Read the open311 preferences from the region and set
        if (region != null && region.getOpen311Servers() != null) {
            for (ObaRegion.Open311Server open311Server : region.getOpen311Servers()) {
                String jurisdictionId = open311Server.getJuridisctionId();

                Open311Option option = new Open311Option(open311Server.getBaseUrl(),
                        open311Server.getApiKey(),
                        TextUtils.isEmpty(jurisdictionId) ? null : jurisdictionId);
                Open311Manager.initOpen311WithOption(option);
            }
        }
    }

    private void reportAnalytics() {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        if (getCustomApiUrl() == null && getCurrentRegion() != null) {
            buildPlausibleInstance(getCurrentRegion());
            buildUmamiInstance(getCurrentRegion());
            ObaAnalytics.setRegion(mPlausible, mFirebaseAnalytics, getCurrentRegion().getName());
        } else if (Application.get().getCustomApiUrl() != null) {
            String customUrl = null;
            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-1");
                digest.update(getCustomApiUrl().getBytes(StandardCharsets.UTF_8));
                customUrl = getString(R.string.analytics_label_custom_url) +
                        ": " + getHex(digest.digest());
            } catch (Exception e) {
                customUrl = Application.get().getString(R.string.analytics_label_custom_url);
            }
            ObaAnalytics.setRegion(mPlausible, mFirebaseAnalytics, customUrl);
        }
    }

    /**
     * Method to check whether bikeshare layer is enabled or not.
     *
     * @return true if the bikeshare layer is an option that can be toggled on/off
     */
    public static boolean isBikeshareEnabled() {
        // Bike layer is enabled if either the current region
        // supports it or a custom otp url is set. The custom otp url is used to make the testing
        // process easier
        return ((Application.get().getCurrentRegion() != null
                && Application.get().getCurrentRegion().getSupportsOtpBikeshare())
                || !TextUtils.isEmpty(Application.get().getCustomOtpApiUrl()));
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel1 = new NotificationChannel(
                    CHANNEL_TRIP_PLAN_UPDATES_ID,
                    "Trip plan notifications (beta)",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel1.setDescription("After planning a trip, send notifications if the trip is delayed or no longer recommended.");

            NotificationChannel channel2 = new NotificationChannel(
                    CHANNEL_ARRIVAL_REMINDERS_ID,
                    "Bus arrival notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel2.setDescription("Notifications to remind the user of an arriving bus.");

            NotificationChannel channel3 = new NotificationChannel(
                    CHANNEL_DESTINATION_ALERT_ID,
                    "Destination alerts",
                    NotificationManager.IMPORTANCE_LOW);
            channel3.setDescription("All notifications relating to Destination alerts");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel1);
            manager.createNotificationChannel(channel2);
            manager.createNotificationChannel(channel3);
        }
    }

    public static Boolean isIgnoringBatteryOptimizations(Context applicationContext) {
        PowerManager pm = (PowerManager) applicationContext.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                pm.isIgnoringBatteryOptimizations(applicationContext.getPackageName())) {
            return true;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }

        return false;
    }

    private void initFirebaseMessaging() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    PreferenceUtils.saveString(getString(R.string.firebase_messaging_token), token);
                });
    }

    public static String getUserPushID() {
        String key = get().getApplicationContext().getString(R.string.firebase_messaging_token);
        String token = PreferenceUtils.getString(key);
        return token != null ? token : "";
    }

}
