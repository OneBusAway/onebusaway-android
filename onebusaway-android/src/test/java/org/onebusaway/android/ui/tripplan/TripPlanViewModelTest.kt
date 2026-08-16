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
import java.time.LocalDate
import java.time.ZoneId
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

    private val settings = AdvancedSettings(modes = TripModeSelection(), maxWalkMeters = 1600.0, optimizeTransfers = true, wheelchair = false)
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

    /** A geocoder whose forward lookup answers only when the test completes [pending]. */
    private class DeferredGeocodeRepository(
        private val pending: CompletableDeferred<Result<List<TripEndpoint.Geocoded>>>
    ) : GeocodeRepository {
        override suspend fun suggest(query: String): Result<List<TripEndpoint.Geocoded>> = pending.await()

        override suspend fun reverse(lat: Double, lon: Double): Result<String?> = Result.success(null)
    }

    /** A geocoder whose reverse lookup never answers, to exercise the plan's naming timeout. */
    private class StalledGeocodeRepository : GeocodeRepository {
        override suspend fun suggest(query: String) = Result.success(emptyList<TripEndpoint.Geocoded>())
        override suspend fun reverse(lat: Double, lon: Double): Result<String?> = CompletableDeferred<Result<String?>>().await()
    }

    private class FakeTripPlanRepository(var result: Result<List<TripItinerary>>) : TripPlanRepository {
        var calls = 0
        var lastParams: TripPlanParams? = null
        override suspend fun plan(params: TripPlanParams): Result<List<TripItinerary>> {
            calls++
            lastParams = params
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

    /** A clock the test can advance, to tell "now at construction" apart from "now at submit". */
    private class FakeClock(var nowMillis: Long = 0L) : TimeProvider {
        override fun now(): Long = nowMillis
    }

    /**
     * A clock that moves *between reads*, walking [readings] and then holding the last one. Where
     * [FakeClock] holds still — and so cannot tell one read apart from two — this makes the number of
     * reads a helper takes observable.
     */
    private class TickingClock(var readings: List<Long>) : TimeProvider {
        var index = 0

        override fun now(): Long = readings[minOf(index++, readings.lastIndex)]
    }

    private fun viewModel(
        geocode: GeocodeRepository = FakeGeocodeRepository(Result.success(emptyList())),
        plan: TripPlanRepository = FakeTripPlanRepository(Result.success(listOf(TripItinerary()))),
        region: RegionRepository = FakeRegionRepository(),
        clock: TimeProvider = TimeProvider { 0L }
    ): TripPlanViewModel {
        // The fake reports "no fix" — see TripPlanFormStateTest for the with-a-fix cases.
        val location = FakeLocationRepository()
        return TripPlanViewModel(
            geocode,
            plan,
            region,
            SearchCenter(location, region),
            location,
            clock,
            FakeAdvancedSettingsRepository()
        )
    }

    /**
     * A wall-clock instant on [date], in the device's own zone — the zone the form reasons about days
     * in, so a fixed epoch millis would place these cases on a different date depending on where the
     * test runs.
     */
    private fun millisOn(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Sets both resolved endpoints (which auto-submits a plan once both have coordinates). */
    private fun setBothEndpoints(vm: TripPlanViewModel) {
        vm.setEndpoint(TripEndpointSlot.FROM, origin)
        vm.setEndpoint(TripEndpointSlot.TO, destination)
    }

    @Test
    fun `initial state carries the injected settings and cannot submit`() = runTest {
        val vm = viewModel()
        val state = vm.formState.value
        assertEquals(TripModeSelection(), state.modes)
        assertEquals(1600.0, state.maxWalkMeters)
        assertTrue(state.optimizeTransfers)
        assertFalse(state.canSubmit)
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `a query change populates suggestions after the debounce`() = runTest {
        val geocode = FakeGeocodeRepository(Result.success(listOf(origin, destination)))
        val vm = viewModel(geocode = geocode)
        vm.onQueryChange(TripEndpointSlot.FROM, "down")
        advanceUntilIdle()
        assertEquals("down", geocode.lastQuery)
        assertEquals(listOf(origin, destination), vm.formState.value.fromSuggestions)
    }

    @Test
    fun `a query change makes the origin a FreeText endpoint`() = runTest {
        val vm = viewModel()
        vm.onQueryChange(TripEndpointSlot.FROM, "downtown")
        assertEquals(TripEndpoint.FreeText("downtown"), vm.formState.value.from)
        assertFalse(vm.formState.value.canSubmit)
    }

    @Test
    fun `selecting a geocoded suggestion stores the resolved endpoint`() = runTest {
        val vm = viewModel()
        vm.onQueryChange(TripEndpointSlot.FROM, "orig")
        advanceUntilIdle()
        vm.setEndpoint(TripEndpointSlot.FROM, origin)
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(origin, state.from)
        assertTrue(state.fromSuggestions.isEmpty())
    }

    @Test
    fun `clearing the origin resets it to empty FreeText and does not submit`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertEquals(1, plan.calls)

        vm.clearEndpoint(TripEndpointSlot.FROM)
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

        vm.setEndpoint(TripEndpointSlot.FROM, origin)
        advanceUntilIdle()
        assertEquals(0, plan.calls) // destination still missing

        vm.setEndpoint(TripEndpointSlot.TO, destination)
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

        vm.clearEndpoint(TripEndpointSlot.TO)
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

        vm.onQueryChange(TripEndpointSlot.FROM, "typing over the pill")
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

        vm.clearEndpoint(TripEndpointSlot.TO) // form no longer submittable: invalidates the in-flight plan
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
        vm.clearEndpoint(TripEndpointSlot.FROM)
        vm.clearEndpoint(TripEndpointSlot.TO)
        advanceUntilIdle()

        // Selecting just one endpoint must not bring back the old route.
        vm.setEndpoint(TripEndpointSlot.FROM, origin)
        advanceUntilIdle()
        assertFalse(vm.formState.value.canSubmit)
        assertEquals(PlanResult.Idle, vm.planState.value)
    }

    @Test
    fun `an endpoint without coordinates does not enable submit`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)

        vm.setEndpoint(TripEndpointSlot.FROM, TripEndpoint.AddressBook("Contact A", lat = null, lon = null))
        vm.setEndpoint(TripEndpointSlot.TO, TripEndpoint.AddressBook("Contact B", lat = null, lon = null))
        advanceUntilIdle()

        assertFalse(vm.formState.value.canSubmit)
        assertEquals(0, plan.calls)
    }

    @Test
    fun `a long-pressed endpoint with no location fix leaves the other end empty`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)

        vm.setEndpointPaired(TripEndpointSlot.TO, TripEndpoint.MapPoint(lat = 47.7, lon = -122.2))
        advanceUntilIdle()

        val state = vm.formState.value
        assertEquals(TripEndpoint.MapPoint(lat = 47.7, lon = -122.2), state.to)
        assertEquals(TripEndpoint.FreeText(), state.from)
        assertFalse(state.canSubmit)
        assertEquals(0, plan.calls)
    }

    // Not a test of the don't-overwrite rule — with no fix the pairing branch is never reached at all.
    // That rule is TripPlanFormStateTest's; this pins the one submission the completed form makes.
    @Test
    fun `a long-pressed endpoint completing the pair plans exactly once`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)
        vm.setEndpoint(TripEndpointSlot.FROM, origin)
        advanceUntilIdle()

        vm.setEndpointPaired(TripEndpointSlot.TO, TripEndpoint.MapPoint(lat = 47.7, lon = -122.2))
        advanceUntilIdle()

        assertTrue(vm.formState.value.canSubmit)
        assertEquals(1, plan.calls)
    }

    // --- a place another app named as text (#1936) ---------------------------------------------------

    @Test
    fun `a place named as text resolves to the geocoder's top match and plans`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val geocode = FakeGeocodeRepository(Result.success(listOf(destination, origin)))
        val vm = viewModel(geocode = geocode, plan = plan)
        vm.setEndpoint(TripEndpointSlot.FROM, origin)
        advanceUntilIdle()

        vm.setEndpointFromQuery(TripEndpointSlot.TO, "400 Broad St")
        advanceUntilIdle()

        assertEquals("400 Broad St", geocode.lastQuery)
        assertEquals(destination, vm.formState.value.to)
        assertEquals(1, plan.calls)
    }

    /** The text is on screen before the geocoder answers, so the form says what the app was opened for. */
    @Test
    fun `a place named as text shows its text while the lookup is out`() = runTest {
        val pending = CompletableDeferred<Result<List<TripEndpoint.Geocoded>>>()
        val vm = viewModel(geocode = DeferredGeocodeRepository(pending))

        vm.setEndpointFromQuery(TripEndpointSlot.TO, "400 Broad St")
        runCurrent()

        assertEquals(TripEndpoint.FreeText("400 Broad St"), vm.formState.value.to)

        pending.complete(Result.success(listOf(destination)))
        advanceUntilIdle()
        assertEquals(destination, vm.formState.value.to)
    }

    /** A rider who types over the field while the lookup is out keeps what they typed. */
    @Test
    fun `an edit during the lookup is not overwritten by its result`() = runTest {
        val pending = CompletableDeferred<Result<List<TripEndpoint.Geocoded>>>()
        val vm = viewModel(geocode = DeferredGeocodeRepository(pending))

        vm.setEndpointFromQuery(TripEndpointSlot.TO, "400 Broad St")
        runCurrent()
        vm.onQueryChange(TripEndpointSlot.TO, "Space Needle")
        pending.complete(Result.success(listOf(destination)))
        advanceUntilIdle()

        assertEquals(TripEndpoint.FreeText("Space Needle"), vm.formState.value.to)
    }

    @Test
    fun `a place named as text that resolves to nothing is left in the field for the rider`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(geocode = FakeGeocodeRepository(Result.success(emptyList())), plan = plan)

        vm.setEndpointFromQuery(TripEndpointSlot.TO, "nowhere at all")
        advanceUntilIdle()

        assertEquals(TripEndpoint.FreeText("nowhere at all"), vm.formState.value.to)
        assertEquals(0, plan.calls)
    }

    @Test
    fun `a failed lookup leaves the text in the field rather than dropping it`() = runTest {
        val vm = viewModel(geocode = FakeGeocodeRepository(Result.failure(IOException("offline"))))

        vm.setEndpointFromQuery(TripEndpointSlot.TO, "400 Broad St")
        advanceUntilIdle()

        assertEquals(TripEndpoint.FreeText("400 Broad St"), vm.formState.value.to)
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
        val updated = AdvancedSettings(modes = TripModeSelection(VehicleMode.BUS, StreetMode.WALK), maxWalkMeters = null, optimizeTransfers = false, wheelchair = true)
        vm.applyAdvancedSettings(updated)
        advanceUntilIdle()
        val state = vm.formState.value
        assertEquals(TripModeSelection(VehicleMode.BUS, StreetMode.WALK), state.modes)
        assertTrue(state.wheelchair)
        assertFalse(state.optimizeTransfers)
        assertEquals(null, state.maxWalkMeters)
    }

    @Test
    fun `setModeSelection updates the form and replans`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertEquals(1, plan.calls)
        val selected = TripModeSelection(VehicleMode.RAIL, StreetMode.BICYCLE)

        vm.setModeSelection(selected)
        advanceUntilIdle()

        assertEquals(selected, vm.formState.value.modes)
        // The modes are part of the request, so a completed form must ask again with the new ones.
        assertEquals(2, plan.calls)
        assertEquals(selected, (vm.planState.value as PlanResult.Success).params?.modes)
    }

    /** The menu reports a tap on the already-checked item; an identical request is not worth re-issuing. */
    @Test
    fun `re-picking the current mode does not replan`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertEquals(1, plan.calls)

        vm.setModeSelection(vm.formState.value.modes)
        advanceUntilIdle()

        assertEquals(1, plan.calls)
    }

    @Test
    fun `applyAdvancedSettings carries the street preferences into the plan request`() = runTest {
        val vm = viewModel()
        setBothEndpoints(vm)
        vm.applyAdvancedSettings(
            AdvancedSettings(
                modes = TripModeSelection(VehicleMode.ALL_TRANSIT, StreetMode.WALK_AND_BIKESHARE),
                maxWalkMeters = null,
                optimizeTransfers = false,
                wheelchair = false,
                walkPreference = WalkPreference.MINIMUM,
                cyclingPreference = CyclingPreference.FLATTEST,
                bikePreference = BikePreference.MAXIMUM
            )
        )
        advanceUntilIdle()
        // They must reach TripPlanParams, not just the form: the params are what builds the request
        // and what the trip-plan-change monitor re-plans with.
        val params = (vm.planState.value as PlanResult.Success).params
        assertEquals(WalkPreference.MINIMUM, params?.walkPreference)
        assertEquals(CyclingPreference.FLATTEST, params?.cyclingPreference)
        assertEquals(BikePreference.MAXIMUM, params?.bikePreference)
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

        vm.setEndpoint(TripEndpointSlot.FROM, TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setEndpoint(TripEndpointSlot.TO, TripEndpoint.MapPoint(lat = 47.7, lon = -122.2))
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

        vm.setEndpoint(TripEndpointSlot.FROM, TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setEndpoint(TripEndpointSlot.TO, destination)
        advanceUntilIdle()

        assertEquals(OTP_PLACEHOLDER_ORIGIN, plannedOriginName(vm))
    }

    @Test
    fun `a stalled reverse lookup does not hold the route back`() = runTest {
        val vm = viewModel(
            geocode = StalledGeocodeRepository(),
            plan = FakeTripPlanRepository(Result.success(plannedTrip))
        )

        vm.setEndpoint(TripEndpointSlot.FROM, TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setEndpoint(TripEndpointSlot.TO, destination)
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
        vm.setEndpoint(TripEndpointSlot.FROM, TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setEndpoint(TripEndpointSlot.TO, destination)
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

        vm.setEndpoint(TripEndpointSlot.FROM, TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3))
        vm.setEndpoint(TripEndpointSlot.TO, destination)
        // No advanceUntilIdle: only the plan is allowed to complete. A naming lookup still in flight must
        // not hold the error behind its timeout.
        runCurrent()

        assertEquals(PlanResult.Error(TripPlanError.Unknown), vm.planState.value)
    }

    @Test
    fun `a my-location endpoint survives a re-plan when there is no fresh fix`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)
        val setFrom = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3)
        vm.setEndpoint(TripEndpointSlot.FROM, setFrom)
        vm.setEndpoint(TripEndpointSlot.TO, destination)
        advanceUntilIdle()

        vm.setArriving(true)
        advanceUntilIdle()

        // The re-read at submit (#2134) finds nothing — the fake reports no fix — so the endpoint must
        // stay exactly as the rider set it, both in the form and in the request. Moving it to a *newer*
        // fix is TripPlanFormStateTest's job: a real one is an android.location.Location.
        assertEquals(2, plan.calls)
        assertEquals(setFrom, vm.formState.value.from)
        assertEquals(setFrom, plan.lastParams?.from)
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

    /**
     * A single-digit hour isn't padded — "6:44 PM", not "06:44 PM". Asserted as the absence of the
     * pad rather than against a literal, which would pin the runner's locale: the padding is the
     * pattern's doing (`h`, not `hh`) and so is locale-independent, but the digits aren't.
     */
    @Test
    fun `the time label does not zero-pad the hour`() = runTest {
        val vm = viewModel()

        vm.setDateTime(millisOn(LocalDate.of(2026, 6, 10), hour = 6))

        assertFalse(
            "expected an unpadded hour, but the label was ${vm.formState.value.timeLabel}",
            vm.formState.value.timeLabel.startsWith("0")
        )
    }

    @Test
    fun `a form starts anchored to now`() = runTest {
        assertTrue(viewModel().formState.value.departNow)
    }

    @Test
    fun `a depart-now plan reads the clock at submit, not at construction`() = runTest {
        val clock = FakeClock(0L)
        val vm = viewModel(clock = clock)
        // The rider leaves the form open for a while before both endpoints resolve.
        clock.nowMillis = 90_000L
        setBothEndpoints(vm)
        advanceUntilIdle()

        // This is the whole point of departNow: the seeded state still carries construction time, but
        // the request that reaches OTP carries the moment of submission.
        assertEquals(0L, vm.formState.value.dateTimeMillis)
        assertEquals(90_000L, (vm.planState.value as PlanResult.Success).params?.dateTimeMillis)
    }

    @Test
    fun `picking a time pins the trip against a moving clock`() = runTest {
        val clock = FakeClock(0L)
        val vm = viewModel(clock = clock)
        setBothEndpoints(vm)
        advanceUntilIdle()

        vm.setDateTime(1_700_000_000_000L)
        clock.nowMillis = 500_000L
        advanceUntilIdle()

        assertFalse(vm.formState.value.departNow)
        assertEquals(
            1_700_000_000_000L,
            (vm.planState.value as PlanResult.Success).params?.dateTimeMillis
        )
    }

    @Test
    fun `setDepartNow returns a pinned trip to the now anchor`() = runTest {
        val clock = FakeClock(0L)
        val vm = viewModel(clock = clock)
        setBothEndpoints(vm)
        vm.setDateTime(1_700_000_000_000L)
        advanceUntilIdle()

        clock.nowMillis = 120_000L
        vm.setDepartNow()
        advanceUntilIdle()

        val state = vm.formState.value
        assertTrue(state.departNow)
        // The pinned instant is refreshed too, so re-opening the picker starts from now rather than
        // from the abandoned selection.
        assertEquals(120_000L, state.dateTimeMillis)
        assertEquals(120_000L, (vm.planState.value as PlanResult.Success).params?.dateTimeMillis)
    }

    /**
     * Refresh's whole reason for existing (#2135): a "depart now" trip is planned against a clock that
     * keeps moving, and until this there was no way to ask for the same trip against the current one
     * without editing the trip.
     */
    @Test
    fun `refreshing a depart-now trip re-plans it against the current clock`() = runTest {
        val clock = FakeClock(0L)
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan, clock = clock)
        setBothEndpoints(vm)
        advanceUntilIdle()
        assertEquals(1, plan.calls)
        assertEquals(0L, plan.lastParams?.dateTimeMillis)

        clock.nowMillis = 600_000L
        vm.refreshPlan()
        advanceUntilIdle()

        assertEquals(2, plan.calls)
        assertEquals(600_000L, plan.lastParams?.dateTimeMillis)
    }

    /** A pinned trip is re-planned, not re-timed — refresh must not quietly move what the rider asked for. */
    @Test
    fun `refreshing a pinned trip re-plans the same instant`() = runTest {
        val clock = FakeClock(0L)
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan, clock = clock)
        setBothEndpoints(vm)
        vm.setDateTime(1_700_000_000_000L)
        advanceUntilIdle()
        val callsBefore = plan.calls

        clock.nowMillis = 600_000L
        vm.refreshPlan()
        advanceUntilIdle()

        assertEquals(callsBefore + 1, plan.calls)
        assertEquals(1_700_000_000_000L, plan.lastParams?.dateTimeMillis)
        assertFalse(vm.formState.value.departNow)
    }

    /**
     * Refresh on a form that names only one end has no trip to re-plan. The button is disabled there,
     * so this pins the ViewModel's own half: it must not issue a request for an incomplete form.
     */
    @Test
    fun `refreshing an incomplete form plans nothing`() = runTest {
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)
        vm.setEndpoint(TripEndpointSlot.FROM, origin)
        advanceUntilIdle()

        vm.refreshPlan()
        advanceUntilIdle()

        assertEquals(0, plan.calls)
    }

    @Test
    fun `a restored trip stays pinned to the instant it was planned for`() = runTest {
        val vm = viewModel(clock = FakeClock(0L))
        vm.restoreFrom(
            from = origin,
            to = destination,
            dateTimeMillis = 1_700_000_000_000L,
            arriving = true,
            itineraries = listOf(TripItinerary())
        )
        val state = vm.formState.value
        assertFalse(state.departNow)
        assertEquals(1_700_000_000_000L, state.dateTimeMillis)
    }

    @Test
    fun `a resumed pin carries the request that produced it, unlike a notification re-entry`() = runTest {
        // The difference that makes a resumed trip refreshable: PlanResult.Success.params is what the
        // change monitor re-plans and what the itinerary's terminus pins are derived from.
        val vm = viewModel(clock = FakeClock(0L))
        val params = pinnedParams()

        vm.restorePinned(params, departNow = false, itineraries = listOf(TripItinerary()))

        val success = vm.planState.value as PlanResult.Success
        assertEquals(params, success.params)
        assertTrue("a redrawn snapshot must not re-arm the change monitor", success.fromSnapshot)
    }

    @Test
    fun `resuming a pin re-plans nothing`() = runTest {
        // The whole point of the stored snapshot: resume draws it, and the Refresh button is the only
        // thing that goes to the network.
        val plan = FakeTripPlanRepository(Result.success(listOf(TripItinerary())))
        val vm = viewModel(plan = plan)

        vm.restorePinned(pinnedParams(), departNow = false, itineraries = listOf(TripItinerary()))
        advanceUntilIdle()

        assertEquals(0, plan.calls)
    }

    @Test
    fun `a trip pinned on the now anchor comes back on it, with the clock re-read`() = runTest {
        // The stored instant is when the request was *submitted*. Handing it back as the rider's
        // starting point would be the moving-anchor bug departNow exists to prevent.
        val vm = viewModel(clock = FakeClock(9_000_000L))

        vm.restorePinned(pinnedParams(), departNow = true, itineraries = listOf(TripItinerary()))

        val state = vm.formState.value
        assertTrue(state.departNow)
        assertEquals(9_000_000L, state.dateTimeMillis)
    }

    @Test
    fun `a trip pinned to a stated instant comes back on that instant`() = runTest {
        val vm = viewModel(clock = FakeClock(9_000_000L))

        vm.restorePinned(pinnedParams(), departNow = false, itineraries = listOf(TripItinerary()))

        val state = vm.formState.value
        assertFalse(state.departNow)
        assertEquals(1_700_000_000_000L, state.dateTimeMillis)
    }

    @Test
    fun `a resumed pin restores the options the trip was planned with`() = runTest {
        // Not just the endpoints: a Refresh has to re-plan the same trip, and it reads these off the
        // form. restoreFrom, the notification path, genuinely doesn't know them.
        val vm = viewModel(clock = FakeClock(0L))
        val params = pinnedParams().copy(
            modes = TripModeSelection(VehicleMode.RAIL, StreetMode.BICYCLE),
            wheelchair = true,
            maxWalkMeters = 800.0,
            walkPreference = WalkPreference.MINIMUM,
            cyclingPreference = CyclingPreference.FLATTEST,
            bikePreference = BikePreference.MAXIMUM
        )

        vm.restorePinned(params, departNow = false, itineraries = listOf(TripItinerary()))

        // Every option the request carries, not a sample of them: restorePinned copies each field by
        // hand, so one left out (or crossed with its neighbour) is exactly the mistake this catches.
        val state = vm.formState.value
        assertEquals(TripModeSelection(VehicleMode.RAIL, StreetMode.BICYCLE), state.modes)
        assertTrue(state.wheelchair)
        assertEquals(800.0, state.maxWalkMeters!!, 0.0)
        assertEquals(WalkPreference.MINIMUM, state.walkPreference)
        assertEquals(CyclingPreference.FLATTEST, state.cyclingPreference)
        assertEquals(BikePreference.MAXIMUM, state.bikePreference)
        assertFalse("optimizeTransfers must arrive as the request stated it", state.optimizeTransfers)
        assertEquals(params.arriving, state.arriving)
    }

    private fun pinnedParams() = TripPlanParams(
        from = origin,
        to = destination,
        dateTimeMillis = 1_700_000_000_000L,
        arriving = false,
        modes = TripModeSelection(),
        wheelchair = false,
        optimizeTransfers = false,
        maxWalkMeters = null
    )

    /**
     * "Arrive by now" asks for a trip that has already finished, so choosing *arriving* leaves the
     * now anchor and pins a concrete instant for the rider to move.
     */
    @Test
    fun `choosing arriving pins the trip to the clock`() = runTest {
        val clock = FakeClock(0L)
        val vm = viewModel(clock = clock)
        clock.nowMillis = 60_000L

        vm.setArriving(true)

        val state = vm.formState.value
        assertTrue(state.arriving)
        assertFalse(state.departNow)
        // Pinned to the clock at the moment of the switch, not to the stale construction stamp.
        assertEquals(60_000L, state.dateTimeMillis)
    }

    /**
     * The callout names the day in words where there is a word for it (#2185), so the ViewModel
     * settles which day a pinned instant falls on alongside the labels it formats.
     */
    @Test
    fun `a pinned instant is classified by the day it falls on`() = runTest {
        val today = LocalDate.of(2026, 6, 10)
        val vm = viewModel(clock = FakeClock(millisOn(today, hour = 9)))

        vm.setDateTime(millisOn(today, hour = 17))
        assertEquals(TripDay.TODAY, vm.formState.value.dayRelation)

        vm.setDateTime(millisOn(today.plusDays(1), hour = 8))
        assertEquals(TripDay.TOMORROW, vm.formState.value.dayRelation)

        vm.setDateTime(millisOn(today.plusDays(2), hour = 8))
        assertEquals(TripDay.OTHER, vm.formState.value.dayRelation)

        // Yesterday is a different day, not a near-enough one — the relation is about the calendar,
        // not about how far off the instant is.
        vm.setDateTime(millisOn(today.minusDays(1), hour = 23))
        assertEquals(TripDay.OTHER, vm.formState.value.dayRelation)
    }

    /**
     * Calendar days, not elapsed hours: half an hour past midnight is *tomorrow* to a rider standing
     * there at 23:30, and would be "today" to any rule that measured the gap instead of the date.
     */
    @Test
    fun `the day a pinned instant falls on is measured in dates, not hours`() = runTest {
        val today = LocalDate.of(2026, 6, 10)
        val vm = viewModel(clock = FakeClock(millisOn(today, hour = 23)))

        vm.setDateTime(millisOn(today.plusDays(1), hour = 0))

        assertEquals(TripDay.TOMORROW, vm.formState.value.dayRelation)
    }

    /** A trip anchored to "now" is by definition for today. */
    @Test
    fun `a form starts on today`() = runTest {
        assertEquals(TripDay.TODAY, viewModel().formState.value.dayRelation)
    }

    /**
     * Pinning the clock takes *one* reading, which then serves as both the instant pinned and the
     * reference its day is measured against. Two readings can straddle midnight, and the trip the
     * rider just pinned to "now" would come back labelled with a different day than the clock it was
     * taken from — so the clock is read at the call site and passed down, never inside the helper.
     */
    @Test
    fun `pinning the clock takes a single reading, even across midnight`() = runTest {
        val midnight = LocalDate.of(2026, 6, 11).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val clock = TickingClock(listOf(midnight - 1))
        val vm = viewModel(clock = clock)

        // From here the clock ticks over midnight on its second read, so a helper that reads it again
        // mid-write lands on the next day and labels 23:59:59.999 as something other than today.
        clock.readings = listOf(midnight - 1, midnight)
        clock.index = 0
        vm.setDepartNow()

        assertEquals(TripDay.TODAY, vm.formState.value.dayRelation)
        assertEquals(midnight - 1, vm.formState.value.dateTimeMillis)
    }

    @Test
    fun `going back to leaving does not disturb a pinned time`() = runTest {
        val vm = viewModel(clock = FakeClock(0L))
        vm.setDateTime(1_700_000_000_000L)

        vm.setArriving(true)
        vm.setArriving(false)

        val state = vm.formState.value
        assertFalse(state.arriving)
        assertFalse(state.departNow)
        assertEquals(1_700_000_000_000L, state.dateTimeMillis)
    }
}
