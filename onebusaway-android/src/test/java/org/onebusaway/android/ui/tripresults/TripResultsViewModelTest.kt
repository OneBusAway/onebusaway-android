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
package org.onebusaway.android.ui.tripresults

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.opentripplanner.api.model.Itinerary

@OptIn(ExperimentalCoroutinesApi::class)
class TripResultsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val options = listOf(
        ItineraryOption("Route 8", "30 min", "3:00p - 3:30p"),
        ItineraryOption("Route 48", "40 min", "3:00p - 3:40p")
    )

    /** Treats itineraries as opaque tokens (as the ViewModel does) and returns canned projections. */
    private class FakeTripResultsRepository(
        var summary: Result<List<ItineraryOption>>,
        var directions: Result<List<DirectionItem>> = Result.success(emptyList())
    ) : TripResultsRepository {
        val directionsForCalls = mutableListOf<Itinerary>()
        override suspend fun summarize(itineraries: List<Itinerary>) = summary
        override suspend fun directionsFor(itinerary: Itinerary): Result<List<DirectionItem>> {
            directionsForCalls.add(itinerary)
            return directions
        }
    }

    private fun itineraries(count: Int): List<Itinerary> = List(count) { Itinerary() }

    @Test
    fun `initial state is Loading before itineraries are set`() = runTest {
        val viewModel = TripResultsViewModel(FakeTripResultsRepository(Result.success(options)))
        assertEquals(TripResultsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `setItineraries emits Success with the options and selected index`() = runTest {
        val viewModel = TripResultsViewModel(FakeTripResultsRepository(Result.success(options)))
        viewModel.setItineraries(itineraries(2), initialIndex = 1, showMap = false)
        advanceUntilIdle()
        val state = viewModel.state.value as TripResultsUiState.Success
        assertEquals(options, state.options)
        assertEquals(1, state.selectedIndex)
        assertFalse(state.showMap)
    }

    @Test
    fun `an out-of-range initial index is clamped`() = runTest {
        val viewModel = TripResultsViewModel(FakeTripResultsRepository(Result.success(options)))
        viewModel.setItineraries(itineraries(2), initialIndex = 9, showMap = false)
        advanceUntilIdle()
        assertEquals(1, (viewModel.state.value as TripResultsUiState.Success).selectedIndex)
    }

    @Test
    fun `selectOption updates the index and reloads that itinerary's directions`() = runTest {
        val repository = FakeTripResultsRepository(Result.success(options))
        val viewModel = TripResultsViewModel(repository)
        val list = itineraries(2)
        viewModel.setItineraries(list, initialIndex = 0, showMap = false)
        advanceUntilIdle()
        repository.directionsForCalls.clear()

        viewModel.selectOption(1)
        advanceUntilIdle()

        assertEquals(1, (viewModel.state.value as TripResultsUiState.Success).selectedIndex)
        assertEquals(listOf(list[1]), repository.directionsForCalls)
    }

    @Test
    fun `selectOption ignores the current index and out-of-range indices`() = runTest {
        val repository = FakeTripResultsRepository(Result.success(options))
        val viewModel = TripResultsViewModel(repository)
        viewModel.setItineraries(itineraries(2), initialIndex = 0, showMap = false)
        advanceUntilIdle()
        repository.directionsForCalls.clear()

        viewModel.selectOption(0) // unchanged
        viewModel.selectOption(5) // out of range
        advanceUntilIdle()

        assertTrue(repository.directionsForCalls.isEmpty())
    }

    @Test
    fun `selectOption emits the selected itinerary so the host can update the map`() = runTest {
        val repository = FakeTripResultsRepository(Result.success(options))
        val viewModel = TripResultsViewModel(repository)
        val list = itineraries(2)
        val emitted = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.selectedItinerary.collect { emitted.add(it.first) }
        }
        viewModel.setItineraries(list, initialIndex = 0, showMap = false)
        advanceUntilIdle()

        viewModel.selectOption(1)
        advanceUntilIdle()

        assertEquals(listOf(1), emitted)
    }

    @Test
    fun `toggleMap flips showMap on the Success state`() = runTest {
        val viewModel = TripResultsViewModel(FakeTripResultsRepository(Result.success(options)))
        viewModel.setItineraries(itineraries(1), initialIndex = 0, showMap = false)
        advanceUntilIdle()

        viewModel.toggleMap(true)
        assertTrue((viewModel.state.value as TripResultsUiState.Success).showMap)
    }

    @Test
    fun `a summarize failure surfaces Error with the message`() = runTest {
        val viewModel = TripResultsViewModel(
            FakeTripResultsRepository(Result.failure(IOException("boom")))
        )
        viewModel.setItineraries(itineraries(1), initialIndex = 0, showMap = false)
        advanceUntilIdle()

        assertEquals(TripResultsUiState.Error("boom"), viewModel.state.value)
    }
}
