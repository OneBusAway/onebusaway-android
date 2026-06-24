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

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.compose.ListUiState

private class FakeSearchResultsRepository(
    var result: Result<List<SearchResultItem>>
) : SearchResultsRepository {

    val queries = mutableListOf<String>()

    override suspend fun search(query: String): Result<List<SearchResultItem>> {
        queries.add(query)
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchResultsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val items = listOf(
        SearchResultItem.Route("1_8", "8", "Rainier Beach", null),
        SearchResultItem.Stop("1_100", "Broadway & Denny", "S", isFavorite = true, 47.6, -122.3)
    )

    @Test
    fun `state starts Loading and the query is exposed for the title`() = runTest {
        val viewModel = SearchResultsViewModel(FakeSearchResultsRepository(Result.success(items)))

        viewModel.search("8")

        assertEquals(ListUiState.Loading, viewModel.state.value)
        assertEquals("8", viewModel.query.value)
    }

    @Test
    fun `search emits Success with the combined results`() = runTest {
        val repository = FakeSearchResultsRepository(Result.success(items))
        val viewModel = SearchResultsViewModel(repository)

        viewModel.search("8")
        advanceUntilIdle()

        assertEquals(ListUiState.Success(items), viewModel.state.value)
        assertEquals(listOf("8"), repository.queries)
    }

    @Test
    fun `search emits Error when the repository fails`() = runTest {
        val viewModel = SearchResultsViewModel(
            FakeSearchResultsRepository(Result.failure(IOException()))
        )

        viewModel.search("zzz")
        advanceUntilIdle()

        assertEquals(ListUiState.Error, viewModel.state.value)
    }

    @Test
    fun `empty results stay Success so the screen shows no-results`() = runTest {
        val viewModel = SearchResultsViewModel(
            FakeSearchResultsRepository(Result.success(emptyList()))
        )

        viewModel.search("zzz")
        advanceUntilIdle()

        assertEquals(ListUiState.Success(emptyList<SearchResultItem>()), viewModel.state.value)
    }

    @Test
    fun `re-search updates the query and results`() = runTest {
        val repository = FakeSearchResultsRepository(Result.success(items))
        val viewModel = SearchResultsViewModel(repository)
        viewModel.search("8")
        advanceUntilIdle()

        repository.result = Result.success(emptyList())
        viewModel.search("zzz")
        advanceUntilIdle()

        assertEquals("zzz", viewModel.query.value)
        assertEquals(ListUiState.Success(emptyList<SearchResultItem>()), viewModel.state.value)
        assertEquals(listOf("8", "zzz"), repository.queries)
    }

    @Test
    fun `retry re-runs the most recent query`() = runTest {
        val repository = FakeSearchResultsRepository(Result.failure(IOException()))
        val viewModel = SearchResultsViewModel(repository)
        viewModel.search("8")
        advanceUntilIdle()
        assertEquals(ListUiState.Error, viewModel.state.value)

        repository.result = Result.success(items)
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(ListUiState.Success(items), viewModel.state.value)
        assertEquals(listOf("8", "8"), repository.queries)
    }
}
