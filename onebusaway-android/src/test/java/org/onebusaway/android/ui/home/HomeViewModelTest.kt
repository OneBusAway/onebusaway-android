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

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.location.FakeLocationRepository
import org.onebusaway.android.map.ItineraryPins
import org.onebusaway.android.map.RiddenSpan
import org.onebusaway.android.map.RideRouteGroup
import org.onebusaway.android.map.RouteFocusRelationship
import org.onebusaway.android.map.RouteFocusSegment
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.map.render.MapViewport
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.RegionStatus
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.tripresults.AlternativeRouteRef
import org.onebusaway.android.ui.tripresults.FocusedLeg
import org.onebusaway.android.ui.tripresults.RouteLegRef
import org.onebusaway.android.ui.tripresults.RouteStopRef
import org.onebusaway.android.util.GeoPoint

private class FakeStartupPreferencesRepository(
    var initial: Boolean = false
) : StartupPreferencesRepository {
    var cleared = 0
    override fun isInitialStartup(): Boolean = initial
    override fun clearInitialStartup() {
        cleared++
        initial = false
    }
}

/**
 * Collects the map directives a [HomeViewModel] emits (and reads its bottom-padding state) so the
 * outbound Home→Map interactions can be asserted directly — the role the old MapInteractionBus fake
 * filled, now just reading the VM's own outputs. Launch [collect] inside the test's scope first.
 */
private class MapDirectiveRecorder(private val vm: HomeViewModel) {
    val sent = mutableListOf<MapDirective>()

    val recenters get() = sent.filterIsInstance<MapDirective.RecenterOnFocusedStop>().map { it.point.latitude to it.point.longitude }
    val routeRequests get() = sent.filterIsInstance<MapDirective.ShowRoute>().map { it.request }
    val routeCommands get() = sent.filterIsInstance<MapDirective.ShowRoute>()
    val routesShown get() = sent.filterIsInstance<MapDirective.ShowRoute>().map { it.request.routeId }
    val stopRoutes get() = sent.filterIsInstance<MapDirective.ShowStopRoutes>()
    val rideArrivals get() = sent.filterIsInstance<MapDirective.SetRideArrivals>()
    val clearStopRoutesCount get() = sent.count { it is MapDirective.ClearStopRoutes }
    val clearFocusCount get() = sent.count { it is MapDirective.ClearFocus }
    val focusStops get() = sent.filterIsInstance<MapDirective.FocusStop>()
    val viewportRestores get() = sent.filterIsInstance<MapDirective.RestoreViewport>()
    val lastBottomPadding get() = vm.mapBottomPadding.value

    suspend fun collect() {
        vm.mapDirectives.collect { sent.add(it) }
    }
}

/**
 * Unit tests for [HomeViewModel]: focus coordination, the arrivals-sheet → map effects, and region resolution.
 * Mirrors the established ViewModel test pattern (MainDispatcherRule + runTest + hand-written fakes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        regionStatus: RegionStatus = RegionStatus.Unchanged,
        startupRepo: FakeStartupPreferencesRepository = FakeStartupPreferencesRepository(),
        regionRepo: FakeRegionRepository = FakeRegionRepository().apply { refreshResult = regionStatus },
        savedState: SavedStateHandle = SavedStateHandle(),
        locationRepo: FakeLocationRepository = FakeLocationRepository()
    ) = HomeViewModel(
        savedState,
        startupRepo,
        regionRepo,
        locationRepo
    )

    // The raw stop payload onArrivalsLoaded forwards to the map; its identity is irrelevant to the
    // pending-focus gate, so one shared fixture suffices.
    private val obaStop = ObaStopElement("1", 47.6, -122.3, "Main St", "100")

    @Test
    fun `focusing an interchangeable itinerary leg shows every resolved route`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val board = RouteStopRef("40_N23-T2", "N23", "Lynnwood City Center", GeoPoint(47.8158, -122.2942))
        val alight = RouteStopRef("40_990", "Westlake", "Westlake Station", GeoPoint(47.6114, -122.3376))
        val routeLeg = RouteLegRef(
            routeId = "40_2LINE",
            headsign = "Downtown Redmond",
            board = board,
            alight = alight,
            alternatives = listOf(
                AlternativeRouteRef("40_1LINE", "Federal Way Downtown", "1 Line", null),
                // Still appears in the joined badge, but cannot be loaded without an OBA route id.
                AlternativeRouteRef(null, "Tacoma Dome", "T Line", null)
            )
        )
        val points = listOf(board.point!!, GeoPoint(47.6114, -122.3376))

        vm.focusItineraryRouteLeg(routeLeg, FocusedLeg(points, setOf(1)))
        advanceUntilIdle()

        assertEquals(
            ShowRouteRequest(
                routeId = "40_2LINE",
                directionStopId = board.stopId,
                // No spans on the ref (this fixture resolves none), so the tapped row's own geometry
                // stands in as one undivided span — what the map drew before rides had spans.
                riddenSpans = listOf(RiddenSpan(points)),
                extraSegments = listOf(
                    RouteFocusSegment(
                        "40_1LINE",
                        board.stopId,
                        relationship = RouteFocusRelationship.INTERCHANGEABLE,
                        directionHeadsign = "Federal Way Downtown"
                    )
                ),
                // Where the rider leaves the ride — the one bound queue-driven selection needs (#2124),
                // and the leg's headsign, which picks the ridden direction among the stop's arrival rows.
                alightStopId = alight.stopId,
                directionHeadsign = "Downtown Redmond"
            ),
            map.routeRequests.single()
        )
        job.cancel()
    }

    @Test
    fun `focusing an interlined ride hands the map its per-route spans, not the joined geometry`() = runTest {
        // #2127: the ride's own spans carry where it changes route, so drilling in can draw each route in
        // its own colour and cut the line between them. The tapped row's joined geometry is only the
        // fallback for a ride that resolved no spans at all.
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val spans = listOf(
            RiddenSpan(listOf(GeoPoint(47.60, -122.33), GeoPoint(47.62, -122.33)), routeId = "1_45"),
            RiddenSpan(listOf(GeoPoint(47.62, -122.33), GeoPoint(47.62, -122.30)), routeId = "1_75", startsCutover = true)
        )
        val routeLeg = RouteLegRef(
            routeId = "1_45",
            headsign = "Downtown",
            board = null,
            alight = null,
            riddenSpans = spans
        )

        vm.focusItineraryRouteLeg(routeLeg, FocusedLeg(listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)), setOf(1, 2)))
        advanceUntilIdle()

        assertEquals(spans, map.routeRequests.single().riddenSpans)
        job.cancel()
    }

    @Test
    fun `focusing an ETA pill draws the same ride the leg card would, spans or fallback`() = runTest {
        // An ETA pill enters the same route focus as the leg card it sits in, so it has to resolve the ride
        // the same way: the ref's spans when it has them, and the row's own geometry as one span when it
        // doesn't (an OTP1 plan, or a leg the repository couldn't resolve) — otherwise tapping a pill on an
        // unresolved ride would drop the traveled line the leg tap draws.
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val spans = listOf(RiddenSpan(listOf(GeoPoint(47.60, -122.33), GeoPoint(47.62, -122.33)), routeId = "1_45"))
        val legPoints = listOf(GeoPoint(47.55, -122.31), GeoPoint(47.58, -122.32))
        val request = ShowRouteRequest(routeId = "1_45", focusTripId = "1_trip")
        val ref = RouteLegRef(routeId = "1_45", headsign = "Downtown", board = null, alight = null)

        vm.focusDirectionsRouteVehicle(request, ref.copy(riddenSpans = spans), legPoints)
        vm.focusDirectionsRouteVehicle(request, ref, legPoints)
        advanceUntilIdle()

        assertEquals(
            listOf(spans, listOf(RiddenSpan(legPoints))),
            map.routeRequests.map { it.riddenSpans }
        )
        job.cancel()
    }

    @Test
    fun `focusing an alternative ETA carries the ride's own alighting stop and the planned headsign`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val routeLeg = RouteLegRef(
            routeId = "40_2LINE",
            headsign = "Downtown Redmond",
            board = RouteStopRef("40_board", "B", "Board", GeoPoint(47.6, -122.3)),
            alight = RouteStopRef("40_planned_alight", "A", "Alight", GeoPoint(47.61, -122.31)),
            alternatives = listOf(AlternativeRouteRef("40_1LINE", null, "1 Line", null))
        )
        val segment = listOf(GeoPoint(47.6, -122.3), GeoPoint(47.61, -122.31))

        vm.focusDirectionsRouteVehicle(
            request = ShowRouteRequest(
                routeId = "40_1LINE",
                directionStopId = "40_board",
                focusTripId = "alternative-trip"
            ),
            routeLeg = routeLeg,
            fallbackPoints = segment
        )
        advanceUntilIdle()

        // An alternative substitutes for the planned leg, so it is bounded at the same alighting stop.
        // There is no restrictive/nonrestrictive distinction any more: admission comes from the boarding
        // stop's arrivals, which every alternative shares by construction, so the bound only has to say
        // when the ride is over.
        assertEquals(
            ShowRouteRequest(
                routeId = "40_1LINE",
                directionStopId = "40_board",
                focusTripId = "alternative-trip",
                riddenSpans = listOf(RiddenSpan(segment)),
                alightStopId = "40_planned_alight",
                directionHeadsign = "Downtown Redmond"
            ),
            map.routeRequests.single()
        )
        job.cancel()
    }

    // -- the focused leg's boarding-stop arrivals, which select the ride's vehicles (#2124) --

    /** A leg on 40_2LINE boarding at 40_board and alighting at 40_alight, with one alternative. */
    private fun rideLeg() = RouteLegRef(
        routeId = "40_2LINE",
        headsign = "Downtown Redmond",
        board = RouteStopRef("40_board", "B", "Board", GeoPoint(47.6, -122.3)),
        alight = RouteStopRef("40_alight", "A", "Alight", GeoPoint(47.61, -122.31)),
        alternatives = listOf(AlternativeRouteRef("40_1LINE", "Federal Way Downtown", "1 Line", null))
    )

    private fun groups() = listOf(RideRouteGroup("40_2LINE", "Downtown Redmond", listOf("t1", "t2")))

    @Test
    fun `the focused leg's boarding-stop arrivals reach the map`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val leg = rideLeg()
        vm.focusItineraryRouteLeg(leg, FocusedLeg(listOf(leg.board!!.point!!, leg.alight!!.point!!), setOf(1)))
        advanceUntilIdle()

        vm.onRideArrivals("40_board", groups())
        advanceUntilIdle()

        assertEquals(1, map.rideArrivals.size)
        assertEquals("40_board", map.rideArrivals.single().stopId)
        assertEquals(groups(), map.rideArrivals.single().groups)
        job.cancel()
    }

    @Test
    fun `arrivals for a stop other than the focused leg's boarding stop are dropped`() = runTest {
        // The hoisted session is re-keyed when the focus moves, so a load already in flight for the
        // previous leg must not select vehicles against the new one.
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val leg = rideLeg()
        vm.focusItineraryRouteLeg(leg, FocusedLeg(listOf(leg.board!!.point!!, leg.alight!!.point!!), setOf(1)))
        advanceUntilIdle()

        vm.onRideArrivals("40_some_other_stop", groups())
        advanceUntilIdle()

        assertTrue(map.rideArrivals.isEmpty())
        job.cancel()
    }

    @Test
    fun `arrivals arriving outside a focused leg are dropped`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        vm.onRideArrivals("40_board", groups())
        advanceUntilIdle()

        assertTrue(map.rideArrivals.isEmpty())
        job.cancel()
    }

    @Test
    fun `a pill tap in the hoisted session rides the focused leg's own ride`() = runTest {
        // The hoisted session sits above the itinerary and cannot close over the leg's row, so the ride
        // is read back off the focus: the tapped route/trip must arrive carrying that ride's spans,
        // alighting stop and headsign, not bare.
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val leg = rideLeg()
        val points = listOf(leg.board!!.point!!, leg.alight!!.point!!)
        vm.focusItineraryRouteLeg(leg, FocusedLeg(points, setOf(1)))
        advanceUntilIdle()

        vm.focusDirectionsRouteVehicleInFocusedLeg(
            ShowRouteRequest(routeId = "40_1LINE", directionStopId = "40_board", focusTripId = "tapped")
        )
        advanceUntilIdle()

        val entered = map.routeRequests.last()
        assertEquals("40_1LINE", entered.routeId)
        assertEquals("tapped", entered.focusTripId)
        assertEquals(listOf(RiddenSpan(points)), entered.riddenSpans)
        assertEquals("40_alight", entered.alightStopId)
        assertEquals("Downtown Redmond", entered.directionHeadsign)
        // The alternative stays loadable alongside the planned route, as it was for the leg itself.
        assertEquals(listOf("40_1LINE"), entered.extraSegments.map { it.routeId })
        job.cancel()
    }

    @Test
    fun `a pill tap outside a focused leg does nothing`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        vm.focusDirectionsRouteVehicleInFocusedLeg(ShowRouteRequest(routeId = "40_1LINE"))
        advanceUntilIdle()

        assertTrue(map.routeRequests.isEmpty())
        job.cancel()
    }

    // A tapped on-street leg. Only [legIndices] distinguishes one from another to the focus model, so the
    // geometry is shared; [walkLeg] gives each test the leg it needs without restating the coordinates.
    private fun walkLeg(vararg legIndices: Int) = FocusedLeg(listOf(GeoPoint(47.6, -122.3), GeoPoint(47.61, -122.31)), legIndices.toSet())

    /** Directions focus with [itinerary] drawn — the state every leg-focus test starts from. */
    private fun HomeViewModel.enterDirectionsShowing(itinerary: TripItinerary = TripItinerary()) = itinerary.also {
        enterDirections()
        showItineraryOnMap(it)
    }

    /**
     * A background tap and Back both drop a focused on-street leg to the itinerary overview before leaving
     * directions — the same two steps, so both gestures ([dropOneLevel]) are held to one script. Where they
     * differ is which state they drop *to* with several legs visited; that's the walk test below.
     */
    private fun assertDropsLegThenExitsDirections(dropOneLevel: HomeViewModel.() -> Unit) = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.enterDirectionsShowing()
        val walk = walkLeg(0)

        vm.focusItineraryLegOnMap(walk)
        advanceUntilIdle()
        assertEquals(CurrentFocus.Directions(DirectionsSubFocus.Leg(walk)), vm.currentFocus.value)
        map.sent.clear()

        // The first gesture drops the leg, not directions: the trip's legs return to full weight.
        vm.dropOneLevel()
        advanceUntilIdle()
        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
        assertEquals(listOf(MapDirective.ClearItineraryLegFocus), map.sent)

        // The second one would leave, but the trip is drawn, so it asks first (#2140) and the answer
        // is what actually leaves.
        vm.dropOneLevel()
        advanceUntilIdle()
        assertTrue(vm.pendingDirectionsExit.value)
        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
        vm.confirmExitDirections()
        advanceUntilIdle()
        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertEquals(1, map.clearFocusCount)
        job.cancel()
    }

    /**
     * Both gestures that would discard a drawn trip stage the same question, and declining it leaves the
     * rider exactly where they were — still in directions, with nothing sent to the map.
     */
    private fun assertConfirmsBeforeDiscardingTrip(leave: HomeViewModel.() -> Unit) = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.enterDirectionsShowing()
        advanceUntilIdle()
        map.sent.clear()

        vm.leave()
        advanceUntilIdle()
        assertTrue(vm.pendingDirectionsExit.value)
        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
        assertTrue(map.sent.isEmpty())

        vm.dismissDirectionsExit()
        advanceUntilIdle()
        assertFalse(vm.pendingDirectionsExit.value)
        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
        assertTrue(map.sent.isEmpty())
        job.cancel()
    }

    @Test
    fun `back out of a drawn trip asks before discarding it`() = assertConfirmsBeforeDiscardingTrip(HomeViewModel::navigateBackInDirections)

    @Test
    fun `tapping off a drawn trip asks before discarding it`() = assertConfirmsBeforeDiscardingTrip(HomeViewModel::unfocusMapOneLevel)

    /** With no trip drawn there is nothing to lose, so leaving directions costs no dialog. */
    private fun assertLeavesUnplannedDirectionsOutright(leave: HomeViewModel.() -> Unit) = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.enterDirections()

        vm.leave()
        advanceUntilIdle()

        assertFalse(vm.pendingDirectionsExit.value)
        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertEquals(1, map.clearFocusCount)
        job.cancel()
    }

    @Test
    fun `back out of an unplanned form leaves directions outright`() = assertLeavesUnplannedDirectionsOutright(HomeViewModel::navigateBackInDirections)

    @Test
    fun `tapping off an unplanned form leaves directions outright`() = assertLeavesUnplannedDirectionsOutright(HomeViewModel::unfocusMapOneLevel)

    @Test
    fun `a trip cleared off the map no longer guards the way out`() = runTest {
        val vm = viewModel()
        vm.enterDirectionsShowing()
        // The plan became unsubmittable, so the drawn trip went with it — there is nothing left to lose.
        vm.clearShownItineraryOnMap()

        vm.navigateBackInDirections()

        assertFalse(vm.pendingDirectionsExit.value)
        assertEquals(CurrentFocus.None, vm.currentFocus.value)
    }

    @Test
    fun `a fresh entry doesn't inherit the previous visit's trip`() = runTest {
        val vm = viewModel()
        vm.enterDirectionsShowing()
        vm.navigateBackInDirections()
        vm.confirmExitDirections()

        // Planning again from an empty form: the trip that was on the map last time isn't on it now, so
        // the first gesture out leaves rather than asking about a trip the rider can't see.
        vm.enterDirections()
        vm.navigateBackInDirections()

        assertFalse(vm.pendingDirectionsExit.value)
        assertEquals(CurrentFocus.None, vm.currentFocus.value)
    }

    @Test
    fun `a bike dock tapped on a drawn trip is refused, not allowed to take the map`() = runTest {
        val vm = viewModel()
        vm.enterDirectionsShowing()

        // The docks drawn in directions are the trip's own, so tapping one used to discard the whole
        // plan in a single tap — the same take-over a stop tap is already refused for (#2097).
        assertFalse(vm.onBikeStationFocused("bike-7"))

        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
        assertNull(vm.currentFocus.value.focusedBikeStationId)
        // Refused outright rather than asked about: nothing is lost, so there is nothing to confirm.
        assertFalse(vm.pendingDirectionsExit.value)
    }

    @Test
    fun `a deep link taking the map drops the staged question`() = runTest {
        val vm = viewModel()
        vm.enterDirectionsShowing()
        vm.navigateBackInDirections()
        assertTrue(vm.pendingDirectionsExit.value)

        // The question was asked of directions; a stop opened from elsewhere replaces that focus outright,
        // so the dialog has nothing left to answer for.
        vm.revealStop(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))

        assertFalse(vm.pendingDirectionsExit.value)
    }

    @Test
    fun `tapping off a focused on-street leg returns to the itinerary overview`() = assertDropsLegThenExitsDirections(HomeViewModel::unfocusMapOneLevel)

    @Test
    fun `back steps out of a focused on-street leg before leaving directions`() = assertDropsLegThenExitsDirections(HomeViewModel::navigateBackInDirections)

    @Test
    fun `back after tapping off an on-street leg refocuses it`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.enterDirectionsShowing()
        val walk = walkLeg(2)
        vm.focusItineraryLegOnMap(walk)
        vm.unfocusMapOneLevel()
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()

        assertEquals(CurrentFocus.Directions(DirectionsSubFocus.Leg(walk)), vm.currentFocus.value)
        assertEquals(
            listOf(MapDirective.FocusItineraryLeg(walk.points, walk.legIndices)),
            map.sent
        )
        job.cancel()
    }

    @Test
    fun `back walks the focused legs the user visited in order`() = runTest {
        val vm = viewModel()
        vm.enterDirectionsShowing()
        val first = walkLeg(0)
        vm.focusItineraryLegOnMap(first)
        vm.focusItineraryLegOnMap(walkLeg(2))

        // Unlike the map-background tap (which jumps straight to the overview), Back retraces each leg.
        vm.navigateBackInDirections()
        assertEquals(CurrentFocus.Directions(DirectionsSubFocus.Leg(first)), vm.currentFocus.value)
        vm.navigateBackInDirections()
        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
    }

    @Test
    fun `back steps out of a transit leg's route focus to the itinerary overview`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val itinerary = vm.enterDirectionsShowing()
        vm.focusItineraryRouteLegOnMap("65")
        advanceUntilIdle()
        map.sent.clear()

        vm.navigateBackInDirections()
        advanceUntilIdle()

        // Route mode tore the trip down, so returning to the overview redraws it.
        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
        assertEquals(listOf(MapDirective.ShowItinerary(itinerary)), map.sent)
        job.cancel()
    }

    @Test
    fun `a redrawn trip keeps the terminus pin its current-location endpoint withheld`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val itinerary = TripItinerary()
        // A trip planned *from* here: the blue dot marks the start, so it wears no start pin (#2111).
        val pins = ItineraryPins(start = false)
        vm.enterDirections()
        vm.showItineraryOnMap(itinerary, pins)
        vm.focusItineraryRouteLegOnMap("65")
        advanceUntilIdle()
        map.sent.clear()

        vm.navigateBackInDirections()
        advanceUntilIdle()

        // The redraw reproduces the trip as it was drawn — a suppressed pin doesn't come back with it.
        assertEquals(listOf(MapDirective.ShowItinerary(itinerary, pins)), map.sent)
        job.cancel()
    }

    /**
     * A route sub-focus entered before any itinerary was drawn has nothing to redraw on the way back, so
     * both gestures clear the map rather than emitting nothing and leaving it in route mode while the focus
     * already reads as the plain overview.
     */
    private fun assertClearsMapWithNoDrawnItinerary(dropOneLevel: HomeViewModel.() -> Unit) = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.enterDirections()
        vm.focusItineraryRouteLegOnMap("65")
        advanceUntilIdle()
        map.sent.clear()

        vm.dropOneLevel()
        advanceUntilIdle()

        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
        assertEquals(listOf(MapDirective.ClearFocus), map.sent)
        job.cancel()
    }

    @Test
    fun `tapping off a route focus with no drawn itinerary clears the map`() = assertClearsMapWithNoDrawnItinerary(HomeViewModel::unfocusMapOneLevel)

    @Test
    fun `back out of a route focus with no drawn itinerary clears the map`() = assertClearsMapWithNoDrawnItinerary(HomeViewModel::navigateBackInDirections)

    @Test
    fun `focusing an on-street leg from a route focus redraws the trip first`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val itinerary = vm.enterDirectionsShowing()
        vm.focusItineraryRouteLegOnMap("65")
        advanceUntilIdle()
        map.sent.clear()
        val walk = walkLeg(1)

        vm.focusItineraryLegOnMap(walk)
        advanceUntilIdle()

        // Route mode tore the itinerary down, so it is redrawn before the leg framing (which would
        // otherwise no-op), and the leg — not the route — is now the focus.
        assertEquals(
            listOf(
                MapDirective.ShowItinerary(itinerary),
                MapDirective.FocusItineraryLeg(walk.points, walk.legIndices)
            ),
            map.sent
        )
        assertEquals(CurrentFocus.Directions(DirectionsSubFocus.Leg(walk)), vm.currentFocus.value)
        job.cancel()
    }

    // --- arrivals sheet settled -> map padding / recenter ---

    @Test
    fun `the initial sheet reveal from hidden emits no map effects`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // previous == Hidden -> skip
        advanceUntilIdle()

        assertTrue(map.sent.isEmpty())
        assertEquals(ArrivalsSheetState.Collapsed, vm.lastSettledSheet)
        job.cancel()
    }

    @Test
    fun `expanding over a focused stop sets padding and recenters`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Expanded, 120)
        advanceUntilIdle()

        assertEquals(120, vm.mapBottomPadding.value)
        assertEquals(listOf(47.6 to -122.3), map.recenters)
        job.cancel()
    }

    @Test
    fun `expanding with no focused stop only sets padding`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Expanded, 120)
        advanceUntilIdle()

        assertEquals(120, vm.mapBottomPadding.value)
        assertTrue(map.recenters.isEmpty())
        job.cancel()
    }

    @Test
    fun `collapsing and hiding set the map padding`() = runTest {
        // Bottom padding is plain state, so no directive collector is needed.
        val vm = viewModel()

        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 80)
        assertEquals(80, vm.mapBottomPadding.value)
        vm.onSheetSettled(ArrivalsSheetState.Hidden, 80)
        assertEquals(0, vm.mapBottomPadding.value)
        assertEquals(ArrivalsSheetState.Hidden, vm.lastSettledSheet)
    }

    // --- initial focus (restored vs intent deep-link) ---

    @Test
    fun `applyInitialFocus adopts an intent stop and marks it pending`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.applyInitialFocus(stop)
        assertEquals(stop, vm.currentFocus.value.focusedStop)
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size) // pending was marked -> focus dispatched to the map
        job.cancel()
    }

    @Test
    fun `applyInitialFocus keeps a restored focus and marks it pending`() = runTest {
        val handle = SavedStateHandle()
        val restored = FocusedStop("42", "Pike St", "577", GeoPoint(47.61, -122.34))
        viewModel(savedState = handle).onStopFocused(restored)
        val vm = viewModel(savedState = handle) // recreation: focus restored from the handle
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.applyInitialFocus(null) // intent carries no stop
        assertEquals(restored, vm.currentFocus.value.focusedStop) // unchanged
        vm.onArrivalsLoaded(ObaStopElement("42", 47.61, -122.34, "Pike St", "577"), null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size) // pending was marked
        job.cancel()
    }

    @Test
    fun `applyInitialFocus with no restored or intent focus does nothing`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.applyInitialFocus(null)
        assertNull(vm.currentFocus.value.focusedStop)
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(0, map.focusStops.size) // not pending -> nothing dispatched
        job.cancel()
    }

    // --- pending map focus / route mode / clear focus ---

    @Test
    fun `a pending focus is dispatched once on arrivals load`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        advanceUntilIdle()
        map.sent.clear()
        vm.markPendingMapFocus()
        // Pending -> dispatch FocusStop (sheet not expanded -> overlayExpanded false); latch then clears.
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size)
        assertEquals(false, map.focusStops.single().overlayExpanded)
        vm.onArrivalsLoaded(obaStop, null, emptySet()) // latch cleared -> no further dispatch
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size)
        job.cancel()
    }

    @Test
    fun `arrivals load with no pending focus dispatches nothing`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(0, map.focusStops.size)
        job.cancel()
    }

    @Test
    fun `a pending focus dispatches overlay-expanded when the sheet is expanded`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onSheetSettled(ArrivalsSheetState.Collapsed, 120) // reveal, skipped
        vm.onSheetSettled(ArrivalsSheetState.Expanded, 120)

        vm.markPendingMapFocus()
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(true, map.focusStops.single().overlayExpanded)
        job.cancel()
    }

    @Test
    fun `standalone route focus shows the route`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()

        vm.focusStandaloneRoute(ShowRouteRequest("42"))
        advanceUntilIdle()

        assertEquals(listOf("42"), map.routesShown)
        assertEquals(CurrentFocus.Route(RouteTarget("42")), vm.currentFocus.value)
        mapJob.cancel()
    }

    @Test
    fun `directives emitted while no collector is subscribed all queue, past the old bound`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)

        // Emit far more directives than the old Channel(capacity = 16) could hold, with NO collector
        // subscribed yet — the exact situation #1904 flagged: a directive fired while HOME's collector
        // is away (e.g. on a pushed search destination) must queue for it, not vanish on a full-channel
        // trySend. An UNLIMITED channel buffers them all; the old bounded one dropped everything past 16.
        val count = 40
        repeat(count) { vm.focusStandaloneRoute(ShowRouteRequest("route-$it")) }

        val mapJob = launch { map.collect() }
        advanceUntilIdle()

        assertEquals((0 until count).map { "route-$it" }, map.routesShown)
        mapJob.cancel()
    }

    @Test
    fun `focused stop route badge preserves stop focus and line direction`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3)))

        vm.requestShowFocusedStopRouteOnMap("42", directionId = 1)
        advanceUntilIdle()

        assertEquals(
            ShowRouteRequest(routeId = "42", directionStopId = "stop", initialDirectionId = 1),
            map.routeRequests.single()
        )
        assertTrue(map.routeCommands.single().stopScoped)
        mapJob.cancel()
    }

    @Test
    fun `an ordinary arrivals poll refreshes the selected route without reframing`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3)))
        advanceUntilIdle()
        vm.selectArrivalRoute(
            request = ShowRouteRequest("65", directionStopId = "stop", initialDirectionId = 0),
            shortName = "65",
            headsign = "Downtown"
        )
        advanceUntilIdle()
        // Selecting the route frames it once.
        assertEquals(true, map.routeCommands.single().frameRoute)
        map.sent.clear()

        // A subsequent arrivals poll (no pending focus) re-shows the selected route but must not reframe.
        vm.onArrivalsLoaded(ObaStopElement("stop", 47.6, -122.3, "Main St", "100"), emptyList(), emptySet())
        advanceUntilIdle()

        assertEquals(false, map.routeCommands.single().frameRoute)
        mapJob.cancel()
    }

    @Test
    fun `drawer route selection and continuation remain subordinate to stop focus`() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        advanceUntilIdle()
        map.sent.clear()

        vm.selectArrivalRoute(
            request = ShowRouteRequest("65", directionStopId = "stop", initialDirectionId = 0),
            shortName = "65",
            headsign = "Downtown"
        )
        vm.advanceRouteContinuation("75", "75", directionId = 1)
        advanceUntilIdle()

        val focus = vm.currentFocus.value as CurrentFocus.Stop
        assertEquals(stop, focus.stop)
        assertEquals("Downtown", focus.selectedRoute?.originHeadsign)
        assertEquals(listOf("65", "75"), focus.selectedRoute?.legs?.map { it.shortName })
        assertEquals(listOf(true, true), map.routeCommands.map { it.stopScoped })
        val restored = viewModel(savedState = savedState).currentFocus.value as CurrentFocus.Stop
        assertEquals(focus.selectedRoute, restored.selectedRoute)
        mapJob.cancel()
    }

    @Test
    fun `an unscoped drawer route request enters standalone route focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3)))
        advanceUntilIdle()
        map.sent.clear()

        // The row menu's "Show route on map": a bare request with no stop scoping must behave like a
        // searched route — standalone route focus, not a selection under the focused stop.
        vm.selectArrivalRoute(
            request = ShowRouteRequest("65"),
            shortName = "65",
            headsign = "Downtown"
        )
        advanceUntilIdle()

        assertEquals(ShowRouteRequest("65"), map.routeRequests.single())
        assertEquals(false, map.routeCommands.single().stopScoped)
        assertEquals(CurrentFocus.Route(RouteTarget("65")), vm.currentFocus.value)
        mapJob.cancel()
    }

    @Test
    fun `clearing a subordinate route retains stop focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", null, "65")

        vm.clearStopRouteSelection()
        advanceUntilIdle()

        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        assertEquals(1, map.sent.count { it is MapDirective.ClearSelectedRoute })
        mapJob.cancel()
    }

    @Test
    fun `focus undo returns from stop route to stop to none`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0, shortName = "65")
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        assertEquals(1, map.sent.count { it is MapDirective.ClearSelectedRoute })

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertEquals(1, map.clearFocusCount)
        assertEquals(false, vm.navigateBackFocus())
        assertEquals(false, vm.canUndoMapAction.value)
        mapJob.cancel()
    }

    @Test
    fun `switching selected routes can undo to the previous route`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        val viewport = MapViewport(GeoPoint(47.61, -122.31), 14.5)
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0, shortName = "65")
        vm.requestShowFocusedStopRouteOnMap(
            "75",
            directionId = 1,
            shortName = "75",
            undoViewport = viewport
        )
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()

        val restored = vm.currentFocus.value as CurrentFocus.Stop
        assertEquals("65", restored.selectedRoute?.currentLeg?.routeId)
        assertEquals(false, map.routeCommands.single().frameRoute)
        assertEquals(listOf(viewport), map.viewportRestores.map { it.viewport })
        mapJob.cancel()
    }

    @Test
    fun `back restores viewport captured before selecting a stop route`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        val viewport = MapViewport(GeoPoint(47.62, -122.32), 13.25)
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0, undoViewport = viewport)
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()

        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        assertEquals(
            listOf(MapDirective.ClearSelectedRoute, MapDirective.RestoreViewport(viewport)),
            map.sent
        )
        mapJob.cancel()
    }

    @Test
    fun `camera-only semantic action restores viewport without changing focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val viewport = MapViewport(GeoPoint(47.63, -122.33), 12.0)
        vm.focusStandaloneRoute(ShowRouteRequest("65"))
        advanceUntilIdle()
        map.sent.clear()
        vm.reframeFocusedRoute(viewport)
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()

        assertEquals(CurrentFocus.Route(RouteTarget("65")), vm.currentFocus.value)
        assertEquals(listOf(MapDirective.RestoreViewport(viewport)), map.sent)
        mapJob.cancel()
    }

    @Test
    fun `back from standalone route restores its previous stop after arrivals load`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        vm.focusStandaloneRoute(ShowRouteRequest("65"))
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        assertEquals(1, map.clearFocusCount)

        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()
        assertEquals(1, map.focusStops.size)
        assertEquals(1, map.stopRoutes.size)
        mapJob.cancel()
    }

    @Test
    fun `restoring a prior stop viewport prevents delayed focus from recentering`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3))
        val viewport = MapViewport(GeoPoint(47.64, -122.34), 13.0)
        vm.onStopFocused(stop)
        vm.focusStandaloneRoute(ShowRouteRequest("65"), undoViewport = viewport)
        advanceUntilIdle()
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        vm.onArrivalsLoaded(obaStop, null, emptySet())
        advanceUntilIdle()

        assertEquals(listOf(viewport), map.viewportRestores.map { it.viewport })
        assertEquals(false, map.focusStops.single().recenter)
        mapJob.cancel()
    }

    @Test
    fun `back restores a route removed by a map tap`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0)

        vm.unfocusMapOneLevel()
        advanceUntilIdle()
        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        map.sent.clear()

        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        val restored = vm.currentFocus.value as CurrentFocus.Stop
        assertEquals("65", restored.selectedRoute?.currentLeg?.routeId)
        assertEquals(listOf("65"), map.routesShown)
        mapJob.cancel()
    }

    @Test
    fun `map taps drop route attention then stop attention`() = runTest {
        val vm = viewModel()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0)

        vm.unfocusMapOneLevel()
        assertEquals(CurrentFocus.Stop(stop), vm.currentFocus.value)
        vm.unfocusMapOneLevel()

        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertTrue(vm.canUndoMapAction.value)
    }

    @Test
    fun `focus banner close clears stop and subordinate route together`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val mapJob = launch { map.collect() }
        advanceUntilIdle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        vm.requestShowFocusedStopRouteOnMap("65", directionId = 0)
        advanceUntilIdle()
        map.sent.clear()

        vm.clearMapFocus()
        advanceUntilIdle()

        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertEquals(1, map.clearFocusCount)
        assertTrue(vm.navigateBackFocus())
        advanceUntilIdle()
        val restored = vm.currentFocus.value as CurrentFocus.Stop
        assertEquals("65", restored.selectedRoute?.currentLeg?.routeId)
        mapJob.cancel()
    }

    @Test
    fun `map tap from plain stop preserves older focus as undo history`() = runTest {
        val vm = viewModel()
        val first = FocusedStop("first", "1st Ave", "100", GeoPoint(47.6, -122.3))
        val second = FocusedStop("second", "2nd Ave", "200", GeoPoint(47.61, -122.31))
        vm.onStopFocused(first)
        vm.onStopFocused(second)

        vm.unfocusMapOneLevel()
        assertEquals(CurrentFocus.None, vm.currentFocus.value)
        assertTrue(vm.navigateBackFocus())
        assertEquals(CurrentFocus.Stop(second), vm.currentFocus.value)
        assertTrue(vm.navigateBackFocus())
        assertEquals(CurrentFocus.Stop(first), vm.currentFocus.value)
    }

    @Test
    fun `restored stop route reconstructs its stop and root parents`() = runTest {
        val state = SavedStateHandle()
        val stop = FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3))
        viewModel(savedState = state).apply {
            onStopFocused(stop)
            requestShowFocusedStopRouteOnMap("65", directionId = 0)
        }
        val restored = viewModel(savedState = state)

        assertTrue(restored.navigateBackFocus())
        assertEquals(CurrentFocus.Stop(stop), restored.currentFocus.value)
        assertTrue(restored.navigateBackFocus())
        assertEquals(CurrentFocus.None, restored.currentFocus.value)
    }

    @Test
    fun `standalone route replaces stop focus and is restored`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(savedState = handle)
        vm.onStopFocused(FocusedStop("stop", "Main St", "100", GeoPoint(47.6, -122.3)))

        vm.focusStandaloneRoute(ShowRouteRequest("65", initialDirectionId = 1))

        val expected = CurrentFocus.Route(RouteTarget("65", directionId = 1))
        assertEquals(expected, vm.currentFocus.value)
        assertEquals(expected, viewModel(savedState = handle).currentFocus.value)
    }

    @Test
    fun `map unfocus clears the focused stop and the map focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))

        vm.unfocusMapOneLevel()
        advanceUntilIdle()

        assertNull(vm.currentFocus.value.focusedStop)
        assertEquals(1, map.clearFocusCount)
        job.cancel()
    }

    // --- focused-stop exact trips (#1827) ---

    @Test
    fun `arrivals load records exact displayed trips even without a pending focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()

        // No pending restored focus: the load still starts the route view for the already-tapped stop.
        val trips = setOf(
            FocusedTrip("trip-40", "40", "shape-40-express", 0xFF112233.toInt()),
            FocusedTrip("trip-44", "44", "shape-44-local", null)
        )
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        advanceUntilIdle()
        map.sent.clear()
        vm.onArrivalsLoaded(obaStop, null, trips)
        advanceUntilIdle()

        assertEquals(trips, vm.focusedTrips)
        assertEquals(0, map.focusStops.size)
        assertEquals(listOf(trips), map.stopRoutes.map { it.trips })
        job.cancel()
    }

    @Test
    fun `arrivals load dispatches map focus before focused trips`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        advanceUntilIdle()
        map.sent.clear()
        vm.markPendingMapFocus()

        val trips = setOf(FocusedTrip("trip-7", "7", "shape-7", null))
        vm.onArrivalsLoaded(obaStop, null, trips)
        advanceUntilIdle()

        assertEquals(trips, vm.focusedTrips)
        assertEquals(1, map.focusStops.size) // pending focus still dispatched
        assertTrue(
            map.sent.indexOfFirst { it is MapDirective.FocusStop } <
                map.sent.indexOfFirst { it is MapDirective.ShowStopRoutes }
        )
        job.cancel()
    }

    @Test
    fun `focusing a different stop resets exact trips`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        advanceUntilIdle()
        map.sent.clear()
        vm.onArrivalsLoaded(obaStop, null, setOf(FocusedTrip("trip", "40", "shape", null)))
        assertTrue(vm.focusedTrips.isNotEmpty())

        vm.onStopFocused(FocusedStop("2", "2nd Ave", "200", GeoPoint(47.6, -122.3)))
        advanceUntilIdle()
        assertEquals(emptySet<FocusedTrip>(), vm.focusedTrips)
        assertEquals(1, map.clearStopRoutesCount)
        job.cancel()
    }

    @Test
    fun `adjacent stop on the sole presented route continues the map presentation`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(
            obaStop,
            null,
            setOf(FocusedTrip("trip", "40", "shape", null))
        )
        advanceUntilIdle()
        map.sent.clear()

        val transition = vm.onStopFocused(
            FocusedStop("2", "2nd Ave", "200", GeoPoint(47.61, -122.31)),
            continuingRoutes = setOf(RouteDirectionKey("40", null))
        )
        advanceUntilIdle()

        assertEquals(StopFocusTransition.ContinuePresentation, transition)
        assertEquals("2", vm.currentFocus.value.focusedStop?.id)
        assertEquals(emptySet<FocusedTrip>(), vm.focusedTrips)
        assertEquals(0, map.clearStopRoutesCount)
        job.cancel()
    }

    @Test
    fun `any shared route continues a multiple-route map presentation`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(
            obaStop,
            null,
            setOf(
                FocusedTrip("trip-40", "40", "shape-40", null),
                FocusedTrip("trip-44", "44", "shape-44", null)
            )
        )
        advanceUntilIdle()
        map.sent.clear()

        val transition = vm.onStopFocused(
            FocusedStop("2", "2nd Ave", "200", GeoPoint(47.61, -122.31)),
            continuingRoutes = setOf(RouteDirectionKey("44", null))
        )
        advanceUntilIdle()

        assertEquals(StopFocusTransition.ContinuePresentation, transition)
        assertEquals(0, map.clearStopRoutesCount)
        job.cancel()
    }

    @Test
    fun `shared route continues from route focus and reanchors selection to the next stop`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(
            obaStop,
            null,
            setOf(
                FocusedTrip("trip-45", "45", "shape-45", null, directionId = 0),
                FocusedTrip("trip-79", "79", "shape-79", null, directionId = 0)
            )
        )
        vm.requestShowFocusedStopRouteOnMap("79", directionId = 0)
        advanceUntilIdle()
        val selection = (vm.currentFocus.value as CurrentFocus.Stop).selectedRoute
        map.sent.clear()

        val nextStop = FocusedStop("2", "2nd Ave", "200", GeoPoint(47.61, -122.31))
        val transition = vm.onStopFocused(
            nextStop,
            continuingRoutes = setOf(RouteDirectionKey("79", 0))
        )
        advanceUntilIdle()

        assertEquals(StopFocusTransition.ContinuePresentation, transition)
        assertEquals(CurrentFocus.Stop(nextStop, selection), vm.currentFocus.value)
        assertTrue(map.sent.isEmpty())

        vm.onArrivalsLoaded(
            ObaStopElement("2", 47.61, -122.31, "2nd Ave", "200"),
            null,
            setOf(FocusedTrip("next-trip-79", "79", "next-shape-79", null, directionId = 0))
        )
        advanceUntilIdle()

        assertEquals(listOf("79"), map.routesShown)
        assertEquals("2", map.routeRequests.single().directionStopId)
        assertTrue(map.routeCommands.single().stopScoped)
        job.cancel()
    }

    @Test
    fun `another direction of the selected route replaces route focus`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(
            obaStop,
            null,
            setOf(FocusedTrip("trip-11", "11", "shape-11", null, directionId = 0))
        )
        vm.selectArrivalRoute(
            request = ShowRouteRequest("11", directionStopId = "1", initialDirectionId = 0),
            shortName = "11",
            headsign = "Downtown"
        )
        advanceUntilIdle()
        map.sent.clear()

        val transition = vm.onStopFocused(
            FocusedStop("2", "2nd Ave", "200", GeoPoint(47.61, -122.31)),
            continuingRoutes = setOf(RouteDirectionKey("11", 1))
        )
        advanceUntilIdle()

        assertEquals(StopFocusTransition.ReplacePresentation, transition)
        assertNull((vm.currentFocus.value as CurrentFocus.Stop).selectedRoute)
        assertEquals(1, map.clearStopRoutesCount)
        job.cancel()
    }

    @Test
    fun `continued route focus warm hops using the current route leg`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(
            obaStop,
            null,
            setOf(FocusedTrip("trip-65", "65", "shape-65", null, directionId = 0))
        )
        vm.selectArrivalRoute(
            request = ShowRouteRequest("65", directionStopId = "1", initialDirectionId = 0),
            shortName = "65",
            headsign = "Downtown"
        )
        vm.advanceRouteContinuation("75", "75", directionId = 1)
        advanceUntilIdle()
        map.sent.clear()

        val nextStop = FocusedStop("2", "2nd Ave", "200", GeoPoint(47.61, -122.31))
        val transition = vm.onStopFocused(
            nextStop,
            continuingRoutes = setOf(RouteDirectionKey("75", 1))
        )
        advanceUntilIdle()

        val focus = vm.currentFocus.value as CurrentFocus.Stop
        assertEquals(StopFocusTransition.ContinuePresentation, transition)
        assertEquals(nextStop, focus.stop)
        assertEquals("75", focus.selectedRoute?.currentLeg?.routeId)
        assertTrue(map.sent.isEmpty())
        job.cancel()
    }

    @Test
    fun `stop without a shared route replaces the map presentation`() = runTest {
        val vm = viewModel()
        val map = MapDirectiveRecorder(vm)
        val job = launch { map.collect() }
        advanceUntilIdle()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(
            obaStop,
            null,
            setOf(
                FocusedTrip("trip-45", "45", "shape-45", null),
                FocusedTrip("trip-79", "79", "shape-79", null)
            )
        )
        advanceUntilIdle()
        map.sent.clear()

        val transition = vm.onStopFocused(
            FocusedStop("2", "2nd Ave", "200", GeoPoint(47.61, -122.31)),
            continuingRoutes = setOf(RouteDirectionKey("62", null))
        )
        advanceUntilIdle()

        assertEquals(StopFocusTransition.ReplacePresentation, transition)
        assertEquals(1, map.clearStopRoutesCount)
        job.cancel()
    }

    @Test
    fun `map unfocus resets exact trips`() = runTest {
        val vm = viewModel()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(obaStop, null, setOf(FocusedTrip("trip", "40", "shape", null)))
        assertTrue(vm.focusedTrips.isNotEmpty())

        vm.unfocusMapOneLevel()
        assertEquals(emptySet<FocusedTrip>(), vm.focusedTrips)
    }

    @Test
    fun `focused trips follow the current focus with no explicit reset`() = runTest {
        // The set is derived from the current focus, not a manually-cleared field: leaving the stop
        // for a standalone route empties it by construction (focusStandaloneRoute has no reset call),
        // and the same load re-scoped to a different stop id never leaks across.
        val vm = viewModel()
        vm.onStopFocused(FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3)))
        vm.onArrivalsLoaded(obaStop, null, setOf(FocusedTrip("trip", "40", "shape", null)))
        assertTrue(vm.focusedTrips.isNotEmpty())

        vm.focusStandaloneRoute(ShowRouteRequest("65"))
        assertEquals(emptySet<FocusedTrip>(), vm.focusedTrips)
    }

    // --- focus + SavedStateHandle ---

    @Test
    fun `onStopFocused sets the focused stop`() = runTest {
        val vm = viewModel()
        val stop = FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)
        assertEquals(stop, vm.currentFocus.value.focusedStop)
    }

    @Test
    fun `focusing a stop clears the focused bike station`() = runTest {
        val vm = viewModel()
        vm.onBikeStationFocused("bike-7")
        assertEquals("bike-7", vm.currentFocus.value.focusedBikeStationId)
        vm.onStopFocused(FocusedStop("1", null, null, GeoPoint(1.0, 2.0)))
        assertNull(vm.currentFocus.value.focusedBikeStationId)
    }

    @Test
    fun `focused stop is restored from SavedStateHandle on recreation`() = runTest {
        val handle = SavedStateHandle()
        val stop = FocusedStop("42", "Pike St", "577", GeoPoint(47.61, -122.34))
        viewModel(savedState = handle).onStopFocused(stop)
        // A fresh ViewModel over the same handle simulates process-death recreation.
        assertEquals(stop, viewModel(savedState = handle).currentFocus.value.focusedStop)
    }

    // --- region refresh (events + manual-picker dialog) ---

    @Test
    fun `a changed region reports a region-selected analytics event`() = runTest {
        val region = region(1)
        val vm = viewModel(regionStatus = RegionStatus.Changed(region))
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertEquals(listOf<HomeAnalyticsEvent>(HomeAnalyticsEvent.RegionSelected(region.name)), events)
        job.cancel()
    }

    @Test
    fun `an auto-selected region is announced via the regionFound event`() = runTest {
        val region = region(1)
        val vm = viewModel(regionStatus = RegionStatus.Changed(region))
        val found = mutableListOf<String>()
        val job = launch { vm.regionFound.collect { found.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertEquals(listOf(region.name), found)
        job.cancel()
    }

    @Test
    fun `an unchanged region is not announced`() = runTest {
        val vm = viewModel(regionStatus = RegionStatus.Unchanged)
        val found = mutableListOf<String>()
        val job = launch { vm.regionFound.collect { found.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertTrue(found.isEmpty())
        job.cancel()
    }

    @Test
    fun `an unchanged region reports no analytics event`() = runTest {
        val vm = viewModel(regionStatus = RegionStatus.Unchanged)
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `needing manual selection reports no analytics (the picker is driven off the repository)`() = runTest {
        val regions = listOf(region(1), region(2))
        val vm = viewModel(regionStatus = RegionStatus.NeedsManualSelection(regions))
        val events = mutableListOf<HomeAnalyticsEvent>()
        val job = launch { vm.analyticsEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.refreshRegions()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `skipped, fixed, and failed statuses emit no event`() = runTest {
        val statuses = listOf(RegionStatus.Skipped, RegionStatus.Fixed(region(1)), RegionStatus.Failed)
        for (status in statuses) {
            val vm = viewModel(regionStatus = status)
            val events = mutableListOf<HomeAnalyticsEvent>()
            val job = launch { vm.analyticsEvents.collect { events.add(it) } }
            advanceUntilIdle()

            vm.refreshRegions()
            advanceUntilIdle()

            assertTrue("$status should emit no event", events.isEmpty())
            job.cancel()
        }
    }

    // (The forced-choice picker + the experimental-regions OTP-reset rule moved off HomeViewModel — see
    // RegionPickerViewModelTest and AdvancedSettingsViewModelTest.)

    // --- report-target derivation (send feedback / contact us) ---
    // The "no stop, but a location is known" branch isn't unit-tested: android.location.Location can't be
    // constructed in plain JVM tests (no Robolectric / mocking lib), so it's left to instrumented coverage.

    @Test
    fun `reportTarget is the focused stop when one is focused`() {
        val vm = viewModel()
        val stop = FocusedStop("1_123", "Main St & 1st", "123", GeoPoint(47.6, -122.3))
        vm.onStopFocused(stop)

        assertEquals(ReportTarget.Stop(stop), vm.reportTarget())
    }

    @Test
    fun `reportTarget is Generic with no focused stop and no known location`() {
        val vm = viewModel(locationRepo = FakeLocationRepository(last = null))

        assertEquals(ReportTarget.Generic, vm.reportTarget())
    }

    // --- startup region-check gate ---

    @Test
    fun `first launch without permission defers the region check`() = runTest {
        val region = FakeRegionRepository()
        viewModel(regionRepo = region, startupRepo = FakeStartupPreferencesRepository(initial = true))
            .onHomeStarted(hasLocationPermission = false)
        advanceUntilIdle()
        assertEquals(0, region.refreshCount)
    }

    @Test
    fun `first launch with permission checks the region now`() = runTest {
        val region = FakeRegionRepository()
        viewModel(regionRepo = region, startupRepo = FakeStartupPreferencesRepository(initial = true))
            .onHomeStarted(hasLocationPermission = true)
        advanceUntilIdle()
        assertEquals(1, region.refreshCount)
    }

    @Test
    fun `a later launch checks the region regardless of permission`() = runTest {
        val region = FakeRegionRepository()
        viewModel(regionRepo = region, startupRepo = FakeStartupPreferencesRepository(initial = false))
            .onHomeStarted(hasLocationPermission = false)
        advanceUntilIdle()
        assertEquals(1, region.refreshCount)
    }

    @Test
    fun `the first-launch permission result clears the flag and checks the region`() = runTest {
        val region = FakeRegionRepository()
        val startup = FakeStartupPreferencesRepository(initial = true)
        val vm = viewModel(regionRepo = region, startupRepo = startup)
        vm.onLocationPermissionResult()
        advanceUntilIdle()
        assertEquals(1, startup.cleared)
        assertEquals(1, region.refreshCount)
    }

    @Test
    fun `a permission result after the first launch does nothing`() = runTest {
        val region = FakeRegionRepository()
        val startup = FakeStartupPreferencesRepository(initial = false)
        viewModel(regionRepo = region, startupRepo = startup).onLocationPermissionResult()
        advanceUntilIdle()
        assertEquals(0, startup.cleared)
        assertEquals(0, region.refreshCount)
    }

    // ---- a stop cannot be selected while directions owns the map (#2097) ----

    private val someStop = FocusedStop("1", "Main St", "100", GeoPoint(47.6, -122.3))

    /** Started from a leg sub-focus, the hardest state the one `is Directions` guard has to hold. */
    @Test
    fun `a stop tapped on the directions map is refused`() = runTest {
        val vm = viewModel()
        vm.enterDirectionsShowing()
        val walk = walkLeg(2)
        vm.focusItineraryLegOnMap(walk)

        val transition = vm.onStopFocused(someStop)

        assertEquals(StopFocusTransition.Refused, transition)
        // The refusal is the point: focus is untouched, so there is no stop selection to leave behind
        // when the rider comes back to the trip.
        assertEquals(CurrentFocus.Directions(DirectionsSubFocus.Leg(walk)), vm.currentFocus.value)
    }

    /**
     * A reveal is a deliberate move to that stop, so it is never refused — and it must not rewrite
     * focus on the way in. pushFocus records the focus it replaces, so stepping through None would
     * drop the trip out of the back stack: Back would land on an empty map rather than the itinerary.
     */
    @Test
    fun `back after revealing a stop from directions returns to the trip`() = runTest {
        val vm = viewModel()
        vm.enterDirectionsShowing()

        vm.revealStop(someStop)
        assertEquals(someStop, vm.currentFocus.value.focusedStop)

        vm.navigateBackFocus()

        assertEquals(CurrentFocus.Directions(), vm.currentFocus.value)
    }

    @Test
    fun `a stop tapped outside directions still focuses`() = runTest {
        val vm = viewModel()

        val transition = vm.onStopFocused(someStop)

        assertEquals(StopFocusTransition.ReplacePresentation, transition)
        assertEquals(someStop, vm.currentFocus.value.focusedStop)
    }
}
