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

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Fake search: records queries, answers with a settable result (optionally gated). */
    private class FakeSearch {

        var result: Result<List<String>> = Result.success(emptyList())

        var gate: CompletableDeferred<Unit>? = null

        val queries = mutableListOf<String>()

        val cancelledQueries = mutableListOf<String>()

        suspend fun search(query: String): Result<List<String>> {
            queries.add(query)
            try {
                gate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelledQueries.add(query)
                throw e
            }
            return result
        }
    }

    // SearchViewModel.state is shared WhileSubscribed, so each test must hold a collector
    // open (backgroundScope is auto-cancelled when runTest finishes)
    private fun TestScope.collectState(viewModel: SearchViewModel<String>) {
        backgroundScope.launch { viewModel.state.collect { } }
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val viewModel = SearchViewModel(FakeSearch()::search)
        collectState(viewModel)

        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `typing does not search until the debounce window elapses`() = runTest {
        val fake = FakeSearch()
        val viewModel = SearchViewModel(fake::search)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.onQueryChange("44")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS - 1)

        assertEquals(SearchUiState.Idle, viewModel.state.value)
        assertEquals(emptyList<String>(), fake.queries)
    }

    @Test
    fun `search fires after the debounce window with results`() = runTest {
        val fake = FakeSearch().apply { result = Result.success(listOf("44", "545")) }
        val viewModel = SearchViewModel(fake::search)
        collectState(viewModel)

        viewModel.onQueryChange("4")
        advanceUntilIdle()

        assertEquals(SearchUiState.Results(listOf("44", "545")), viewModel.state.value)
        assertEquals(listOf("4"), fake.queries)
    }

    @Test
    fun `rapid retyping within the window searches only the final query`() = runTest {
        val fake = FakeSearch().apply { result = Result.success(listOf("545")) }
        val viewModel = SearchViewModel(fake::search)
        collectState(viewModel)

        viewModel.onQueryChange("5")
        advanceTimeBy(500)
        viewModel.onQueryChange("54")
        advanceTimeBy(500)
        viewModel.onQueryChange("545")
        advanceUntilIdle()

        assertEquals(listOf("545"), fake.queries)
        assertEquals(SearchUiState.Results(listOf("545")), viewModel.state.value)
    }

    @Test
    fun `retyping during an in-flight search cancels it`() = runTest {
        val fake = FakeSearch().apply { gate = CompletableDeferred() }
        val viewModel = SearchViewModel(fake::search)
        collectState(viewModel)

        viewModel.onQueryChange("first")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertEquals(SearchUiState.Searching, viewModel.state.value)
        assertEquals(listOf("first"), fake.queries)

        viewModel.onQueryChange("second")
        fake.gate = null
        fake.result = Result.success(listOf("2nd"))
        advanceUntilIdle()

        assertEquals(listOf("first"), fake.cancelledQueries)
        assertEquals(listOf("first", "second"), fake.queries)
        assertEquals(SearchUiState.Results(listOf("2nd")), viewModel.state.value)
    }

    @Test
    fun `clearing the query returns to Idle immediately`() = runTest {
        val fake = FakeSearch().apply { result = Result.success(listOf("44")) }
        val viewModel = SearchViewModel(fake::search)
        collectState(viewModel)

        viewModel.onQueryChange("44")
        advanceUntilIdle()
        assertEquals(SearchUiState.Results(listOf("44")), viewModel.state.value)

        viewModel.onQueryChange("")
        // No debounce wait needed for an empty query
        advanceTimeBy(1)

        assertEquals(SearchUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `search failure shows Error`() = runTest {
        val fake = FakeSearch().apply { result = Result.failure(IOException()) }
        val viewModel = SearchViewModel(fake::search)
        collectState(viewModel)

        viewModel.onQueryChange("zzz")
        advanceUntilIdle()

        assertEquals(SearchUiState.Error, viewModel.state.value)
    }

    @Test
    fun `empty results stay Results so the UI shows no-results rather than an error`() = runTest {
        val fake = FakeSearch().apply { result = Result.success(emptyList()) }
        val viewModel = SearchViewModel(fake::search)
        collectState(viewModel)

        viewModel.onQueryChange("zzz")
        advanceUntilIdle()

        assertEquals(SearchUiState.Results(emptyList<String>()), viewModel.state.value)
    }
}
