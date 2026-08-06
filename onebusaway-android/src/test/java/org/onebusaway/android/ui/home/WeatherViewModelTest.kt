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
import kotlinx.coroutines.CompletableDeferred
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
 * A [WeatherRepository] whose fetches stay in flight until the test answers them, so a load can be left
 * outstanding across a region switch and then completed out of order.
 */
private class SuspendingWeatherRepository : WeatherRepository {
    val requestedRegions = mutableListOf<Long>()
    private val pending = mutableMapOf<Long, CompletableDeferred<WeatherData>>()

    override suspend fun currentForecast(regionId: Long): Result<WeatherData> {
        requestedRegions.add(regionId)
        return Result.success(pendingFor(regionId).await())
    }

    /** The sidecar for [regionId] answers, whether or not that region is still the current one. */
    fun answer(regionId: Long, data: WeatherData) {
        pendingFor(regionId).complete(data)
    }

    private fun pendingFor(regionId: Long) = pending.getOrPut(regionId) { CompletableDeferred() }
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
    private val staleForecast = WeatherData(icon = "rain", temperatureF = 41.0, summary = "Rain")

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

    @Test
    fun `a sidecar host change refetches even when the sidecar id is unchanged`() = runTest {
        val regions = FakeRegionRepository()
        val repo = FakeWeatherRepository(Result.success(forecast))
        val vm = WeatherViewModel(repo, regions, FakePreferencesRepository())

        regions.emit(region(1, sidecarBaseUrl = "https://a.example/"))
        advanceUntilIdle()
        // A deep-link-added region whose *own* sidecar knows it as 1, on a different host (#2165). Keyed
        // on the id alone this switch would look like no change and the chip would keep host a's forecast.
        regions.emit(region(2, sidecarBaseUrl = "https://b.example/", sidecarRegionId = 1))
        advanceUntilIdle()

        assertEquals(listOf(1L, 1L), repo.requestedRegions)
        assertEquals(forecast, vm.state.value.data)
    }

    @Test
    fun `a superseded fetch cannot land on the region that replaced it`() = runTest {
        val regions = FakeRegionRepository()
        val repo = SuspendingWeatherRepository()
        val vm = WeatherViewModel(repo, regions, FakePreferencesRepository())

        regions.emit(region(1))
        advanceUntilIdle()
        regions.emit(region(2)) // region 1's fetch is still outstanding
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), repo.requestedRegions)

        // Region 1's sidecar answers late, after the switch. Its forecast belongs to an endpoint that is
        // no longer current, so it must not reach the chip.
        repo.answer(1, staleForecast)
        advanceUntilIdle()
        assertNull(vm.state.value.data)

        repo.answer(2, forecast)
        advanceUntilIdle()
        assertEquals(forecast, vm.state.value.data)
    }

    @Test
    fun `switching regions clears the previous forecast while the new one loads`() = runTest {
        val regions = FakeRegionRepository()
        val repo = SuspendingWeatherRepository()
        val vm = WeatherViewModel(repo, regions, FakePreferencesRepository())

        regions.emit(region(1))
        repo.answer(1, forecast)
        advanceUntilIdle()
        assertEquals(forecast, vm.state.value.data)

        regions.emit(region(2))
        advanceUntilIdle()
        assertNull(vm.state.value.data)
    }

    @Test
    fun `a fetch failure clears the previous region's forecast rather than keeping it`() = runTest {
        val regions = FakeRegionRepository()
        val repo = FakeWeatherRepository(Result.success(forecast))
        val vm = WeatherViewModel(repo, regions, FakePreferencesRepository())

        regions.emit(region(1))
        advanceUntilIdle()
        assertEquals(forecast, vm.state.value.data)

        repo.result = Result.failure(IOException("boom"))
        regions.emit(region(2))
        advanceUntilIdle()
        assertNull(vm.state.value.data)
    }
}
