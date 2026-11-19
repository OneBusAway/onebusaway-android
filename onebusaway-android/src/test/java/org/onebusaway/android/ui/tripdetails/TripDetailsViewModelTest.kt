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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.RateLimitedException
import org.onebusaway.android.api.net.ObaRateLimit
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.TripMapReveal
import retrofit2.HttpException
import retrofit2.Response

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

    override fun destinationStops(position: Int): DestinationReminderStops? = null

    override fun lastLoadedTime(): Long? = null
}

// The view model under test, with no rate limit unless a test sets one.
private fun tripDetailsViewModel(savedState: SavedStateHandle, repository: TripDetailsRepository, rateLimit: ObaRateLimit = ObaRateLimit { null }) = TripDetailsViewModel(savedState, repository, rateLimit)

@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The VM reads its launch args from SavedStateHandle (seeded from intent extras in production).
    private fun handle(tripId: String = "t") = SavedStateHandle(mapOf(NavRoutes.ARG_TRIP_ID to tripId))

    private fun data() = TripDetailsData(
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
        lineColorArgb = 0,
        mapRequest = TripMapReveal("t", "route", "8", "Capitol Hill", 1, "stop", true)
    )

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = tripDetailsViewModel(handle(), FakeTripDetailsRepository(Result.success(data())))

        assertEquals(TripDetailsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `refresh emits Content on success`() = runTest {
        val viewModel = tripDetailsViewModel(handle(), FakeTripDetailsRepository(Result.success(data())))

        viewModel.refresh()

        val state = viewModel.state.value
        assertTrue(state is TripDetailsUiState.Content)
        assertEquals("Capitol Hill", (state as TripDetailsUiState.Content).header.headsign)
        assertEquals(data().mapRequest, state.mapRequest)
    }

    @Test
    fun `refresh emits Error when there is no content and the load fails`() = runTest {
        val viewModel = tripDetailsViewModel(
            handle(),
            FakeTripDetailsRepository(Result.failure(IOException("No network")))
        )

        viewModel.refresh()

        assertEquals(TripDetailsUiState.Error("No network"), viewModel.state.value)
    }

    @Test
    fun `a rate-limited first load stays Loading and retries when the limit lifts`() = runTest {
        val refused = IOException("Sorry, there was a communication error", RateLimitedException(5.seconds))
        val repository = FakeTripDetailsRepository(Result.failure(refused))
        val viewModel = tripDetailsViewModel(handle(), repository, rateLimit = { 5.seconds })

        viewModel.refresh()

        assertEquals(TripDetailsUiState.Loading, viewModel.state.value)
        assertEquals("retry when the limit lifts", 5.seconds, viewModel.earlyRetryAfter)

        repository.result = Result.success(data())
        viewModel.refresh()

        assertTrue(viewModel.state.value is TripDetailsUiState.Content)
        assertNull(viewModel.earlyRetryAfter)
    }

    @Test
    fun `a 429 that arrives after the cooldown ended retries right away`() = runTest {
        val refused = IOException("Sorry, there was a communication error", HttpException(Response.error<Unit>(429, "".toResponseBody())))
        val viewModel = tripDetailsViewModel(handle(), FakeTripDetailsRepository(Result.failure(refused)), rateLimit = { null })

        viewModel.refresh()

        assertEquals(TripDetailsUiState.Loading, viewModel.state.value)
        assertEquals(Duration.ZERO, viewModel.earlyRetryAfter)
    }

    @Test
    fun `a first load that fails without a rate limit shows Error and waits the full period`() = runTest {
        val viewModel = tripDetailsViewModel(handle(), FakeTripDetailsRepository(Result.failure(IOException("No network"))), rateLimit = { 5.seconds })

        viewModel.refresh()

        assertEquals(TripDetailsUiState.Error("No network"), viewModel.state.value)
        assertNull(viewModel.earlyRetryAfter)
    }

    @Test
    fun `a rate-limited refresh with content showing keeps it and waits the full period`() = runTest {
        val repository = FakeTripDetailsRepository(Result.success(data()))
        val viewModel = tripDetailsViewModel(handle(), repository, rateLimit = { 5.seconds })
        viewModel.refresh()

        repository.result = Result.failure(RateLimitedException(5.seconds))
        viewModel.refresh()

        assertTrue(viewModel.state.value is TripDetailsUiState.Content)
        assertNull(viewModel.earlyRetryAfter)
    }

    @Test
    fun `a failed refresh keeps existing content instead of showing Error`() = runTest {
        val repository = FakeTripDetailsRepository(Result.success(data()))
        val viewModel = tripDetailsViewModel(handle(), repository)
        viewModel.refresh()
        assertTrue(viewModel.state.value is TripDetailsUiState.Content)

        repository.result = Result.failure(IOException("blip"))
        viewModel.refresh()

        assertTrue(viewModel.state.value is TripDetailsUiState.Content)
    }

    @Test
    fun `setDestinationId reloads with the new destination id`() = runTest {
        val repository = FakeTripDetailsRepository(Result.success(data()))
        val viewModel = tripDetailsViewModel(handle(), repository)
        viewModel.refresh()

        viewModel.setDestinationId("1_99")
        advanceUntilIdle()

        assertEquals("1_99", repository.requestedDestinationIds.last())
    }
}
