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
package org.onebusaway.android.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.nav.NavRoutes

private class FakeTripDetailsRepository(
    var result: Result<TripDetailsData>
) : TripDetailsRepository {

    val requestedDestinationIds = mutableListOf<String?>()

    override suspend fun getTripDetails(
        tripId: String,
        stopId: String?,
        scrollMode: String?,
        destinationId: String?
    ): Result<TripDetailsData> {
        requestedDestinationIds.add(destinationId)
        return result
    }

    override fun lastResponse(): ObaTripDetailsResponse? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The VM reads its launch args from SavedStateHandle (seeded from intent extras in production).
    private fun handle(tripId: String = "t") =
        SavedStateHandle(mapOf(NavRoutes.ARG_TRIP_ID to tripId))

    private fun data(routeId: String = "1_8") = TripDetailsData(
        header = TripHeader(
            routeShortName = "8",
            headsign = "Capitol Hill",
            tripShortName = null,
            agencyName = "Metro Transit",
            vehicleId = null,
            statusText = "Scheduled",
            statusColor = R.color.stop_info_scheduled_time,
            isRealtime = false
        ),
        stops = emptyList(),
        scrollToIndex = -1,
        routeId = routeId,
        lineColorArgb = 0
    )

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = TripDetailsViewModel(handle(), FakeTripDetailsRepository(Result.success(data())))

        assertEquals(TripDetailsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `refresh emits Content on success`() = runTest {
        val viewModel = TripDetailsViewModel(handle(), FakeTripDetailsRepository(Result.success(data())))

        viewModel.refresh()

        val state = viewModel.state.value
        assertTrue(state is TripDetailsUiState.Content)
        assertEquals("1_8", (state as TripDetailsUiState.Content).routeId)
    }

    @Test
    fun `refresh emits Error when there is no content and the load fails`() = runTest {
        val viewModel = TripDetailsViewModel(
            handle(), FakeTripDetailsRepository(Result.failure(IOException("No network")))
        )

        viewModel.refresh()

        assertEquals(TripDetailsUiState.Error("No network"), viewModel.state.value)
    }

    @Test
    fun `a failed refresh keeps existing content instead of showing Error`() = runTest {
        val repository = FakeTripDetailsRepository(Result.success(data()))
        val viewModel = TripDetailsViewModel(handle(), repository)
        viewModel.refresh()
        assertTrue(viewModel.state.value is TripDetailsUiState.Content)

        repository.result = Result.failure(IOException("blip"))
        viewModel.refresh()

        assertTrue(viewModel.state.value is TripDetailsUiState.Content)
    }

    @Test
    fun `setDestinationId reloads with the new destination id`() = runTest {
        val repository = FakeTripDetailsRepository(Result.success(data()))
        val viewModel = TripDetailsViewModel(handle(), repository)
        viewModel.refresh()

        viewModel.setDestinationId("1_99")
        advanceUntilIdle()

        assertEquals("1_99", repository.requestedDestinationIds.last())
    }
}
