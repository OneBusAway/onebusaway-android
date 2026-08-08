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
package org.onebusaway.android.ui.home.nearby

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.contract.ArrivalsForLocation
import org.onebusaway.android.api.contract.ArrivalsForLocationData
import org.onebusaway.android.api.data.NearbyArrivals
import org.onebusaway.android.api.data.NearbyArrivalsDataSource
import org.onebusaway.android.api.data.NearbyArrivalsResult
import org.onebusaway.android.api.data.NearbyArrivalsSupport
import org.onebusaway.android.api.data.isEndpointAbsent
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.GeoPoint
import retrofit2.HttpException
import retrofit2.Response

/**
 * JVM unit tests for the transit-centre drawer's query (#2107): when it asks, when it stops, and how
 * it tells "this region can't serve this" apart from "the network hiccuped" — the distinction that
 * decides whether the feature is switched off for a region or simply retried.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyArrivalsViewModelTest {

    // One scheduler shared by the rule's Main dispatcher and every runTest below, so advanceTimeBy
    // drives the view model's own poll delay (which runs on viewModelScope -> Main) and not just the
    // test body's. Unconfined so the derived `state` recomputes eagerly and can be read synchronously.
    private val scheduler = TestCoroutineScheduler()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher(scheduler))

    /**
     * `state` is a `stateIn(WhileSubscribed)`, so it only asks while something is collecting — which is
     * the mechanism that stops the poll when the screen goes away. Tests therefore need a live
     * collector; `backgroundScope` cancels it when the test ends.
     */
    private fun TestScope.launchCollect(vm: NearbyArrivalsViewModel): Job = backgroundScope.launch { vm.state.collect { } }

    private val viewport = CameraSnapshot(
        center = GeoPoint(47.6109, -122.3376),
        zoom = 17.8,
        latSpan = 0.002,
        lonSpan = 0.003,
        southWest = GeoPoint(47.60, -122.34),
        northEast = GeoPoint(47.62, -122.33)
    )

    private class FakeDataSource(
        var result: NearbyArrivalsResult = NearbyArrivalsResult.Failed(IOException("unset"))
    ) : NearbyArrivalsDataSource {
        val requests = mutableListOf<CameraSnapshot>()
        override suspend fun arrivals(
            viewport: CameraSnapshot,
            minutesAfter: Int
        ): NearbyArrivalsResult {
            requests += viewport
            return result
        }
    }

    /** The OBA deployment every test below is pointed at — the key the support verdict is held under. */
    private val endpoint = "https://api.pugetsound.onebusaway.org/"

    private fun regions(obaBaseUrl: String? = endpoint) = FakeRegionRepository(region(id = 1, obaBaseUrl = obaBaseUrl))

    private fun viewModel(
        dataSource: NearbyArrivalsDataSource,
        support: NearbyArrivalsSupport = NearbyArrivalsSupport(),
        regionRepo: FakeRegionRepository = regions()
    ) = NearbyArrivalsViewModel(dataSource, regionRepo, support)

    private fun http(code: Int) = HttpException(
        Response.error<Unit>(code, "".toResponseBody("text/html".toMediaType()))
    )

    /** A resolved response. Contents don't matter here — these tests are about *when* it asks. */
    private fun nearbyArrivals() = NearbyArrivals(
        ArrivalsForLocationData(entry = ArrivalsForLocation()),
        ServerTime(1_786_132_299_765L),
        NEARBY_MINUTES_AFTER
    )

    // --- when it asks -----------------------------------------------------------------------------

    /** Below the transit-centre band the drawer isn't shown, so nothing is fetched for it. */
    @Test
    fun `no request below the transit-centre band`() = runTest(scheduler) {
        val source = FakeDataSource()
        val vm = viewModel(source)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.FULL)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)

        assertTrue(source.requests.isEmpty())
        assertEquals(NearbyArrivalsUiState.Idle, vm.state.value)
        collector.cancel()
    }

    /** A focused stop hands the sheet to that stop's own session; this query must stop polling. */
    @Test
    fun `no request while another surface owns the sheet`() = runTest(scheduler) {
        val source = FakeDataSource()
        val vm = viewModel(source)
        val collector = launchCollect(vm)

        vm.setActive(false)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)

        assertTrue(source.requests.isEmpty())
        collector.cancel()
    }

    @Test
    fun `a settled viewport in the band is queried once`() = runTest(scheduler) {
        val source = FakeDataSource()
        val vm = viewModel(source)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)

        assertEquals(1, source.requests.size)
        collector.cancel()
    }

    /** A pan re-queries; re-reporting the *same* viewport does not. */
    @Test
    fun `a new viewport re-queries and an identical one does not`() = runTest(scheduler) {
        val source = FakeDataSource()
        val vm = viewModel(source)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)

        vm.onViewportSettled(viewport.copy(center = GeoPoint(47.7, -122.3)))
        advanceTimeBy(1)
        assertEquals(2, source.requests.size)
        collector.cancel()
    }

    /** The cadence the rest of the app's live-ETA surfaces use. */
    @Test
    fun `the query repeats on the refresh period`() = runTest(scheduler) {
        val source = FakeDataSource()
        val vm = viewModel(source)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)

        advanceTimeBy(NEARBY_REFRESH_PERIOD_MS + 1)
        assertEquals(2, source.requests.size)
        collector.cancel()
    }

    /**
     * A pan must not empty the drawer. The sheet gates its visibility on having rows, so re-emitting
     * Loading for the new viewport would retract the drawer and slide it back up a request later —
     * once per pan. The previous response is held until the new one lands.
     */
    @Test
    fun `a pan keeps the previous rows instead of reverting to loading`() = runTest(scheduler) {
        val source = FakeDataSource()
        val vm = viewModel(source)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)

        val loaded = NearbyArrivalsResult.Loaded(nearbyArrivals())
        source.result = loaded
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        assertTrue(vm.state.value is NearbyArrivalsUiState.Loaded)

        // A new viewport, whose response hasn't arrived yet: the state must still be Loaded.
        source.result = NearbyArrivalsResult.Failed(SocketTimeoutException("in flight"))
        vm.onViewportSettled(viewport.copy(center = GeoPoint(47.62, -122.33)))
        advanceTimeBy(1)
        assertTrue(vm.state.value is NearbyArrivalsUiState.Loaded)
        collector.cancel()
    }

    /** Leaving the band drops the held rows, so re-entering elsewhere can't flash the old centre. */
    @Test
    fun `leaving the band clears the held rows`() = runTest(scheduler) {
        val source = FakeDataSource(NearbyArrivalsResult.Loaded(nearbyArrivals()))
        val vm = viewModel(source)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        assertTrue(vm.state.value is NearbyArrivalsUiState.Loaded)

        vm.onStopBand(StopBand.FULL)
        advanceTimeBy(1)
        assertEquals(NearbyArrivalsUiState.Idle, vm.state.value)

        // Back into the band with a response not yet in: Loading, not the previous centre's rows.
        source.result = NearbyArrivalsResult.Failed(SocketTimeoutException("in flight"))
        vm.onStopBand(StopBand.ROUTES)
        advanceTimeBy(1)
        assertEquals(NearbyArrivalsUiState.Loading, vm.state.value)
        collector.cancel()
    }

    // --- unsupported vs transient -----------------------------------------------------------------

    /** A 404 is durable: the state says so and the poll stops rather than hammering the server. */
    @Test
    fun `a 404 marks the region unsupported and stops querying`() = runTest(scheduler) {
        val support = NearbyArrivalsSupport()
        val source = FakeDataSource(NearbyArrivalsResult.Unsupported)
        val vm = viewModel(source, support)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)

        assertEquals(NearbyArrivalsUiState.Unsupported, vm.state.value)
        assertTrue(support.isKnownUnsupported(endpoint))

        advanceTimeBy(NEARBY_REFRESH_PERIOD_MS * 3)
        assertEquals(1, source.requests.size)
        collector.cancel()
    }

    /** Once recorded, a later viewport in the same region doesn't re-probe within the process. */
    @Test
    fun `a region already known unsupported is not queried again`() = runTest(scheduler) {
        val support = NearbyArrivalsSupport().apply { recordAbsent(endpoint) }
        val source = FakeDataSource()
        val vm = viewModel(source, support)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)

        assertTrue(source.requests.isEmpty())
        assertEquals(NearbyArrivalsUiState.Unsupported, vm.state.value)
        collector.cancel()
    }

    /** A transient failure must never be mistaken for "unsupported": it retries on the next poll. */
    @Test
    fun `a transient failure keeps polling and does not disable the region`() = runTest(scheduler) {
        val support = NearbyArrivalsSupport()
        val source = FakeDataSource(NearbyArrivalsResult.Failed(SocketTimeoutException("slow")))
        val vm = viewModel(source, support)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        advanceTimeBy(NEARBY_REFRESH_PERIOD_MS + 1)

        assertEquals(2, source.requests.size)
        assertFalse(support.isKnownUnsupported(endpoint))
        collector.cancel()
    }

    // --- isEndpointAbsent -------------------------------------------------------------------------

    @Test
    fun `only an http 404 reads as an absent endpoint`() {
        assertTrue(isEndpointAbsent(http(404)))

        assertFalse(isEndpointAbsent(http(500)))
        assertFalse(isEndpointAbsent(http(401)))
        assertFalse(isEndpointAbsent(http(403)))
        // An OBA envelope code of 404 arrives inside a well-formed 200; it is not the transport
        // saying the action is unmapped, so it stays transient.
        assertFalse(isEndpointAbsent(ObaApiException(404)))
        assertFalse(isEndpointAbsent(IOException("offline")))
        assertFalse(isEndpointAbsent(SocketTimeoutException("slow")))
    }

    /**
     * The verdict is about the *server*, so it is keyed by the OBA base URL rather than by
     * `Region.id`: a directory refresh can repoint an existing region at another deployment, and a
     * deep-link-added region is a host the directory never named — either would inherit the other's
     * 404 under an id key and lose the drawer where it in fact works.
     */
    @Test
    fun `the unsupported verdict is scoped to the deployment that answered`() {
        val support = NearbyArrivalsSupport()
        support.recordAbsent(endpoint)
        assertTrue(support.isKnownUnsupported(endpoint))
        assertFalse(support.isKnownUnsupported("https://api.tampa.onebusaway.org/"))
        assertFalse(support.isKnownUnsupported(null))
    }

    // --- switching regions ------------------------------------------------------------------------

    /**
     * The server is a query input, not a fact read once: switching regions must re-ask, even though
     * neither the camera nor the sheet moved. Keyed on the endpoint alone, so an unrelated region
     * field can't re-fire it.
     */
    @Test
    fun `switching regions re-queries the new deployment`() = runTest(scheduler) {
        val source = FakeDataSource(NearbyArrivalsResult.Loaded(nearbyArrivals()))
        val regionRepo = regions()
        val vm = viewModel(source, regionRepo = regionRepo)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)

        // A field that isn't the endpoint: same deployment, so nothing to re-ask.
        regionRepo.emit(region(id = 1, obaBaseUrl = endpoint, twitterUrl = "https://example.test"))
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)

        regionRepo.emit(region(id = 2, obaBaseUrl = "https://api.tampa.onebusaway.org/"))
        advanceTimeBy(1)
        assertEquals(2, source.requests.size)
        collector.cancel()
    }

    /**
     * Rows are held across a *pan* so the drawer updates in place — but rows from another deployment
     * describe another city, so a region switch starts from Loading rather than showing the previous
     * region's bays over the new one.
     */
    @Test
    fun `a region switch drops the held rows instead of holding them through the load`() = runTest(scheduler) {
        val source = FakeDataSource(NearbyArrivalsResult.Loaded(nearbyArrivals()))
        val regionRepo = regions()
        val vm = viewModel(source, regionRepo = regionRepo)
        val collector = launchCollect(vm)

        vm.setActive(true)
        vm.onStopBand(StopBand.ROUTES)
        vm.onViewportSettled(viewport)
        advanceTimeBy(1)
        assertTrue(vm.state.value is NearbyArrivalsUiState.Loaded)

        // Never answers, so whatever the switch emitted first is what stays observable.
        source.result = NearbyArrivalsResult.Failed(SocketTimeoutException("slow"))
        regionRepo.emit(region(id = 2, obaBaseUrl = "https://api.tampa.onebusaway.org/"))
        advanceTimeBy(1)

        assertEquals(NearbyArrivalsUiState.Loading, vm.state.value)
        collector.cancel()
    }
}
