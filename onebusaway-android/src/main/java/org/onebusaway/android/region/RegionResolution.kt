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

import org.onebusaway.android.region.Region

/**
 * Outcome of a region-status refresh (the one-shot result of [RegionRepository.refresh]), as opposed
 * to the persistent [RegionState] the repository exposes as a flow. It replaces the int
 * `currentRegionChanged` flag the legacy `ObaRegionsTask` passed to its callback. The repository
 * performs the region model writes (via `RegionActivator`); the *effects* the task ran inline — the
 * progress dialog, the manual-region picker, the region-found toast, analytics — are mapped from this
 * value by the caller (the HomeViewModel).
 *
 * Moved into the `region` package when region resolution moved into
 * [DefaultRegionRepository]; the pure decision functions below moved with it.
 */
sealed interface RegionStatus {

    /** A custom API URL is set, so no region info is needed. */
    object Skipped : RegionStatus

    /** The build flavor hard-codes a region; it was set and auto-selection was disabled. */
    data class Fixed(val region: Region) : RegionStatus

    /** The current region was auto-selected or changed to [region]. */
    data class Changed(val region: Region) : RegionStatus

    /** A region was already set and remains the best match (contents refreshed silently). */
    object Unchanged : RegionStatus

    /**
     * No region is set and none could be auto-selected, so the user must pick one from [regions]
     * (the usable regions, sorted by name). [resolveRegionStatus] returns this with an empty list as
     * a decision sentinel; [DefaultRegionRepository.refresh] attaches the real list.
     */
    data class NeedsManualSelection(val regions: List<Region>) : RegionStatus

    /** Region info could not be loaded from any source (catastrophic failure). */
    object Failed : RegionStatus
}

/** One week, in milliseconds — the staleness window after which region info is reloaded. */
internal const val REGION_UPDATE_THRESHOLD_MS = 1000L * 60 * 60 * 24 * 7

/**
 * The pure force-reload decision from `HomeActivity.checkRegionStatus()`: reload if there is no
 * region yet, the cache is older than [REGION_UPDATE_THRESHOLD_MS], or the app version increased.
 * [now] is passed in so this stays a stateless helper.
 */
internal fun shouldForceReload(
    hasRegion: Boolean,
    lastUpdate: Long,
    now: Long,
    oldVer: Int,
    newVer: Int
): Boolean = !hasRegion || (now - lastUpdate > REGION_UPDATE_THRESHOLD_MS) || (oldVer < newVer)

/**
 * The pure region-selection branches from `ObaRegionsTask.onPostExecute` (regions compared by id,
 * matching `Region.equals`). [closest] is precomputed by the caller — it is null when
 * auto-selection is off or no usable region is within range.
 */
internal fun resolveRegionStatus(
    current: Region?,
    closest: Region?,
    autoSelect: Boolean
): RegionStatus {
    // The manual-selection cases carry an empty list here; the repository fills in the usable
    // regions (it alone holds the fetched results) before returning to the caller.
    if (!autoSelect) {
        return if (current == null) RegionStatus.NeedsManualSelection(emptyList()) else RegionStatus.Unchanged
    }
    return when {
        current == null && closest != null -> RegionStatus.Changed(closest)
        current == null -> RegionStatus.NeedsManualSelection(emptyList())
        closest != null && current.id != closest.id -> RegionStatus.Changed(closest)
        else -> RegionStatus.Unchanged
    }
}
