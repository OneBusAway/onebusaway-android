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

import android.content.Context;
import android.hardware.GeomagneticField;
import android.location.Location;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.notifications.NotificationChannels;
import org.onebusaway.android.app.di.AnalyticsEntryPoint;
import org.onebusaway.android.api.ObaApi;
import org.onebusaway.android.region.Region;
import org.onebusaway.android.app.di.LocationEntryPoint;
import org.onebusaway.android.app.di.RegionEntryPoint;
import org.onebusaway.android.region.RegionSubsystems;
import org.onebusaway.android.util.BuildFlavorUtils;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.CustomApiUrlLabel;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.ThemeUtils;

import java.util.UUID;


import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.android.HiltAndroidApp;
import org.onebusaway.android.app.di.DatabaseEntryPoint;
import org.onebusaway.android.app.di.FirebaseMessagingEntryPoint;
import org.onebusaway.android.app.di.PreferencesEntryPoint;

@HiltAndroidApp
public class Application extends android.app.Application {

    private static Application mApp;

    // Magnetic declination is based on location, so track this centrally too. (The last-known location
    // itself lives in the reactive LocationRepository singleton.)
    static GeomagneticField mGeomagneticField = null;


    @Override
    public void onCreate() {
        super.onCreate();

        mApp = this;

        // Seed the per-install app UID once, eagerly, before any reader needs it. It has multiple
        // independent direct readers (ObaEndpointResolver sends it as app_uid; the Open311 report path
        // reads it as device_id), so seeding lazily in one reader can't guarantee it for the others.
        if (PreferenceUtils.getString(ObaApi.APP_UID) == null) {
            PreferenceUtils.saveString(ObaApi.APP_UID, UUID.randomUUID().toString());
        }

        // Seed first-run OBA defaults: the build-flavor arrival-info style, and apply the saved theme.
        BuildFlavorUtils.applyDefaultArrivalInfoStyleIfUnset(this);
        ThemeUtils.applyPersistedTheme(this);

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

        NotificationChannels.registerAll(this);

        PreferencesEntryPoint.get(this).incrementAppLaunchCount();

        FirebaseMessagingEntryPoint.get(this).fetchAndStoreToken();
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

    private void reportAnalytics() {
        // The Plausible/Umami emitters are owned + built reactively by AnalyticsProvider; here we only set
        // the initial Firebase/Umami region label via setRegion (which resolves the Umami emitter through
        // the provider itself).
        String customApiUrl = PreferencesEntryPoint.get(this)
                .getString(R.string.preference_key_oba_api_url, null);
        if (customApiUrl == null && getCurrentRegion() != null) {
            AnalyticsEntryPoint.get(this).setRegion(getCurrentRegion().getName());
        } else if (customApiUrl != null) {
            AnalyticsEntryPoint.get(this)
                    .setRegion(CustomApiUrlLabel.forUrl(this, customApiUrl));
        }
    }

}
