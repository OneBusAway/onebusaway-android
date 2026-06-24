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
package org.onebusaway.android.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel for fetch-once list screens. Holds the [ListUiState] flow and the
 * Loading → Success/Error transition; subclasses expose their own public load/retry entry
 * points that call [load] with the appropriate fetch. Survives configuration changes, so an
 * in-flight load continues across rotation (unlike the AsyncTaskLoaders these replace).
 */
abstract class ListLoadingViewModel<T> : ViewModel() {

    private val _state = MutableStateFlow<ListUiState<T>>(ListUiState.Loading)
    val state: StateFlow<ListUiState<T>> = _state.asStateFlow()

    /** Publishes Loading, then runs [fetch] and publishes its Success or Error. */
    protected fun load(fetch: suspend () -> Result<List<T>>) {
        _state.value = ListUiState.Loading
        viewModelScope.launch {
            _state.value = fetch().fold(
                onSuccess = { ListUiState.Success(it) },
                onFailure = { ListUiState.Error }
            )
        }
    }
}
