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
package org.onebusaway.android.ui.regions

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.compose.ListUiState

private class FakeRegionsRepository(
    var result: Result<List<RegionItem>>,
    var selectRegionReturns: Boolean = false
) : RegionsRepository {

    var lastRefresh: Boolean? = null

    var selectedId: Long? = null

    override suspend fun getRegions(refresh: Boolean): Result<List<RegionItem>> {
        lastRefresh = refresh
        return result
    }

    override suspend fun selectRegion(id: Long): Boolean {
        selectedId = id
        return selectRegionReturns
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RegionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val regions = listOf(
        RegionItem(1, "Puget Sound", 1500f, isCurrent = true),
        RegionItem(2, "Tampa Bay", 4_500_000f, isCurrent = false),
        RegionItem(3, "No-location Region", null, isCurrent = false)
    )

    @Test
    fun `initial state is Loading before the load completes`() = runTest {
        val viewModel = RegionsViewModel(FakeRegionsRepository(Result.success(regions)))

        assertEquals(ListUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `load emits Success with the repository's regions`() = runTest {
        val repository = FakeRegionsRepository(Result.success(regions))
        val viewModel = RegionsViewModel(repository)

        advanceUntilIdle()

        assertEquals(ListUiState.Success(regions), viewModel.state.value)
        assertEquals(false, repository.lastRefresh)
    }

    @Test
    fun `load emits Error when the repository fails`() = runTest {
        val viewModel = RegionsViewModel(FakeRegionsRepository(Result.failure(IOException())))

        advanceUntilIdle()

        assertEquals(ListUiState.Error, viewModel.state.value)
    }

    @Test
    fun `retry after a failure goes through Loading and recovers`() = runTest {
        val repository = FakeRegionsRepository(Result.failure(IOException()))
        val viewModel = RegionsViewModel(repository)
        advanceUntilIdle()
        assertEquals(ListUiState.Error, viewModel.state.value)

        repository.result = Result.success(regions)
        viewModel.load()

        assertEquals(ListUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(ListUiState.Success(regions), viewModel.state.value)
    }

    @Test
    fun `refresh forces a server fetch`() = runTest {
        val repository = FakeRegionsRepository(Result.success(regions))
        val viewModel = RegionsViewModel(repository)
        advanceUntilIdle()

        viewModel.load(refresh = true)
        advanceUntilIdle()

        assertEquals(true, repository.lastRefresh)
        assertEquals(ListUiState.Success(regions), viewModel.state.value)
    }

    @Test
    fun `selectRegion delegates to the repository and returns its result`() = runTest {
        val repository = FakeRegionsRepository(Result.success(regions))
        val viewModel = RegionsViewModel(repository)

        repository.selectRegionReturns = true
        assertTrue(viewModel.selectRegion(regions[1]))
        assertEquals(2L, repository.selectedId)

        repository.selectRegionReturns = false
        assertFalse(viewModel.selectRegion(regions[0]))
        assertEquals(1L, repository.selectedId)
    }
}
