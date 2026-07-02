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
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.region.Region
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.util.RegionUtils

/**
 * The observable current region — the reactive replacement for reading `Application.currentRegion`
 * statically — and the owner of region *resolution*. Features that must react to a
 * region change (weather, wide alerts, the survey/what's-new gate, the nav-drawer items, the map's
 * re-centering) collect [region]/[state]; the resolution action ([refresh]/[choose]) lives here too,
 * folding in the former `RegionStatusRepository`.
 *
 * One process-singleton instance is held on `Application` (mirroring `getGtfsAlerts()`), so every view
 * model shares the same flow; tests substitute a fake. It lives in the neutral `region` package so both
 * the map and the home UI can depend on it without a backward dependency.
 */
interface RegionRepository {

    /** The current region, or null when none is set (e.g. a custom API URL is configured). */
    val region: StateFlow<Region?>

    /** A synchronous snapshot of the current region — for non-reactive readers (e.g. Java callers
     * via [org.onebusaway.android.app.di.RegionEntryPoint]); reactive consumers should collect [region]. */
    fun currentRegion(): Region? = region.value

    /**
     * Whether a region is currently resolved — the deduped `region != null` projection. Derived once
     * here so feature view models (survey, what's-new gate) consume one canonical predicate instead of
     * each re-deriving it from [region].
     */
    val regionPresent: Flow<Boolean> get() = region.map { it != null }.distinctUntilChanged()

    /**
     * The richer resolution state — what the UI can render directly: a refresh in flight,
     * an active region, a manual choice required, or a failure. [region] mirrors this flow's
     * [RegionState.Active] region (and keeps its last value while [RegionState.Resolving]).
     */
    val state: StateFlow<RegionState>

    /**
     * Resolves the current region (Regions REST API + closest-match auto-select), applies the result via
     * [applyRegion], updates [state]/[region], and returns the one-shot [RegionStatus] outcome for the
     * caller's effects (toast, picker, analytics). Replaces `RegionStatusRepository.refreshRegions`.
     */
    suspend fun refresh(): RegionStatus

    /** Sets the region the user picked from the manual-selection dialog (the old picker's onClick). */
    suspend fun choose(region: Region)

    /**
     * Clears the current region — a custom API URL was entered, or an experimental region was disabled.
     * The non-null counterpart to [choose]; unlike resolution it does no IO (just the activation
     * transaction + a state publish), so it is synchronous and callable from non-coroutine Java writers.
     */
    fun clear()

    /**
     * Applies custom OBA / OTP API URLs (e.g. from the `onebusaway://add-region` deep link), validating
     * each via [ApiUrlValidator]: a valid [obaUrl] is persisted and the current region is [clear]ed (a
     * custom OBA endpoint replaces region resolution); a valid [otpUrl] is persisted. Null or invalid
     * URLs are ignored. The caller is only responsible for parsing the URLs out of the intent.
     */
    fun applyCustomApiUrls(obaUrl: String?, otpUrl: String?)

    /**
     * Applies [region] as the active region directly — the canonical region write (A7): the OBA API
     * context region, the persisted region-id pref, the custom-URL clears, and the state publish. This
     * is what [refresh]/[choose]/[clear] use internally; it is also the seam the instrumented-test writer
     * `Application.setCurrentRegion` calls. The region-*derived* subsystems (Plausible, Open311) react to
     * the published [region] flow rather than being written here.
     */
    fun applyRegion(region: Region?, regionChanged: Boolean)
}

/**
 * The reactive region resolution state, superseding the bare nullable [RegionRepository.region]:
 * what the UI can render directly. [RegionRepository.region] mirrors the [Active] region.
 */
sealed interface RegionState {

    /** A region resolution is in flight and no result is available yet. */
    object Resolving : RegionState

    /** A region is set. [region] is null only when a custom API URL is configured (no region needed). */
    data class Active(val region: Region?) : RegionState

    /** No region could be auto-selected; the user must pick one from [regions] (usable, name-sorted). */
    data class NeedsManualChoice(val regions: List<Region>) : RegionState

    /** Region info could not be loaded from any source (catastrophic failure). */
    object Failed : RegionState
}

/** Mirrors `HomeActivity`'s `checkRegionVer` preference key so the same slot is read/written. */
private const val CHECK_REGION_VER = "checkRegionVer"

/**
 * Default implementation. The observable state lives in a [RegionStateHolder] (so its transitions stay
 * JVM-testable); resolution ([refresh]) is the `Context`-coupled IO ported from
 * `DefaultRegionStatusRepository.refreshRegions`. As of A7 the repo owns the canonical region *write*
 * ([applyRegion]) directly — the OBA API context region + the region prefs — instead of delegating to
 * `Application.applyRegionTransaction`; the region-derived subsystems (Plausible, Open311) observe the
 * [region] flow. A Hilt `@Singleton` (A2) seeded from the OBA context region `initObaRegion` already
 * loaded. The instrumented-test writer reaches it through `RegionEntryPoint` to [applyRegion].
 */
@Singleton
class DefaultRegionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val locationRepository: LocationRepository,
) : RegionRepository {

    // Seeded from persistence (the region-id pref → ContentProvider lookup) so the repo is the sole
    // owner of the current region — there is no external store to read from anymore.
    private val holder = RegionStateHolder(loadPersistedRegion())

    override val region: StateFlow<Region?> get() = holder.region

    override val state: StateFlow<RegionState> get() = holder.state

    /** Loads the persisted current region (by the saved region-id) on construction, or null if none. */
    private fun loadPersistedRegion(): Region? {
        val id = prefs.getLong(R.string.preference_key_region, -1L)
        if (id < 0) return null
        return ObaContract.Regions.get(context, id.toInt())
    }

    override fun applyRegion(region: Region?, regionChanged: Boolean) {
        // The canonical region write: this holder is the single source of truth for the current region
        // (every reader observes [region] or reads its value), plus the persisted region-id pref and the
        // custom-URL clears. The region-derived subsystems (Plausible rebuild, Open311 re-init) react to
        // the published flow, not here.
        prefs.setLong(R.string.preference_key_region, region?.id ?: -1L)
        if (region != null) {
            prefs.setString(R.string.preference_key_oba_api_url, null) // using a region → clear custom OBA URL
            if (regionChanged && region.otpBaseUrl != null) {
                prefs.setString(R.string.preference_key_otp_api_url, null)
                prefs.setBoolean(R.string.preference_key_otp_api_url_version, false)
            }
        }
        holder.activated(region)
    }

    override suspend fun refresh(): RegionStatus = withContext(Dispatchers.IO) {
        holder.resolving()

        // A custom API URL means we don't use the Regions API at all (region stays null).
        if (prefs.getString(R.string.preference_key_oba_api_url, null)?.isNotEmpty() == true) {
            holder.activated(null)
            return@withContext RegionStatus.Skipped
        }

        // A build flavor may hard-code its region; set it and disable auto-selection.
        if (BuildConfig.USE_FIXED_REGION) {
            val region = RegionUtils.getRegionFromBuildFlavor()
            RegionUtils.saveToProvider(context, listOf(region))
            applyRegion(region, true)
            prefs.setBoolean(R.string.preference_key_auto_select_region, false)
            return@withContext RegionStatus.Fixed(region)
        }

        // Force a server reload when we have no region, the cache has expired, or the app updated.
        val current = region.value
        val newVer = appVersionCode()
        val force = shouldForceReload(
            hasRegion = current != null,
            lastUpdate = prefs.getLong(R.string.preference_key_last_region_update, 0),
            now = System.currentTimeMillis(),
            oldVer = prefs.getInt(CHECK_REGION_VER, 0),
            newVer = newVer
        )
        prefs.setInt(CHECK_REGION_VER, newVer)

        val results = RegionUtils.getRegions(context, force)
        if (results == null) {
            holder.failed()
            return@withContext RegionStatus.Failed
        }

        val autoSelect = prefs.getBoolean(R.string.preference_key_auto_select_region, true)
        // getClosestRegion uses Location.distanceTo, so only compute it when auto-selecting.
        val closest = if (autoSelect) {
            RegionUtils.getClosestRegion(results, locationRepository.lastKnownLocation(), true)
        } else {
            null
        }

        when (val status = resolveRegionStatus(current, closest, autoSelect)) {
            is RegionStatus.Changed -> {
                applyRegion(status.region, true)
                status
            }
            // Same region as before: refresh its contents silently (auto-select only).
            RegionStatus.Unchanged -> {
                if (autoSelect && closest != null) {
                    applyRegion(closest, false)
                } else {
                    holder.activated(current) // clear the transient Resolving; region is unchanged
                }
                status
            }
            is RegionStatus.NeedsManualSelection -> {
                // Attach the picker list (usable regions, name-sorted) to the decision sentinel.
                val regions = results.filter { RegionUtils.isRegionUsable(it) }.sortedBy { it.name }
                holder.needsChoice(regions)
                RegionStatus.NeedsManualSelection(regions)
            }
            else -> status // Skipped / Fixed are returned earlier; nothing else reaches here.
        }
    }

    override suspend fun choose(region: Region) = withContext(Dispatchers.IO) {
        applyRegion(region, true)
    }

    override fun clear() {
        applyRegion(null, true)
    }

    override fun applyCustomApiUrls(obaUrl: String?, otpUrl: String?) {
        // Order matters: persist the OBA URL first, then clear() — clear() applies a null region, which
        // leaves the custom OBA URL pref untouched (applyRegion only clears it when a region is set).
        if (obaUrl != null && ApiUrlValidator.validateUrl(obaUrl)) {
            prefs.setString(R.string.preference_key_oba_api_url, obaUrl)
            clear()
        }
        if (otpUrl != null && ApiUrlValidator.validateUrl(otpUrl)) {
            prefs.setString(R.string.preference_key_otp_api_url, otpUrl)
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Int = try {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_META_DATA).versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }
}
