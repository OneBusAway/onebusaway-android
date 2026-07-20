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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.database.oba.RouteFavoritesRepository
import org.onebusaway.android.map.RouteHeader

/** Route-favorite state for the shared focus banner. Stop favorites remain stop-session state. */
@HiltViewModel
class FocusBannerViewModel @Inject constructor(
    private val routeFavorites: RouteFavoritesRepository
) : ViewModel() {

    // Optimistic overrides applied synchronously on tap so back-to-back toggles each build on the last
    // intent instead of the store's (still-lagging) snapshot. Each entry is dropped once the store's
    // flow catches up to it, so a favorite changed on another surface is never masked.
    private val optimisticFavorites = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val favoriteRouteIds: StateFlow<Set<String>> =
        combine(routeFavorites.favoriteRouteIds(), optimisticFavorites) { stored, overrides ->
            stored.toMutableSet().apply {
                overrides.forEach { (routeId, favorite) -> if (favorite) add(routeId) else remove(routeId) }
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
}
