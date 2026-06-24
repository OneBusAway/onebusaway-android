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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.compose.ListUiState

private class FakeMyListRepository(
    private val items: Flow<List<String>>
) : MyListRepository<String> {
    val removed = mutableListOf<String>()
    var clearedCount = 0

    override fun observe(): Flow<List<String>> = items
    override suspend fun remove(id: String) {
        removed += id
    }

    override suspend fun clearAll() {
        clearedCount++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MyListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = MyListViewModel(FakeMyListRepository(flowOf(listOf("a"))))

        assertEquals(ListUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `state becomes Success with the repository's items once collected`() = runTest {
        val viewModel = MyListViewModel(FakeMyListRepository(flowOf(listOf("a", "b"))))

        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        assertEquals(ListUiState.Success(listOf("a", "b")), viewModel.state.value)
        job.cancel()
    }

    @Test
    fun `remove forwards the id to the repository`() = runTest {
        val repository = FakeMyListRepository(flowOf(emptyList()))
        val viewModel = MyListViewModel(repository)

        viewModel.remove("1_42")
        advanceUntilIdle()

        assertEquals(listOf("1_42"), repository.removed)
    }

    @Test
    fun `clearAll forwards to the repository`() = runTest {
        val repository = FakeMyListRepository(flowOf(emptyList()))
        val viewModel = MyListViewModel(repository)

        viewModel.clearAll()
        advanceUntilIdle()

        assertEquals(1, repository.clearedCount)
    }
}
