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
package org.onebusaway.android.ui.ondemand

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.isNotFound
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.nav.NavRoutes

/** Loads the service named by [NavRoutes.ARG_ONDEMAND_SERVICE_ID] and presents it for the page. */
@HiltViewModel
class OnDemandServiceViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dataSource: OnDemandDataSource
) : ViewModel() {

    private val serviceId: String = requireNotNull(savedState[NavRoutes.ARG_ONDEMAND_SERVICE_ID]) { "on-demand service page requires a service id" }

    private val _state = MutableStateFlow<OnDemandServiceUiState>(OnDemandServiceUiState.Loading)
    val state: StateFlow<OnDemandServiceUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = OnDemandServiceUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = dataSource.service(serviceId)) {
                // The deadline is computed against the device wall clock (spec §6.3), never the
                // envelope's currentTime, which sits on the long-cache tier.
                is OnDemandResult.Loaded -> presentService(result.value, Instant.ofEpochMilli(WallTime.now().epochMs))
                is OnDemandResult.Failed -> if (result.cause.isNotFound) OnDemandServiceUiState.NotFound else OnDemandServiceUiState.Error
                OnDemandResult.Unsupported -> OnDemandServiceUiState.Error
            }
        }
    }
}
