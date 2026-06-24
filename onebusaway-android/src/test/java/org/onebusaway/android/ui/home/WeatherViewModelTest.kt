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
package org.onebusaway.android.ui.home

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.home.weather.WeatherData
import org.onebusaway.android.ui.home.weather.WeatherRepository
import org.onebusaway.android.ui.home.weather.WeatherViewModel

private class FakeWeatherRepository(var result: Result<WeatherData>) : WeatherRepository {
    val requestedRegions = mutableListOf<Long>()
    override suspend fun currentForecast(regionId: Long): Result<WeatherData> {
        requestedRegions.add(regionId)
        return result
    }
}

/**
 * Unit tests for [WeatherViewModel]'s region-keyed forecast fetch (migrated from HomeViewModelTest when
 * weather became its own feature module). The hide-weather pref + the NEARBY-tab gate are Application /
 * Compose concerns, verified by equivalence rather than here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val forecast = WeatherData(icon = "clear-day", temperatureF = 70.0, summary = "Clear")

    @Test
    fun `forecast loads when a region is set`() = runTest {
        val regions = FakeRegionRepository()
        val vm = WeatherViewModel(FakeWeatherRepository(Result.success(forecast)), regions, FakePreferencesRepository())
        regions.emit(region(1))
        advanceUntilIdle()
        assertEquals(forecast, vm.state.value.data)
    }

    @Test
    fun `clearing the region clears the forecast`() = runTest {
        val regions = FakeRegionRepository(region(1))
        val vm = WeatherViewModel(FakeWeatherRepository(Result.success(forecast)), regions, FakePreferencesRepository())
        advanceUntilIdle()
        assertEquals(forecast, vm.state.value.data)
        regions.emit(null)
        advanceUntilIdle()
        assertNull(vm.state.value.data)
    }

    @Test
    fun `a fetch failure leaves the forecast null`() = runTest {
        val regions = FakeRegionRepository(region(1))
        val vm = WeatherViewModel(FakeWeatherRepository(Result.failure(IOException("boom"))), regions, FakePreferencesRepository())
        advanceUntilIdle()
        assertNull(vm.state.value.data)
    }

    @Test
    fun `the forecast is fetched once per region id, again when the region changes`() = runTest {
        val regions = FakeRegionRepository()
        val repo = FakeWeatherRepository(Result.success(forecast))
        val vm = WeatherViewModel(repo, regions, FakePreferencesRepository())

        regions.emit(region(1))
        advanceUntilIdle()
        regions.emit(region(1)) // same id: no refetch
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.requestedRegions)

        regions.emit(region(2)) // new id: refetch
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), repo.requestedRegions)
    }
}
