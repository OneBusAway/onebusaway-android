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
package org.onebusaway.android.database.oba

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.KeyedMutex
import org.onebusaway.android.util.MyTextUtils

/**
 * Stop starring and renaming for the shared focus banner, mirroring [RouteFavoritesRepository]. Exists
 * so the banner's stop star works the moment a stop is focused, rather than only after its arrivals
 * have loaded (#684): [setFavorite] ensures the `stops` row exists before flipping the flag, so a stop
 * that isn't yet in the user-state table (it lives only in the map's `cached_stops` cache until an
 * arrivals load records it) can still be starred. [setUserName] does the same for a custom name (#2414).
 */
@Singleton
class StopFavoritesRepository @Inject constructor(
    private val stopDao: StopDao,
    private val regionRepository: RegionRepository,
    private val importGate: ImportGate
) {

    // Serializes writes per stop id so two rapid taps on the same star (star then unstar) can't reach
    // the store out of order and leave it disagreeing with the last tap (#2001). Both stop-star
    // surfaces funnel through [setFavorite], so guarding here covers them uniformly. Every rename, from
    // any surface, goes through [setUserName] and takes the same guard: a star and a rename that both
    // find the row absent would otherwise each insert it, the second insert replacing the first's write.
    private val writeGuard = KeyedMutex<String>()

    /** The starred stop ids, live and import-gated (so legacy favorites are visible on first read). */
    fun favoriteStopIds(): Flow<Set<String>> = stopDao.favoriteStopIds()
        .onStart { importGate.awaitReady() }
        .map { it.toSet() }
        .distinctUntilChanged()

    /** The stop's official and custom names, live and import-gated; null when the stop has no row. */
    fun names(stopId: String): Flow<StopNamesRow?> = stopDao.names(stopId)
        .onStart { importGate.awaitReady() }
        .distinctUntilChanged()

    /**
     * Stars (or unstars) a stop. Ensures the `stops` row exists first — inserting the identity
     * (id/name/code/coords + current region) only when absent, so an existing row's user name and
     * use-count are never clobbered — then sets the flag. Gated on the one-time legacy import so the
     * write can't be undone by a later import merge.
     */
    suspend fun setFavorite(
        id: String,
        code: String?,
        name: String?,
        latitude: Double,
        longitude: Double,
        favorite: Boolean
    ) = writeGuard.withLock(id) {
        importGate.awaitReady()
        stopDao.setFavoriteEnsuringRow(
            identity = identity(id, code, name, latitude, longitude),
            favorite = if (favorite) 1 else 0
        )
    }

    /**
     * Gives a stop a custom name — or, when [userName] is blank or the official name, removes it
     * ([normalizedUserName]). Ensures the `stops` row exists first, like [setFavorite], without
     * starring it.
     */
    suspend fun setUserName(
        id: String,
        code: String?,
        name: String?,
        latitude: Double,
        longitude: Double,
        userName: String
    ) = writeGuard.withLock(id) {
        importGate.awaitReady()
        stopDao.setUserNameEnsuringRow(
            identity = identity(id, code, name, latitude, longitude),
            userName = userName
        )
    }

    private fun identity(id: String, code: String?, name: String?, latitude: Double, longitude: Double) = StopRecord(
        id = id,
        code = code.orEmpty(),
        // Formatted as an arrivals load records it (markStopUsed), so the official name a rename is
        // compared against reads the same whichever write created the row.
        name = MyTextUtils.formatDisplayText(name).orEmpty(),
        direction = "",
        useCount = 0,
        latitude = latitude,
        longitude = longitude,
        regionId = regionRepository.region.value?.id
    )
}
