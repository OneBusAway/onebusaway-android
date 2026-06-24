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
package org.onebusaway.android.ui.routeinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.ui.nav.NavRoutes

/** ViewModel for the route info screen. */
@HiltViewModel
class RouteInfoViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: RouteInfoRepository,
) : ViewModel() {

    // The route id arrives via SavedStateHandle, keyed by NavRoutes.ARG_ROUTE_ID — populated either by
    // the NavHost destination's nav-arg, or by RouteInfoActivity normalizing its data URI into that
    // extra (the standalone/legacy host).
    private val routeId: String =
        savedState.get<String>(NavRoutes.ARG_ROUTE_ID).orEmpty()

    private val _state = MutableStateFlow<RouteInfoUiState>(RouteInfoUiState.Loading)
    val state: StateFlow<RouteInfoUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Loads (or retries loading) the route metadata and its stops. */
    fun load() {
        _state.value = RouteInfoUiState.Loading
        viewModelScope.launch {
            _state.value = repository.loadRouteInfo(routeId).fold(
                onSuccess = { RouteInfoUiState.Success(it) },
                onFailure = { RouteInfoUiState.Error(it.message.orEmpty()) }
            )
        }
    }
}
