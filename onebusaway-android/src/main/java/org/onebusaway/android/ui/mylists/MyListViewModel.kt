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
package org.onebusaway.android.ui.mylists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.onebusaway.android.ui.compose.ListUiState

/**
 * Drives a single My-tab list (one of the starred/recent stop/route sources). The list re-emits
 * automatically on content changes via [MyListRepository.observe]; [remove] and [clearAll] mutate
 * the provider and the observer feeds the change back as a new [ListUiState.Success].
 */
class MyListViewModel<T>(private val repository: MyListRepository<T>) : ViewModel() {

    val state: StateFlow<ListUiState<T>> = repository.observe()
        .map<List<T>, ListUiState<T>> { ListUiState.Success(it) }
        .catch { emit(ListUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState.Loading)

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    /** Changes the sort order; the repository re-emits the list (no-op for non-sorting lists). */
    fun setSort(order: Int) = repository.setSort(order)
}
