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
package org.onebusaway.android.ui.home.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.database.oba.RouteFavoritesRepository
import org.onebusaway.android.database.oba.StopFavoritesRepository
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.util.runCatchingCancellable

/** Route- and stop-favorite state for the shared focus banner. */
@HiltViewModel
class FocusBannerViewModel @Inject constructor(
    private val routeFavorites: RouteFavoritesRepository,
    private val stopFavorites: StopFavoritesRepository
) : ViewModel() {

    // Optimistic overrides applied synchronously on tap so back-to-back toggles each build on the last
    // intent instead of the store's (still-lagging) snapshot. Each entry is dropped once the store's
    // flow catches up to it, so a favorite changed on another surface is never masked. Routes and stops
    // keep separate overlays (ids can collide across the two namespaces).
    private val optimisticFavorites = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val optimisticStopFavorites = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val favoriteRouteIds: StateFlow<Set<String>> =
        combine(routeFavorites.favoriteRouteIds(), optimisticFavorites) { stored, overrides ->
            stored.toMutableSet().apply {
                overrides.forEach { (routeId, favorite) -> if (favorite) add(routeId) else remove(routeId) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Latest persisted stop-favorite set, or null until the import-gated store's first emission. Kept
    // distinct from "loaded but empty" so a legacy-starred stop is never shown unstarred (and thus
    // un-unstarrable) during the one-time legacy import window.
    private val storedStopFavorites: StateFlow<Set<String>?> =
        stopFavorites.favoriteStopIds()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True once the persisted stop-favorite set is known; the banner star stays disabled until then. */
    val stopFavoritesReady: StateFlow<Boolean> =
        storedStopFavorites
            .map { it != null }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val favoriteStopIds: StateFlow<Set<String>> =
        combine(storedStopFavorites, optimisticStopFavorites) { stored, overrides ->
            stored.orEmpty().toMutableSet().apply {
                overrides.forEach { (stopId, favorite) -> if (favorite) add(stopId) else remove(stopId) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        // Reconcile: once the store reflects an override, drop it so it stops shadowing the store.
        viewModelScope.launch {
            routeFavorites.favoriteRouteIds().collect { stored ->
                optimisticFavorites.update { overrides ->
                    overrides.filterNot { (routeId, favorite) -> (routeId in stored) == favorite }
                }
            }
        }
        viewModelScope.launch {
            storedStopFavorites.collect { stored ->
                if (stored == null) return@collect
                optimisticStopFavorites.update { overrides ->
                    overrides.filterNot { (stopId, favorite) -> (stopId in stored) == favorite }
                }
            }
        }
    }

    fun toggleRouteFavorite(header: RouteHeader) {
        val routeId = header.routeId ?: return
        val favorite = routeId !in favoriteRouteIds.value
        optimisticFavorites.update { it + (routeId to favorite) }
        viewModelScope.launch {
            routeFavorites.setFavorite(
                routeId = routeId,
                shortName = header.shortName,
                longName = header.longName,
                url = null,
                favorite = favorite
            )
        }
    }

    /**
     * Stars/unstars the focused [stop] straight from the banner — independent of whether its arrivals
     * have loaded (#684). Ensures the stop row exists via [StopFavoritesRepository.setFavorite].
     */
    fun toggleStopFavorite(stop: FocusedStop) {
        // Ignore taps until the persisted set is known — otherwise a legacy-starred stop looks unstarred
        // and this would compute favorite = true, re-starring what's already starred.
        if (!stopFavoritesReady.value) return
        val favorite = stop.id !in favoriteStopIds.value
        optimisticStopFavorites.update { it + (stop.id to favorite) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                stopFavorites.setFavorite(
                    id = stop.id,
                    code = stop.code,
                    name = stop.name,
                    latitude = stop.point.latitude,
                    longitude = stop.point.longitude,
                    favorite = favorite
                )
            }
            // On a persistence failure the store can never reconcile this override, so roll it back —
            // but only if it still matches this request, so a newer tap isn't clobbered.
            if (result.isFailure) {
                optimisticStopFavorites.update { overrides ->
                    if (overrides[stop.id] == favorite) overrides - stop.id else overrides
                }
            }
        }
    }
}
