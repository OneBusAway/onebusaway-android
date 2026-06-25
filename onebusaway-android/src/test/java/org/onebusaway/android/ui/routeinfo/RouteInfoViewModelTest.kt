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
package org.onebusaway.android.ui.routeinfo

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.nav.NavRoutes

private class FakeRouteInfoRepository(
    var result: Result<RouteInfo>
) : RouteInfoRepository {

    var requestedRouteId: String? = null

    override suspend fun loadRouteInfo(routeId: String): Result<RouteInfo> {
        requestedRouteId = routeId
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RouteInfoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The VM reads its route id from SavedStateHandle (the host normalizes the data URI into it).
    private fun handle(routeId: String = "1_8") =
        SavedStateHandle(mapOf(NavRoutes.ARG_ROUTE_ID to routeId))

    private val route = RouteInfo(
        id = "1_8",
        shortName = "8",
        longName = "Seattle Center - Rainier Beach",
        agencyName = "Metro Transit",
        url = null,
        directions = listOf(
            RouteDirection("Southbound", listOf(RouteStopItem("1", "Stop A", "S", 47.6, -122.3)))
        )
    )

    @Test
    fun `initial state is Loading before the load completes`() = runTest {
        val viewModel = RouteInfoViewModel(handle(), FakeRouteInfoRepository(Result.success(route)))

        assertEquals(RouteInfoUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `load emits Success with the route and passes the route id through`() = runTest {
        val repository = FakeRouteInfoRepository(Result.success(route))
        val viewModel = RouteInfoViewModel(handle(), repository)

        advanceUntilIdle()

        assertEquals(RouteInfoUiState.Success(route), viewModel.state.value)
        assertEquals("1_8", repository.requestedRouteId)
    }

    @Test
    fun `load emits Error carrying the failure message`() = runTest {
        val viewModel = RouteInfoViewModel(
            handle(),
            FakeRouteInfoRepository(Result.failure(IOException("No network")))
        )

        advanceUntilIdle()

        assertEquals(RouteInfoUiState.Error("No network"), viewModel.state.value)
    }

    @Test
    fun `retry after a failure goes through Loading and recovers`() = runTest {
        val repository = FakeRouteInfoRepository(Result.failure(IOException("No network")))
        val viewModel = RouteInfoViewModel(handle(), repository)
        advanceUntilIdle()
        assertEquals(RouteInfoUiState.Error("No network"), viewModel.state.value)

        repository.result = Result.success(route)
        viewModel.load()

        assertEquals(RouteInfoUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(RouteInfoUiState.Success(route), viewModel.state.value)
    }
}
