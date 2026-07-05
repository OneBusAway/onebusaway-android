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

import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import org.onebusaway.android.R
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.app.di.FirebaseMessagingEntryPoint
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
        // starts empty and fills from the device-listener ingestion path (LocationHelper ->
        // LocationSink.update) / its lazy provider poll. Region reads/writes go straight through
        // RegionRepository (via injection or RegionEntryPoint). So nothing to construct here.
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

        @JvmStatic
        fun get(): Application = mApp!!
    }
}
