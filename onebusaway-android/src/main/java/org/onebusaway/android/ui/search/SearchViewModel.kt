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
package org.onebusaway.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * ViewModel for an incremental search screen: debounces query changes and runs [search],
 * cancelling any in-flight search when the query changes again (transformLatest). Replaces the
 * java.util.Timer debounce in the legacy MySearchFragmentBase.
 *
 * @param search performs the search; failures are shown as [SearchUiState.Error]
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel<T>(
    private val search: suspend (String) -> Result<List<T>>
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** The current search box text. */
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SearchUiState<T>> = _query
        // Clearing the box returns to the hint immediately; typing waits for a pause
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .transformLatest { query ->
            if (query.isEmpty()) {
                emit(SearchUiState.Idle)
            } else {
                emit(SearchUiState.Searching)
                emit(search(query).fold(
                    onSuccess = { SearchUiState.Results(it) },
                    onFailure = { SearchUiState.Error }
                ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)

    fun onQueryChange(text: String) {
        _query.value = text
    }

    companion object {

        /** Same delay as the legacy DELAYED_SEARCH_TIMEOUT. */
        const val SEARCH_DEBOUNCE_MS = 1000L
    }
}
