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
package org.onebusaway.android.region

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.R
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.RegionDao
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.util.RegionUtils

/**
 * The region cache, backed by Room (the legacy `RegionUtils.getRegionsFromProvider`/`saveToProvider`
 * over the ContentProvider). Every read/write awaits the one-time importer via [ImportGate] first, so
 * a cached region survives the migration. Maps between the Room rows and the domain [Region] through
 * the pure [RegionMapper]. The server/bundled-resource fetchers stay in [RegionUtils]; this owns only
 * the local cache and the source-fallback orchestration that used to live in `RegionUtils.getRegions`.
 */
@Singleton
class RegionCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val regionDao: RegionDao,
    private val importGate: ImportGate,
    private val prefs: PreferencesRepository
) {

    /** The cached regions, or an empty list when the cache is empty. */
    suspend fun cachedRegions(): List<Region> {
        importGate.awaitReady()
        return regionDao.getAllRegions().map { RegionMapper.toRegion(it) }
    }

    /** The cached regions, or null when the cache is empty (for the source-fallback short-circuits). */
    private suspend fun cachedRegionsOrNull(): List<Region>? = cachedRegions().takeIf { it.isNotEmpty() }

    /** The cached region with [id], or null if it isn't cached. */
    suspend fun cachedRegion(id: Long): Region? {
        importGate.awaitReady()
        return regionDao.getRegion(id)?.let { RegionMapper.toRegion(it) }
    }

    /** Replaces the whole cache with the usable subset of [regions] (the legacy `saveToProvider`). */
    suspend fun save(regions: List<Region>) {
        importGate.awaitReady()
        val usable = regions
            .filter { RegionUtils.isRegionUsable(context, it) }
            .map { RegionMapper.toEntities(it) }
        regionDao.replaceAll(usable)
    }

    /**
     * The legacy `RegionUtils.getRegions` source-fallback: prefer the cache (unless [forceReload]),
     * then the server, then — if forced — the cache again, then the bundled resource. A server or
     * resource result is persisted. Null only when every source failed.
     *
     * Every return path includes the user's custom regions (#2027). The cache paths get them for free
     * (they're rows like any other); the server/bundled paths have to union them back in, since the
     * regions directory has never heard of them — without that, a forced reload would drop the rider's
     * own region out of the picker list.
     */
    suspend fun loadRegions(forceReload: Boolean): List<Region>? {
        if (!forceReload) {
            cachedRegionsOrNull()?.let { return it }
        }

        var results: List<Region>? = RegionUtils.getRegionsFromServer(context)
        if (results.isNullOrEmpty()) {
            if (forceReload) {
                cachedRegionsOrNull()?.let { return it }
            }
            results = RegionUtils.getRegionsFromResources(context)
            // An empty bundled result is a total failure too: return null (so callers surface "couldn't
            // load regions") instead of falling through to save([]), which would wipe the cache.
            if (results.isNullOrEmpty()) return null
        } else {
            prefs.setLong(R.string.preference_key_last_region_update, System.currentTimeMillis())
        }

        save(results)
        return results + customRegions()
    }

    /** The user-added custom regions (#2027); empty when there are none. */
    private suspend fun customRegions(): List<Region> {
        importGate.awaitReady()
        return regionDao.getCustomRegions().map { RegionMapper.toRegion(it) }
    }

    /**
     * Persists [request] as a custom region and returns it.
     *
     * Re-adding a server already present updates that region in place — matched on the exact
     * [Region.obaBaseUrl], so it keeps its id and stays the same entity to anything holding a reference
     * (the persisted region-id preference, most of all). Otherwise it gets a fresh id from
     * [nextCustomRegionId]. Deliberately does *not* go through [save], whose `isRegionUsable` filter and
     * whole-cache replace are the directory refresh's business.
     */
    suspend fun saveCustom(request: CustomRegionRequest): Region {
        importGate.awaitReady()
        val existingId = regionDao.customRegionIdByObaUrl(request.obaBaseUrl)
        val region = customRegion(existingId ?: nextCustomRegionId(regionDao.minRegionId()), request)
        regionDao.upsertCustomRegion(RegionMapper.toEntities(region))
        return region
    }

    /** Removes a custom region. A directory region is never deleted here — the refresh owns those rows. */
    suspend fun deleteCustom(region: Region) {
        importGate.awaitReady()
        if (!region.custom) return
        regionDao.deleteRegionById(region.id)
    }
}
