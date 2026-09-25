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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.isNotFound
import org.onebusaway.android.app.di.DefaultDispatcher
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.TimeProvider

/**
 * Loads the service named by [NavRoutes.ARG_ONDEMAND_SERVICE_ID] and presents it for the page. The
 * booking line depends on the clock as well as the service, so [representNow] re-presents the loaded
 * service when the page resumes (say, back from the dialer) without fetching it again.
 */
@HiltViewModel
class OnDemandServiceViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dataSource: OnDemandDataSource,
    private val timeProvider: TimeProvider,
    // presentService walks every rule's calendar day by day, which is too much for the main thread.
    @param:DefaultDispatcher private val presentDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val serviceId: String = requireNotNull(savedState[NavRoutes.ARG_ONDEMAND_SERVICE_ID]) { "on-demand service page requires a service id" }

    private val _state = MutableStateFlow<OnDemandServiceUiState>(OnDemandServiceUiState.Loading)
    val state: StateFlow<OnDemandServiceUiState> = _state.asStateFlow()

    // The fetch or presentation in flight, cancelled by the next one so a slow earlier answer can't
    // land last.
    private var job: Job? = null

    // The last service fetched, kept to re-present; null while a fetch is in flight or after it failed.
    private var loaded: OnDemandService? = null

    init {
        load()
    }

    fun retry() = load()

    /** Re-presents the loaded service at the current time; a no-op until a service has loaded. */
    fun representNow() {
        val service = loaded ?: return
        job?.cancel()
        job = viewModelScope.launch { _state.value = present(service) }
    }

    private fun load() {
        job?.cancel()
        loaded = null
        _state.value = OnDemandServiceUiState.Loading
        job = viewModelScope.launch {
            _state.value = when (val result = dataSource.service(serviceId)) {
                is OnDemandResult.Loaded -> {
                    loaded = result.value
                    present(result.value)
                }
                is OnDemandResult.Failed -> if (result.cause.isNotFound) OnDemandServiceUiState.NotFound else OnDemandServiceUiState.Error
                OnDemandResult.Unsupported -> OnDemandServiceUiState.Error
            }
        }
    }

    // The deadline is computed against the device wall clock (spec §6.3), never the envelope's
    // currentTime, which sits on the long-cache tier.
    private suspend fun present(service: OnDemandService): OnDemandServiceUiState.Content {
        val now = Instant.ofEpochMilli(timeProvider.now())
        return withContext(presentDispatcher) { presentService(service, now) }
    }
}
