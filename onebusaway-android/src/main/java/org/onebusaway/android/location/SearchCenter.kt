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
package org.onebusaway.android.location

import android.location.Location
import javax.inject.Inject
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.span
import org.onebusaway.android.util.locationOf

/**
 * Resolves the location to center a "near me" search on: the device's last known location, falling
 * back to the current region's center, or null when neither is available.
 *
 * Injectable (unscoped) — both collaborators are Hilt singletons, so Hilt-reachable callers get it
 * for free; the two non-Hilt Compose call sites build it from the [org.onebusaway.android.app.di]
 * entry points. Replaces the static `LocationUtils.getSearchCenter`.
 */
class SearchCenter @Inject constructor(
    private val locationRepository: LocationRepository,
    private val regionRepository: RegionRepository
) {
    /** Last known device location, else [regionCenter], else null. */
    fun current(): Location? = locationRepository.lastKnownLocation() ?: regionCenter()

    /** The current region's center, or null when no region is set. */
    fun regionCenter(): Location? = regionRepository.region.value?.span()?.let { locationOf(it.centerLat, it.centerLon) }

    companion object {
        /** Radius (m) for the wide fallback search around the region center. */
        const val DEFAULT_SEARCH_RADIUS_METERS = 40000
    }
}
