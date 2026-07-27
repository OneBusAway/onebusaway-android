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
package org.onebusaway.android.ui.regions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionCache
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.RegionUtils

/**
 * A region as displayed on the region picker screen, decoupled from the io/elements
 * response types.
 *
 * @param distanceMeters distance from the user's last known location, or null when no
 * location (or no distance) is available
 * @param custom whether the rider added this region themselves (an `onebusaway://add-region` link,
 * #2027) rather than it coming from the OBA regions directory — the ones that can be removed
 */
data class RegionItem(
    val id: Long,
    val name: String,
    val distanceMeters: Float?,
    val custom: Boolean = false
)

/** Provides the list of available OBA regions and handles manual region selection. */
interface RegionsRepository {

    /**
     * Loads the available regions. When [refresh] is true, forces a fetch from the regions
     * server instead of the local provider cache.
     */
    suspend fun getRegions(refresh: Boolean): Result<List<RegionItem>>

    /**
     * Makes the region with the given id the app's current region, disabling automatic
     * region selection if it was enabled (matching the legacy picker behavior).
     *
     * @return true if this call disabled automatic region selection (drives the toast)
     */
    suspend fun selectRegion(id: Long): Boolean

    /**
     * Removes the custom region with the given id. A directory region is ignored — only regions the
     * rider added themselves can be removed. Removing the current region re-resolves, so the app
     * lands on a real region rather than pointing at one that no longer exists.
     */
    suspend fun removeRegion(id: Long)
}

/**
 * Default implementation wrapping the blocking [RegionUtils.getRegions] call (local provider,
 * then server, then bundled resources; null only when every source failed). All Android
 * statics (Application, analytics, preferences) are quarantined here so [RegionsViewModel]
 * stays JVM-testable.
 */
class DefaultRegionsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val regionCache: RegionCache,
    private val locationRepository: LocationRepository,
    private val obaAnalytics: ObaAnalytics
) : RegionsRepository {

    // Domain objects from the last successful load, so selectRegion(id) can resolve the Region
    // that RegionRepository.choose() needs. @Volatile: written on Dispatchers.IO in getRegions, read
    // in selectRegion from a separately-launched coroutine.
    @Volatile
    private var regionsById: Map<Long, Region> = emptyMap()

    override suspend fun getRegions(refresh: Boolean): Result<List<RegionItem>> = withContext(Dispatchers.IO) {
        val regions = regionCache.loadRegions(refresh)
            ?: return@withContext Result.failure(
                IOException("Regions could not be loaded from any source")
            )
        val usable = regions.filter { RegionUtils.isRegionUsable(context, it) }
        regionsById = usable.associateBy { it.id }

        val location = locationRepository.lastKnownLocation()
        val items = usable.map { region ->
            RegionItem(
                id = region.id,
                name = region.name,
                distanceMeters = location?.let { RegionUtils.getDistanceAway(region, it) },
                custom = region.custom
            )
        }
        // Sort by distance only when we have a location, like the legacy picker
        Result.success(
            if (location != null) {
                items.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
            } else {
                items
            }
        )
    }

    override suspend fun selectRegion(id: Long): Boolean {
        val region = regionsById[id] ?: return false
        regionRepository.choose(region)

        // If we're currently auto-selecting regions, disable this so it doesn't override
        // the manual setting
        val wasAutoSelectEnabled = prefs.getBoolean(R.string.preference_key_auto_select_region, true)
        if (wasAutoSelectEnabled) {
            prefs.setBoolean(R.string.preference_key_auto_select_region, false)
        }

        obaAnalytics.reportUiEvent(
            PlausibleAnalytics.REPORT_REGION_EVENT_URL,
            context.getString(R.string.region_selected_manually),
            region.name
        )
        return wasAutoSelectEnabled
    }

    // deleteCustomRegion ignores a directory region itself, so there is nothing to check here beyond
    // resolving the id. No analytics: the forced-choice picker deletes through the same domain call
    // without reporting, and an event on only one of two entry points describes nothing.
    override suspend fun removeRegion(id: Long) = withContext(Dispatchers.IO) {
        regionsById[id]?.let { regionRepository.deleteCustomRegion(it) } ?: Unit
    }
}
