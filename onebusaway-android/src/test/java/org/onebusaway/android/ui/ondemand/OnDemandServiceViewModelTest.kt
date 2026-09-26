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
package org.onebusaway.android.ui.ondemand

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.LocationCheck
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandServiceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FakeDataSource(var result: OnDemandResult<OnDemandService>) : OnDemandDataSource {
        val requested = mutableListOf<String>()
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun service(id: String, geometryDetail: String): OnDemandResult<OnDemandService> {
            requested += id
            return result
        }
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    }

    private val service = OnDemandService("5088_77652", "5088", "5088_77652", "DOT Paratransit", OnDemandServiceKind.ZONE, agencyTimezone = "America/Los_Angeles")

    // Tuesday 2026-03-10 at 16:00 Pacific; tests move it on.
    private var nowMs = OffsetDateTime.parse("2026-03-10T16:00:00-07:00").toInstant().toEpochMilli()

    // A live service schedules a refresh against this fixed test clock (spec §3.6); left running,
    // runTest's own end-of-test drain would keep firing and rescheduling it forever. Every
    // constructed view model is tracked here so onDemandTest() can cancel it no matter how the test
    // body exits.
    private val createdViewModels = mutableListOf<OnDemandServiceViewModel>()

    /**
     * Runs a test body, cancelling every view model's scope afterward — success or failure — so a
     * live scheduled refresh can never leave runTest's own end-of-test drain chasing it forever
     * against this class's fixed clock. This has to happen *inside* the test coroutine: runTest
     * performs that drain before returning control to JUnit, so a `@After`/JUnit rule cancelling
     * only after the test method returns is too late to help a body that fails mid-test.
     */
    private fun onDemandTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            body()
        } finally {
            createdViewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun viewModel(source: OnDemandDataSource, presentDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) = OnDemandServiceViewModel(
        SavedStateHandle(mapOf(NavRoutes.ARG_ONDEMAND_SERVICE_ID to "5088_77652")),
        source,
        TimeProvider { nowMs },
        presentDispatcher
    ).also { createdViewModels += it }

    @Test
    fun `loads the service named by the nav arg`() = onDemandTest {
        val source = FakeDataSource(OnDemandResult.Loaded(service))
        val vm = viewModel(source)
        assertEquals(listOf("5088_77652"), source.requested)
        val content = vm.state.value as OnDemandServiceUiState.Content
        assertEquals("DOT Paratransit", content.service.name)
    }

    @Test
    fun `an OBA 404 is not found and a transport failure is an error`() = onDemandTest {
        assertEquals(OnDemandServiceUiState.NotFound, viewModel(FakeDataSource(OnDemandResult.Failed(ObaApiException(404)))).state.value)
        assertEquals(OnDemandServiceUiState.Error, viewModel(FakeDataSource(OnDemandResult.Failed(IOException("offline")))).state.value)
        assertEquals(OnDemandServiceUiState.Error, viewModel(FakeDataSource(OnDemandResult.Unsupported)).state.value)
    }

    @Test
    fun `retry re-requests`() = onDemandTest {
        val source = FakeDataSource(OnDemandResult.Failed(IOException("offline")))
        val vm = viewModel(source)
        source.result = OnDemandResult.Loaded(service)
        vm.retry()
        assertEquals(2, source.requested.size)
        assertTrue(vm.state.value is OnDemandServiceUiState.Content)
    }

    /** Each request waits on its own deferred, so a test decides when (and in which order) they land. */
    private class GatedDataSource : OnDemandDataSource {
        val pending = mutableListOf<CompletableDeferred<OnDemandResult<OnDemandService>>>()
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun service(id: String, geometryDetail: String): OnDemandResult<OnDemandService> = CompletableDeferred<OnDemandResult<OnDemandService>>().also { pending += it }.await()
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    }

    @Test
    fun `a slow earlier load cannot overwrite a retry`() = onDemandTest {
        val source = GatedDataSource()
        val vm = viewModel(source)
        vm.retry()
        source.pending[1].complete(OnDemandResult.Loaded(service))
        source.pending[0].complete(OnDemandResult.Failed(IOException("offline")))
        assertTrue(vm.state.value is OnDemandServiceUiState.Content)
    }

    @Test
    fun `the service is presented on the injected dispatcher, not the main thread`() = onDemandTest {
        val presentDispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(FakeDataSource(OnDemandResult.Loaded(service)), presentDispatcher)
        assertEquals(OnDemandServiceUiState.Loading, vm.state.value)
        presentDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is OnDemandServiceUiState.Content)
    }

    @Test
    fun `resuming re-presents the loaded service against a fresh clock without refetching`() = onDemandTest {
        // Every day, booked by 17:00 the day before.
        val daily = FlexCalendar("c", DayOfWeek.entries.toSet(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), emptySet())
        val dayBefore = BookingRule(
            "b", BookingType.PRIOR_DAYS, null, null, priorNoticeLastDay = 1, priorNoticeLastTime = ServiceDayTime.parse("17:00:00"),
            priorNoticeStartDay = null, priorNoticeStartTime = null, priorNoticeCalendarId = null,
            message = null, pickupMessage = null, dropOffMessage = null, phoneNumber = null, infoUrl = null, bookingUrl = null
        )
        val rule = AvailabilityRule(listOf("a"), listOf("a"), null, null, null, listOf("c"), 2, 2, "b", "b", null, null)
        val source = FakeDataSource(OnDemandResult.Loaded(service.copy(rules = listOf(rule), bookingRules = mapOf("b" to dayBefore), calendars = mapOf("c" to daily))))
        val vm = viewModel(source)
        assertEquals(LocalDate.of(2026, 3, 11), (vm.state.value as OnDemandServiceUiState.Content).booking?.travelDate)

        nowMs = OffsetDateTime.parse("2026-03-10T17:30:00-07:00").toInstant().toEpochMilli()
        vm.representNow()

        assertEquals(LocalDate.of(2026, 3, 12), (vm.state.value as OnDemandServiceUiState.Content).booking?.travelDate)
        assertEquals(1, source.requested.size)
    }

    @Test
    fun `resuming before anything has loaded does nothing`() = onDemandTest {
        val source = FakeDataSource(OnDemandResult.Failed(IOException("offline")))
        val vm = viewModel(source)
        vm.representNow()
        assertEquals(OnDemandServiceUiState.Error, vm.state.value)
        assertEquals(1, source.requested.size)
    }

    private fun viewModelWithArgs(source: OnDemandDataSource, args: Map<String, Any?>) = OnDemandServiceViewModel(
        SavedStateHandle(mapOf(NavRoutes.ARG_ONDEMAND_SERVICE_ID to "5088_77652") + args),
        source,
        TimeProvider { nowMs },
        UnconfinedTestDispatcher()
    ).also { createdViewModels += it }

    @Test
    fun `the route arguments become the location check`() = onDemandTest {
        val vm = viewModelWithArgs(
            FakeDataSource(OnDemandResult.Loaded(service)),
            mapOf(NavRoutes.ARG_ONDEMAND_INSIDE to "true", NavRoutes.ARG_ONDEMAND_SOURCE to "rider", NavRoutes.ARG_ONDEMAND_LOCALITY to "Boyne City", NavRoutes.ARG_ONDEMAND_LAT to "45.05", NavRoutes.ARG_ONDEMAND_LON to "-85.1")
        )
        val content = vm.state.value as OnDemandServiceUiState.Content
        assertEquals(LocationCheck(ProbeSource.Rider, true, "Boyne City", GeoPoint(45.05, -85.1)), content.locationCheck)
        assertEquals(null, (viewModel(FakeDataSource(OnDemandResult.Loaded(service))).state.value as OnDemandServiceUiState.Content).locationCheck)
    }

    @Test
    fun `the page re-presents itself a second after the next change`() = onDemandTest {
        val calendar = FlexCalendar("c", setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), emptySet())
        val running = service.copy(
            rules = listOf(AvailabilityRule(listOf("a"), listOf("a"), ServiceDayTime.parse("07:20:00"), ServiceDayTime.parse("16:40:00"), null, listOf("c"), 2, 2, null, null, null, null)),
            calendars = mapOf("c" to calendar)
        )
        nowMs = OffsetDateTime.parse("2026-03-10T16:39:30-07:00").toInstant().toEpochMilli()
        val vm = viewModel(FakeDataSource(OnDemandResult.Loaded(running)))
        assertTrue((vm.state.value as OnDemandServiceUiState.Content).availability.status is OnDemandStatus.OpenNow)

        nowMs = OffsetDateTime.parse("2026-03-10T16:40:01-07:00").toInstant().toEpochMilli()
        advanceTimeBy(32_000)
        assertTrue((vm.state.value as OnDemandServiceUiState.Content).availability.status is OnDemandStatus.OpensAt)
    }
}
