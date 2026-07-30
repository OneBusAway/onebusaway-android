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

import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread
import org.onebusaway.android.R
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.app.di.FirebaseMessagingEntryPoint
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.PushRegistrationEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.region.RegionSubsystems
import org.onebusaway.android.util.CustomApiUrlLabel
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ThemeUtils

@HiltAndroidApp
class Application :
    android.app.Application(),
    Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()

        mApp = this

        // Seed the per-install app UID once, eagerly, before any reader needs it. It has multiple
        // independent direct readers (ObaEndpointResolver sends it as app_uid; the Open311 report path
        // reads it as device_id), so seeding lazily in one reader can't guarantee it for the others.
        // Its absence is also this launch's fresh-install signal: it has been written on first launch
        // far longer than any build that could have left navigation traces behind.
        val freshInstall = PreferenceUtils.getString(ObaApi.APP_UID) == null
        if (freshInstall) {
            PreferenceUtils.saveString(ObaApi.APP_UID, UUID.randomUUID().toString())
        }

        removeLegacyNavigationTraces(freshInstall)

        // Apply the saved theme.
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

        // Keep this device's OBACloud push registration in sync (register on launch / token rotation /
        // opt-in, unregister on opt-out) so service alerts reach riders who never set a trip alarm (#1957).
        PushRegistrationEntryPoint.get(this).start()
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

    /**
     * Removes files and queued jobs left by destination-reminder trace collection in older builds.
     * A one-shot upgrade cost: without the flag this would run `WorkManager.getInstance` (which
     * forces WorkManager initialization, opening its database) on the main thread of every cold
     * start, forever, long after the work and files are gone.
     *
     * A [freshInstall] has nothing to remove — neither the traces nor the queued work can exist in
     * an install that never ran an older build — so it latches without paying that main-thread
     * WorkManager init at all. Only a genuine upgrade does the work, and only until it succeeds.
     */
    private fun removeLegacyNavigationTraces(freshInstall: Boolean) {
        if (PreferenceUtils.getBoolean(LEGACY_NAV_TRACES_REMOVED, false)) return
        if (freshInstall) {
            PreferenceUtils.saveBoolean(LEGACY_NAV_TRACES_REMOVED, true)
            return
        }
        val workManager = WorkManager.getInstance(this)
        val cancellations = listOf(
            workManager.cancelUniqueWork("navigation_log_upload"),
            workManager.cancelUniqueWork("navigation_log_cleanup")
        )
        thread(name = "remove-navigation-traces") {
            // Cancel first: cancelUniqueWork only *requests* cancellation, so deleting while a worker
            // still ran could let it recreate the directory behind us. Awaiting the cancellations
            // closes that window.
            val cancelled = runCatching { cancellations.forEach { it.result.get() } }
                .onFailure { Log.w(TAG, "Unable to cancel legacy navigation work", it) }
                .isSuccess
            // Deleting the recorded traces is the privacy-relevant half, so it happens whether or not
            // the cancellations resolved — it is ordered after them, not conditional on them.
            val filesDeleted = File(filesDir, "ObaNavLog").deleteRecursively()
            // Only latch when everything is actually gone; otherwise retry on the next launch.
            if (filesDeleted && cancelled) PreferenceUtils.saveBoolean(LEGACY_NAV_TRACES_REMOVED, true)
        }
    }

    companion object {
        private const val TAG = "Application"

        /** Latched once the legacy trace files and their queued workers are confirmed gone. */
        private const val LEGACY_NAV_TRACES_REMOVED = "legacy_navigation_traces_removed"

        // Set in onCreate, cleared in onTerminate (emulator-only). Nullable-backed rather than lateinit
        // precisely because onTerminate re-nulls it; get() unwraps it non-null since it's never read
        // before onCreate and every caller dereferences it.
        private var mApp: Application? = null

        @JvmStatic
        fun get(): Application = checkNotNull(mApp) { "Application.get() before onCreate() or after onTerminate()" }
    }
}
