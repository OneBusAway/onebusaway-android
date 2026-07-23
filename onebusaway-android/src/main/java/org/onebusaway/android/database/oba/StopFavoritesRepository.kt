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

/**
 * Stop starring for the shared focus banner, mirroring [RouteFavoritesRepository]. Exists so the
 * banner's stop star works the moment a stop is focused, rather than only after its arrivals have
 * loaded (#684): [setFavorite] ensures the `stops` row exists before flipping the flag, so a stop
 * that isn't yet in the user-state table (it lives only in the map's `cached_stops` cache until an
 * arrivals load records it) can still be starred.
 */
@Singleton
class StopFavoritesRepository @Inject constructor(
    private val stopDao: StopDao,
    private val regionRepository: RegionRepository,
    private val importGate: ImportGate
) {

    /** The starred stop ids, live and import-gated (so legacy favorites are visible on first read). */
    fun favoriteStopIds(): Flow<Set<String>> = stopDao.favoriteStopIds()
        .onStart { importGate.awaitReady() }
        .map { it.toSet() }
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
    ) {
        importGate.awaitReady()
        stopDao.setFavoriteEnsuringRow(
            identity = StopRecord(
                id = id,
                code = code.orEmpty(),
                name = name.orEmpty(),
                direction = "",
                useCount = 0,
                latitude = latitude,
                longitude = longitude,
                regionId = regionRepository.region.value?.id
            ),
            favorite = if (favorite) 1 else 0
        )
    }
}
