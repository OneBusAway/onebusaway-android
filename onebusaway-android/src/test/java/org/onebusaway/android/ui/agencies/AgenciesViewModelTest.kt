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
package org.onebusaway.android.ui.agencies

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.compose.ListUiState

private class FakeAgenciesRepository(
    var result: Result<List<AgencyItem>>
) : AgenciesRepository {

    override suspend fun getAgencies(): Result<List<AgencyItem>> = result
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgenciesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val agencies = listOf(
        AgencyItem("1", "King County Metro", "https://kingcounty.gov/metro"),
        AgencyItem("40", "Sound Transit", null)
    )

    @Test
    fun `initial state is Loading before the load completes`() = runTest {
        val viewModel = AgenciesViewModel(FakeAgenciesRepository(Result.success(agencies)))

        assertEquals(ListUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `load emits Success with the repository's agencies`() = runTest {
        val viewModel = AgenciesViewModel(FakeAgenciesRepository(Result.success(agencies)))

        advanceUntilIdle()

        assertEquals(ListUiState.Success(agencies), viewModel.state.value)
    }

    @Test
    fun `load emits Error when the repository fails`() = runTest {
        val viewModel = AgenciesViewModel(FakeAgenciesRepository(Result.failure(IOException())))

        advanceUntilIdle()

        assertEquals(ListUiState.Error, viewModel.state.value)
    }

    @Test
    fun `retry after a failure goes through Loading and recovers`() = runTest {
        val repository = FakeAgenciesRepository(Result.failure(IOException()))
        val viewModel = AgenciesViewModel(repository)
        advanceUntilIdle()
        assertEquals(ListUiState.Error, viewModel.state.value)

        repository.result = Result.success(agencies)
        viewModel.load()

        assertEquals(ListUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(ListUiState.Success(agencies), viewModel.state.value)
    }
}
