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
import android.hardware.GeomagneticField;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.donations.DonationsManager;
import org.onebusaway.android.app.di.AnalyticsEntryPoint;
import org.onebusaway.android.api.ObaApi;
import org.onebusaway.android.region.Region;
import org.onebusaway.android.app.di.LocationEntryPoint;
import org.onebusaway.android.app.di.RegionEntryPoint;
import org.onebusaway.android.region.RegionSubsystems;
import org.onebusaway.android.util.BuildFlavorUtils;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.ThemeUtils;

import java.security.MessageDigest;
import java.util.UUID;

import edu.usf.cutr.open311client.Open311Manager;
import edu.usf.cutr.open311client.models.Open311Option;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;
import java.nio.charset.StandardCharsets;

import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.android.HiltAndroidApp;
import org.onebusaway.android.app.di.DatabaseEntryPoint;
import org.onebusaway.android.app.di.FirebaseMessagingEntryPoint;

@HiltAndroidApp
public class Application extends android.app.Application {

    // Region preference (long id)
    private static final String TAG = "Application";

    public static final String CHANNEL_TRIP_PLAN_UPDATES_ID = "trip_plan_updates";
    public static final String CHANNEL_ARRIVAL_REMINDERS_ID = "arrival_reminders";
    public static final String CHANNEL_DESTINATION_ALERT_ID = "destination_alerts";

    private DonationsManager mDonationsManager;

    private static Application mApp;

    // Magnetic declination is based on location, so track this centrally too. (The last-known location
    // itself lives in the reactive LocationRepository singleton.)
    static GeomagneticField mGeomagneticField = null;


    @Override
    public void onCreate() {
        super.onCreate();

        mApp = this;

        initOba();

        // Kick the one-time legacy ContentProvider -> Room data import (fire-and-forget) so it overlaps
        // startup; every migrated repository read/write awaits this gate before touching the DB.
        EntryPointAccessors.fromApplication(this, DatabaseEntryPoint.class).importGate().start();

        // The region and location repositories (which own region/location state) are now Hilt
        // @Singletons, constructed lazily on first injection after onCreate. The region repo seeds
        // itself from persistence (the saved region-id → ContentProvider lookup); the location repo
        // starts empty and fills from setLastKnownLocation (listener updates) / its lazy provider poll.
        // The legacy setCurrentRegion / setLastKnownLocation writers reach them via their EntryPoints.
        // So nothing to construct here.
        // The region-derived Open311 endpoints observe the region flow (A7) via RegionSubsystems — this
        // performs their initial init (the StateFlow replays the repo's seeded region) and re-inits on
        // change, replacing the former explicit initOpen311(getCurrentRegion()) call. The Plausible/Umami
        // analytics emitters observe the same flow independently, from AnalyticsProvider.
        RegionSubsystems.observe(this);

        reportAnalytics();

        createNotificationChannels();

        incrementAppLaunchCount();

        FirebaseMessagingEntryPoint.get(this).fetchAndStoreToken();

        mDonationsManager = new DonationsManager(getApplicationContext(), getResources(), getAppLaunchCount());
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


    // Preserve the original preference-key value so persisted launch counts survive upgrades.
    public static final String APP_LAUNCH_COUNT_KEY = "appLaunchCountPreferencesKey";

    private void incrementAppLaunchCount() {
        int count = PreferenceUtils.getInt(APP_LAUNCH_COUNT_KEY, 0);
        count += 1;
        PreferenceUtils.saveInt(APP_LAUNCH_COUNT_KEY, count);
    }

    public int getAppLaunchCount() {
        return PreferenceUtils.getInt(APP_LAUNCH_COUNT_KEY, 0);
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
    public synchronized Region getCurrentRegion() {
        // RegionRepository is the sole owner of the current region; read its current value. (This stays
        // as a convenience for the remaining non-injectable Java readers that go through Application.)
        return RegionEntryPoint.get(this).currentRegion();
    }

    /**
     * Sets the current region directly. The production region writers all route
     * through {@code RegionRepository} ({@code refresh}/{@code choose}/{@code clear}); this remains only
     * as the instrumented-test seam (the io/* request tests that pin a known region synchronously). It
     * delegates the canonical region write to {@code RegionRepository.applyRegion}.
     */
    public synchronized void setCurrentRegion(Region region) {
        setCurrentRegion(region, true);
    }

    public synchronized void setCurrentRegion(Region region, boolean regionChanged) {
        // The canonical region write lives in RegionRepository as of A7; the region-derived subsystems
        // re-init reactively by observing the published flow — Open311 via RegionSubsystems, the
        // Plausible/Umami analytics emitters via AnalyticsProvider.
        RegionEntryPoint.get(this).applyRegion(region, regionChanged);
    }

    /**
     * Re-initializes the region-*derived* Open311 reporting endpoints for [region]. Driven reactively by
     * {@link org.onebusaway.android.region.RegionSubsystems}, which observes the region flow (A7), rather
     * than poked imperatively by a region write transaction. The Plausible/Umami analytics emitters are
     * likewise region-derived, but they are owned + rebuilt by
     * {@link org.onebusaway.android.analytics.AnalyticsProvider} (which observes the same flow).
     */
    public void onRegionChanged(Region region) {
        initOpen311(region);
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
        // Ensure a per-install app UID is persisted; ObaEndpointResolver reads it as app_uid.
        if (PreferenceUtils.getString(ObaApi.APP_UID) == null) {
            PreferenceUtils.saveString(ObaApi.APP_UID, getAppUid());
        }

        checkArrivalStylePreferenceDefault();
        checkDarkMode();
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
            ThemeUtils.setAppTheme(this, appThemePref);
        }
    }

    private void initOpen311(Region region) {
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
            for (Region.Open311Server open311Server : region.getOpen311Servers()) {
                String jurisdictionId = open311Server.getJuridisctionId();

                Open311Option option = new Open311Option(open311Server.getBaseUrl(),
                        open311Server.getApiKey(),
                        TextUtils.isEmpty(jurisdictionId) ? null : jurisdictionId);
                Open311Manager.initOpen311WithOption(option);
            }
        }
    }

    private void reportAnalytics() {
        // The Plausible/Umami emitters are owned + built reactively by AnalyticsProvider; here we only set
        // the initial Firebase/Umami region label via setRegion (which resolves the Umami emitter through
        // the provider itself).
        if (getCustomApiUrl() == null && getCurrentRegion() != null) {
            AnalyticsEntryPoint.get(this).setRegion(getCurrentRegion().getName());
        } else if (getCustomApiUrl() != null) {
            String customUrl;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.update(getCustomApiUrl().getBytes(StandardCharsets.UTF_8));
                customUrl = getString(R.string.analytics_label_custom_url) +
                        ": " + getHex(digest.digest());
            } catch (Exception e) {
                customUrl = getString(R.string.analytics_label_custom_url);
            }
            AnalyticsEntryPoint.get(this).setRegion(customUrl);
        }
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

}
