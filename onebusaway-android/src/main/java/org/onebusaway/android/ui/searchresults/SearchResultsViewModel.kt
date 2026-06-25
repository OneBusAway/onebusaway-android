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
package org.onebusaway.android.ui.searchresults

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onebusaway.android.ui.compose.ListLoadingViewModel

/**
 * ViewModel for the combined search results screen. Unlike the fetch-once list screens, the
 * query arrives from the hosting activity's search intent (and again on re-search), so there is
 * no init-time load.
 */
@HiltViewModel
class SearchResultsViewModel @Inject constructor(
    private val repository: SearchResultsRepository
) : ListLoadingViewModel<SearchResultItem>() {

    private val _query = MutableStateFlow("")

    /** The current search query, surfaced for the screen title. */
    val query: StateFlow<String> = _query.asStateFlow()

    /** Runs (or re-runs) the search for [query]. */
    fun search(query: String) {
        _query.value = query
        load { repository.search(query) }
    }

    /** Retries the most recent search (e.g. after a communication error). */
    fun retry() {
        if (_query.value.isNotEmpty()) {
            search(_query.value)
        }
    }
}
