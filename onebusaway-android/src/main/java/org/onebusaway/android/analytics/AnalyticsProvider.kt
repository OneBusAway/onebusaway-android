/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.analytics

import android.content.Context
import com.onebusaway.plausible.android.Plausible
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.onebusaway.android.app.di.AppScope
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository

/**
 * Owns the region-derived analytics emitters — the [Plausible] and [UmamiAnalytics] instances — as an
 * injected app-singleton, replacing the `Application.getPlausibleInstance()` / `getUmamiInstance()`
 * service-locator reads. Both emitters point at the *current* region's analytics servers, so they are
 * rebuilt reactively whenever the [RegionRepository] region changes (the same "everything that cares
 * observes the region flow" form as [org.onebusaway.android.region.RegionSubsystems]).
 *
 * Injectable consumers inject this directly; the context-less static / Java call sites reach it via
 * [org.onebusaway.android.app.di.AnalyticsEntryPoint].
 */
@Singleton
class AnalyticsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    regionRepository: RegionRepository,
    @AppScope scope: CoroutineScope,
) {

    /** The Plausible emitter for the current region, or null when the region has no Plausible server. */
    @Volatile
    var plausible: Plausible? = null
        private set

    /** The Umami emitter for the current region, or null when the region has no Umami config. */
    @Volatile
    var umami: UmamiAnalytics? = null
        private set

    init {
        // Seed synchronously from the current region so the emitters are ready the moment the provider
        // is first injected — the region collector below runs on the @AppScope (IO) dispatcher, so it
        // would otherwise leave a null window on first use. The collector re-applies the current value
        // once on subscription (a harmless rebuild) and thereafter tracks region changes.
        rebuild(regionRepository.region.value)
        scope.launch {
            regionRepository.region.collect { rebuild(it) }
        }
    }

    private fun rebuild(region: Region?) {
        plausible = buildPlausible(region)
        umami = buildUmami(region)
    }

    private fun buildPlausible(region: Region?): Plausible? {
        if (region?.obaBaseUrl == null || region.plausibleAnalyticsServerUrl == null) return null
        // Fire-and-forget telemetry must never throw on a malformed URL (this runs in a long-lived
        // region collector); a bad host just disables Plausible for that region.
        val domain = hostOf(region.obaBaseUrl) ?: return null
        return Plausible(context, domain, region.plausibleAnalyticsServerUrl)
    }

    private fun buildUmami(region: Region?): UmamiAnalytics? {
        if (region?.obaBaseUrl == null ||
            region.umamiAnalyticsUrl == null ||
            region.umamiAnalyticsId == null
        ) {
            return null
        }
        val host = hostOf(region.obaBaseUrl) ?: return null
        return UmamiAnalytics(region.umamiAnalyticsUrl, region.umamiAnalyticsId, host)
    }

    private fun hostOf(url: String): String? = try {
        URI(url).host
    } catch (e: Exception) {
        null
    }
}
