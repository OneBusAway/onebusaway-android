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
package org.onebusaway.android.app

import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import org.onebusaway.android.R
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.app.di.FirebaseMessagingEntryPoint
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.region.RegionSubsystems
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.CustomApiUrlLabel
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ThemeUtils
import java.util.UUID

@HiltAndroidApp
class Application : android.app.Application() {

    override fun onCreate() {
        super.onCreate()

        mApp = this

        // Seed the per-install app UID once, eagerly, before any reader needs it. It has multiple
        // independent direct readers (ObaEndpointResolver sends it as app_uid; the Open311 report path
        // reads it as device_id), so seeding lazily in one reader can't guarantee it for the others.
        if (PreferenceUtils.getString(ObaApi.APP_UID) == null) {
            PreferenceUtils.saveString(ObaApi.APP_UID, UUID.randomUUID().toString())
        }

        // Seed first-run OBA defaults: the build-flavor arrival-info style, and apply the saved theme.
        BuildFlavorUtils.applyDefaultArrivalInfoStyleIfUnset(this)
        ThemeUtils.applyPersistedTheme(this)

        // Kick the one-time legacy ContentProvider -> Room data import (fire-and-forget) so it overlaps
        // startup; every migrated repository read/write awaits this gate before touching the DB.
        EntryPointAccessors.fromApplication(this, DatabaseEntryPoint::class.java).importGate().start()

        // The region and location repositories (which own region/location state) are now Hilt
        // @Singletons, constructed lazily on first injection after onCreate. The region repo seeds
        // itself from persistence (the saved region-id → ContentProvider lookup); the location repo
        // starts empty and fills from setLastKnownLocation (listener updates) / its lazy provider poll.
        // The legacy setLastKnownLocation writer reaches the location repo via its EntryPoint; region
        // reads/writes now go straight through RegionRepository (via injection or RegionEntryPoint).
        // So nothing to construct here.
        // The region-derived Open311 endpoints observe the region flow (A7) via RegionSubsystems — this
        // performs their initial init (the StateFlow replays the repo's seeded region) and re-inits on
        // change, replacing the former explicit initOpen311(getCurrentRegion()) call. The Plausible/Umami
        // analytics emitters observe the same flow independently, from AnalyticsProvider.
        RegionSubsystems.observe(this)

        reportAnalytics()

        NotificationChannels.registerAll(this)

        PreferencesEntryPoint.get(this).incrementAppLaunchCount()

        FirebaseMessagingEntryPoint.get(this).fetchAndStoreToken()
    }

    /**
     * Per http://developer.android.com/reference/android/app/Application.html#onTerminate(),
     * this code is only executed in emulated process environments - it will never be called
     * on a production Android device.
     */
    override fun onTerminate() {
        super.onTerminate()
        mApp = null
    }

    private fun reportAnalytics() {
        // The Plausible/Umami emitters are owned + built reactively by AnalyticsProvider; here we only set
        // the initial Firebase/Umami region label via setRegion (which resolves the Umami emitter through
        // the provider itself): a custom API URL identifies the region, otherwise the active region's name.
        val customApiUrl = PreferencesEntryPoint.get(this)
            .getString(R.string.preference_key_oba_api_url, null)
        val label = if (customApiUrl != null) {
            CustomApiUrlLabel.forUrl(this, customApiUrl)
        } else {
            RegionEntryPoint.get(this).currentRegion()?.name
        }
        label?.let { AnalyticsEntryPoint.get(this).setRegion(it) }
    }

    companion object {

        // Set in onCreate, cleared in onTerminate (emulator-only). Nullable-backed rather than lateinit
        // precisely because onTerminate re-nulls it; get() unwraps it non-null since it's never read
        // before onCreate and every caller dereferences it.
        private var mApp: Application? = null

        // Magnetic declination is based on location, so track this centrally too. (The last-known
        // location itself lives in the reactive LocationRepository singleton.)
        private var mGeomagneticField: GeomagneticField? = null

        @JvmStatic
        fun get(): Application = mApp!!

        /**
         * Returns the last known location that the application has seen, or null if we haven't seen a
         * location yet. When trying to get a most recent location in one shot, this method should
         * always be called.
         *
         * The location lives in the reactive [org.onebusaway.android.location.LocationRepository]; this
         * is a thin delegate kept for the `LocationHelper` listener read-back and the instrumented
         * tests under `io/`. Injectable production readers inject `LocationRepository` directly. The
         * [cxt] is used only to resolve the singleton graph (any context's application works), so a null
         * one falls back to the Application itself.
         */
        @JvmStatic
        @Synchronized
        fun getLastKnownLocation(cxt: Context?): Location? {
            val ctx = cxt ?: mApp ?: return null
            return LocationEntryPoint.get(ctx).lastKnownLocation()
        }

        /**
         * Sets the last known location observed by the application via an instance of LocationHelper.
         * The location itself is stored in the `LocationRepository` (which applies the "is it better?"
         * gate); when it accepts the update we refresh the location-derived magnetic declination here.
         */
        @JvmStatic
        @Synchronized
        fun setLastKnownLocation(l: Location) {
            val app = mApp ?: return
            if (LocationEntryPoint.getSink(app).update(l)) {
                mGeomagneticField = GeomagneticField(
                    l.latitude.toFloat(),
                    l.longitude.toFloat(),
                    l.altitude.toFloat(),
                    System.currentTimeMillis()
                )
            }
        }

        /**
         * Returns the declination of the horizontal component of the magnetic field from true north, in
         * degrees (i.e. positive means the magnetic field is rotated east that much from true north), or
         * null if it's not available.
         */
        @JvmStatic
        fun getMagneticDeclination(): Float? = mGeomagneticField?.declination
    }
}
