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
package org.onebusaway.android.ui.tripplan

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.onebusaway.android.location.FakeLocationRepository
import org.onebusaway.android.location.SearchCenter
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.TimeProvider

/** What OTP calls a trip's origin when it was asked to route from a bare coordinate. */
private const val OTP_PLACEHOLDER_ORIGIN = "Origin"

@OptIn(ExperimentalCoroutinesApi::class)
class TripPlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = AdvancedSettings(modeId = 4, maxWalkMeters = 1600.0, optimizeTransfers = true, wheelchair = false)
    private val origin = TripEndpoint.Geocoded("Origin", lat = 47.6, lon = -122.3)
    private val destination = TripEndpoint.Geocoded("Destination", lat = 47.7, lon = -122.2)

    /** A two-leg itinerary named the way OTP names one planned between bare coordinates. */
    private val plannedTrip = listOf(
        TripItinerary(
            legs = listOf(
                TripLeg(from = TripPlace(name = OTP_PLACEHOLDER_ORIGIN), to = TripPlace(name = "Pine St & 3rd Ave")),
                TripLeg(from = TripPlace(name = "Pine St & 3rd Ave"), to = TripPlace(name = "Destination"))
            )
        )
    )

    private class FakeGeocodeRepository(
        var result: Result<List<TripEndpoint.Geocoded>>,
        var reverseResult: Result<String?> = Result.success(null)
    ) : GeocodeRepository {
        var lastQuery: String? = null
        val reverseCalls = mutableListOf<Pair<Double, Double>>()

        override suspend fun suggest(query: String): Result<List<TripEndpoint.Geocoded>> {
            lastQuery = query
            return result
        }

        override suspend fun reverse(lat: Double, lon: Double): Result<String?> {
            reverseCalls.add(lat to lon)
            return reverseResult
        }
    }

    /** A geocoder whose reverse lookup never answers, to exercise the plan's naming timeout. */
    private class StalledGeocodeRepository : GeocodeRepository {
        override suspend fun suggest(query: String) = Result.success(emptyList<TripEndpoint.Geocoded>())
        override suspend fun reverse(lat: Double, lon: Double): Result<String?> = CompletableDeferred<Result<String?>>().await()
    }

    private class FakeTripPlanRepository(var result: Result<List<TripItinerary>>) : TripPlanRepository {
        var calls = 0
        override suspend fun plan(params: TripPlanParams): Result<List<TripItinerary>> {
            calls++
            return result
        }

        override fun planBlocking(builder: TripRequestBuilder): List<TripItinerary> = result.getOrDefault(emptyList())
    }

    /** A plan repository whose call suspends until [gate] is completed, to exercise the in-flight race. */
    private class GatedTripPlanRepository : TripPlanRepository {
        val gate = CompletableDeferred<Result<List<TripItinerary>>>()
        var calls = 0
        override suspend fun plan(params: TripPlanParams): Result<List<TripItinerary>> {
            calls++
            return gate.await()
        }

        override fun planBlocking(builder: TripRequestBuilder): List<TripItinerary> = emptyList()
    }

    private inner class FakeAdvancedSettingsRepository : AdvancedSettingsRepository {
        override fun load() = settings
    }

    private fun viewModel(
        geocode: GeocodeRepository = FakeGeocodeRepository(Result.success(emptyList())),
        plan: TripPlanRepository = FakeTripPlanRepository(Result.success(listOf(TripItinerary()))),
        region: RegionRepository = FakeRegionRepository()
    ) = TripPlanViewModel(
        geocode,
        plan,
        region,
        SearchCenter(FakeLocationRepository(), region),
        TimeProvider { 0L },
        FakeAdvancedSettingsRepository()
    )

    /** Sets both resolved endpoints (which auto-submits a plan once both have coordinates). */
    private fun setBothEndpoints(vm: TripPlanViewModel) {
        vm.setFrom(origin)
        vm.setTo(destination)
    }

    @Test
    fun `initial state carries the injected settings and cannot submit`() = runTest {
        val vm = viewModel()
        val state = vm.formState.value
        assertEquals(4, state.modeId)
        assertEquals(1600.0, state.maxWalkMeters)
        assertTrue(state.optimizeTransfers)
        assertFalse(state.canSubmit)
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `a query change populates suggestions after the debounce`() = runTest {
        val geocode = FakeGeocodeRepository(Result.success(listOf(origin, destination)))
        val vm = viewModel(geocode = geocode)
        vm.onFromQueryChange("down")
        advanceUntilIdle()
        assertEquals("down", geocode.lastQuery)
        assertEquals(listOf(origin, destination), vm.formState.value.fromSuggestions)
    }

    @Test
    fun `a query change makes the origin a FreeText endpoint`() = runTest {
        val vm = viewModel()
        vm.onFromQueryChange("downtown")
        assertEquals(TripEndpoint.FreeText("downtown"), vm.formState.value.from)
        assertFalse(vm.formState.value.canSubmit)
    }

    @Test
    fun `selecting a geocoded suggestion stores the resolved endpoint`() = runTest {
        val vm = viewModel()
        vm.onFromQueryChange("orig")
        advanceUntilIdle()
        vm.setFrom(origin)
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(origin, state.from)
        assertTrue(state.fromSuggestions.isEmpty())
    }

    @Test
    fun `clearFrom resets the origin to empty FreeText and does not submit`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertEquals(1, plan.calls)

        vm.clearFrom()
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(TripEndpoint.FreeText(), state.from)
        assertFalse(state.canSubmit)
        assertEquals(1, plan.calls) // clearing must not re-plan
    }

    @Test
    fun `setting both endpoints with coordinates auto-submits the plan`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)

        vm.setFrom(origin)
        advanceUntilIdle()
        assertEquals(0, plan.calls) // destination still missing

        vm.setTo(destination)
        advanceUntilIdle()
        assertTrue(vm.formState.value.canSubmit)
        assertEquals(1, plan.calls)
        assertTrue(vm.planState.value is PlanResult.Success)
    }

    @Test
    fun `clearing an endpoint after a successful plan drops the stale result`() = runTest {
        val vm = viewModel()
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertTrue(vm.planState.value is PlanResult.Success)

        vm.clearTo()
        advanceUntilIdle()
        assertFalse(vm.formState.value.canSubmit)
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `typing over a resolved endpoint drops the stale result`() = runTest {
        val vm = viewModel()
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertTrue(vm.planState.value is PlanResult.Success)

        vm.onFromQueryChange("typing over the pill")
        advanceUntilIdle()
        assertFalse(vm.formState.value.canSubmit)
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `a plan finishing after the form is cleared cannot surface a stale route`() = runTest {
        val plan = GatedTripPlanRepository()
        val vm = viewModel(plan = plan)
        setBothEndpoints(vm) // canSubmit -> a plan launches and suspends on the gate
        advanceUntilIdle()
        assertEquals(PlanResult.Loading, vm.planState.value)

        vm.clearTo() // form no longer submittable: invalidates the in-flight plan
        advanceUntilIdle()
        assertEquals(PlanResult.Idle, vm.planState.value)

        plan.gate.complete(Result.success(listOf(TripItinerary()))) // late completion of the stale plan
        advanceUntilIdle()
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `changing one endpoint while the other is unset does not surface a route`() = runTest {
        val vm = viewModel()
        // Seed a stale success, then reset both endpoints to empty as a fresh start.
        setBothEndpoints(vm)
        advanceUntilIdle()
        vm.clearFrom()
        vm.clearTo()
        advanceUntilIdle()

        // Selecting just one endpoint must not bring back the old route.
        vm.setFrom(origin)
        advanceUntilIdle()
        assertFalse(vm.formState.value.canSubmit)
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `an endpoint without coordinates does not enable submit`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)

        vm.setFrom(TripEndpoint.AddressBook("Contact A", lat = null, lon = null))
        vm.setTo(TripEndpoint.AddressBook("Contact B", lat = null, lon = null))
        advanceUntilIdle()

        assertFalse(vm.formState.value.canSubmit)
        assertEquals(0, plan.calls)
    }

    @Test
    fun `reverseTrip swaps origin and destination`() = runTest {
        val vm = viewModel()
        setBothEndpoints(vm)
        advanceUntilIdle()

        vm.reverseTrip()
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(destination, state.from)
        assertEquals(origin, state.to)
    }

    @Test
    fun `applyAdvancedSettings updates the form`() = runTest {
        val vm = viewModel()
        val updated = AdvancedSettings(modeId = 1, maxWalkMeters = null, optimizeTransfers = false, wheelchair = true)
        vm.applyAdvancedSettings(updated)
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(1, state.modeId)
        assertTrue(state.wheelchair)
        assertFalse(state.optimizeTransfers)
        assertEquals(null, state.maxWalkMeters)
    }

    @Test
    fun `a classified plan failure surfaces its TripPlanError`() = runTest {
        val error = TripPlanError(TripPlanError.Category.SCHEDULE, R.string.tripplanner_error_no_transit_times)
        val vm = viewModel(plan = FakeTripPlanRepository(Result.failure(TripPlanException(error))))
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertEquals(PlanResult.Error(error), vm.planState.value)
    }

    @Test
    fun `an unclassified plan failure falls back to Unknown`() = runTest {
        val vm = viewModel(plan = FakeTripPlanRepository(Result.failure(IOException("boom"))))
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertEquals(PlanResult.Error(TripPlanError.Unknown), vm.planState.value)
    }

    /** The name the surfaced result gives the trip's origin — the first leg's origin. */
    private fun plannedOriginName(vm: TripPlanViewModel): String? = (vm.planState.value as PlanResult.Success).itineraries.first().legs.first().from.name

    @Test
    fun `a coordinate-only endpoint is reverse-geocoded onto the itineraries`() = runTest {
        val geocode = FakeGeocodeRepository(
            Result.success(emptyList()),
            reverseResult = Result.success("Pike Place Market")
        )
        val vm = viewModel(geocode = geocode, plan = FakeTripPlanRepository(Result.success(plannedTrip)))

        vm.setFrom(TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setTo(TripEndpoint.MapPoint(lat = 47.7, lon = -122.2))
        advanceUntilIdle()

        val legs = (vm.planState.value as PlanResult.Success).itineraries.first().legs
        assertEquals("Pike Place Market", legs.first().from.name)
        assertEquals("Pike Place Market", legs.last().to.name)
        assertEquals(listOf(47.6 to -122.3, 47.7 to -122.2), geocode.reverseCalls.sortedBy { it.first })
        // The form's own pill keeps its fixed "My Location" label.
        assertEquals(null, vm.formState.value.from.displayText)
    }

    @Test
    fun `an endpoint the user named labels the trip without a lookup`() = runTest {
        val geocode = FakeGeocodeRepository(
            Result.success(emptyList()),
            reverseResult = Result.success("Somewhere Else")
        )
        val vm = viewModel(geocode = geocode, plan = FakeTripPlanRepository(Result.success(plannedTrip)))
        setBothEndpoints(vm)
        advanceUntilIdle()

        assertEquals("Origin", plannedOriginName(vm))
        assertTrue(geocode.reverseCalls.isEmpty())
    }

    @Test
    fun `a failed reverse lookup keeps OTP's own name and still plans`() = runTest {
        val geocode = FakeGeocodeRepository(
            Result.success(emptyList()),
            reverseResult = Result.failure(IOException("boom"))
        )
        val vm = viewModel(geocode = geocode, plan = FakeTripPlanRepository(Result.success(plannedTrip)))

        vm.setFrom(TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setTo(destination)
        advanceUntilIdle()

        assertEquals(OTP_PLACEHOLDER_ORIGIN, plannedOriginName(vm))
    }

    @Test
    fun `a stalled reverse lookup does not hold the route back`() = runTest {
        val vm = viewModel(
            geocode = StalledGeocodeRepository(),
            plan = FakeTripPlanRepository(Result.success(plannedTrip))
        )

        vm.setFrom(TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setTo(destination)
        advanceUntilIdle()

        assertTrue(vm.planState.value is PlanResult.Success)
        assertEquals(OTP_PLACEHOLDER_ORIGIN, plannedOriginName(vm))
    }

    @Test
    fun `a re-plan reuses the name already looked up for the same point`() = runTest {
        val geocode = FakeGeocodeRepository(
            Result.success(emptyList()),
            reverseResult = Result.success("Pike Place Market")
        )
        val vm = viewModel(geocode = geocode, plan = FakeTripPlanRepository(Result.success(plannedTrip)))
        vm.setFrom(TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setTo(destination)
        advanceUntilIdle()
        assertEquals(1, geocode.reverseCalls.size)

        // Nudging the time re-plans the same two points; the geocoder must not be asked again.
        vm.setDateTime(90_000L)
        vm.setArriving(true)
        advanceUntilIdle()

        assertEquals(1, geocode.reverseCalls.size)
        assertEquals("Pike Place Market", plannedOriginName(vm))
    }

    @Test
    fun `a plan failure surfaces without waiting on the naming lookup`() = runTest {
        val vm = viewModel(
            geocode = StalledGeocodeRepository(),
            plan = FakeTripPlanRepository(Result.failure(IOException("boom")))
        )

        vm.setFrom(TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setTo(destination)
        // No advanceUntilIdle: only the plan is allowed to complete. A naming lookup still in flight must
        // not hold the error behind its timeout.
        runCurrent()

        assertEquals(PlanResult.Error(TripPlanError.Unknown), vm.planState.value)
    }

    @Test
    fun `otpContactEmail reflects the active region's OTP contact`() = runTest {
        val regionRepo = FakeRegionRepository(region(id = 1, otpContactEmail = "otp@example.com"))
        val vm = viewModel(region = regionRepo)
        assertEquals("otp@example.com", vm.otpContactEmail)

        // A region change (or clearing) is reflected on the next read — it isn't cached in the VM.
        regionRepo.emit(region(id = 2, otpContactEmail = null))
        assertEquals(null, vm.otpContactEmail)
    }

    @Test
    fun `setDateTime refreshes the date and time labels`() = runTest {
        val vm = viewModel()
        vm.setDateTime(1_700_000_000_000L)
        val state = vm.formState.value
        assertTrue(state.dateLabel.isNotBlank())
        assertTrue(state.timeLabel.isNotBlank())
        assertEquals(1_700_000_000_000L, state.dateTimeMillis)
    }
}
