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
package org.onebusaway.android.map

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.OnDemandGeometryCache
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.rule
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandProbeControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private data class NearRequest(val point: GeoPoint, val radius: Int, val detail: String)

    private class FakeDataSource(var near: OnDemandResult<List<OnDemandService>>) : OnDemandDataSource {
        val nearRequests = mutableListOf<NearRequest>()
        val geometryRequests = mutableListOf<String>()
        var geometry: OnDemandResult<OnDemandService> = OnDemandResult.Loaded(service(areas = listOf(area())))

        /** When set, the geometry fetch waits on it — a slow server. */
        var geometryGate: CompletableDeferred<Unit>? = null
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String): OnDemandResult<List<OnDemandService>> {
            nearRequests += NearRequest(point, radiusMeters, geometryDetail)
            return near
        }
        override suspend fun service(id: String, geometryDetail: String): OnDemandResult<OnDemandService> {
            geometryRequests += id
            geometryGate?.await()
            return geometry
        }
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    }

    private val endpoint = "https://maglev.example.org/"
    private val camera = MutableSharedFlow<CameraSnapshot>(replay = 1)
    private val fixes = MutableStateFlow<RiderFix?>(null)
    private val enabled = MutableStateFlow(true)
    private val deployment = MutableStateFlow<String?>(endpoint)
    private val support = OnDemandSupport()
    private var nowMs = instant("2026-03-10T14:00:00-04:00").toEpochMilli()
    private val clock = TimeProvider { nowMs }

    private val centre = GeoPoint(45.05, -85.1)

    // 0.02° of latitude is ~2.2 km: street level. 0.1° is ~11 km: region level.
    private fun street(center: GeoPoint = centre) = CameraSnapshot(center, 15.0, 0.02, 0.03, GeoPoint(center.latitude - 0.01, center.longitude - 0.015), GeoPoint(center.latitude + 0.01, center.longitude + 0.015))
    private fun region(center: GeoPoint = centre) = street(center).copy(zoom = 11.0, latSpan = 0.1, lonSpan = 0.15)

    /** [centre] shifted [meters] north. */
    private fun north(meters: Double, from: GeoPoint = centre) = GeoPoint(from.latitude + meters / 111_194.9, from.longitude)

    private val insideService = service(areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT)
    private val nearbyService = service(id = "CC_CC2", name = "Medical Trips", areas = listOf(area(distance = 900.0, nearest = north(900.0))), matchReason = OnDemandMatchReason.AREA_NEARBY)

    private fun controller(source: FakeDataSource, scope: kotlinx.coroutines.CoroutineScope, cache: OnDemandGeometryCache = OnDemandGeometryCache(source)) = OnDemandProbeController(camera, fixes, enabled, deployment, source, support, cache, clock, scope)

    @Test
    fun `a settled camera probes the centre with radius 5000 and no geometry`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)

        assertEquals(listOf(NearRequest(centre, 5_000, "none")), source.nearRequests)
        val bar = subject.dockState.value as OnDemandDockState.Bar
        assertEquals(ProbeSource.MapCenter, bar.probe.source)
        assertEquals("CC_CC1", bar.matches.single().service.id)
        assertEquals(OnDemandZoomLevel.STREET, subject.zoomLevel.value)

        camera.emit(region())
        advanceTimeBy(1)
        assertTrue(subject.dockState.value is OnDemandDockState.Card)
        subject.stop()
    }

    @Test
    fun `moving under 100 m does not re-probe but 150 m does`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        camera.emit(street(north(60.0)))
        advanceTimeBy(1)
        assertEquals(1, source.nearRequests.size)
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `the first rider fix takes over from the map centre and a later 150 m move re-probes`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        // 60 m is under the move threshold, so only the first-fix trigger can probe; it also lands in
        // another 3-decimal cache cell than the centre (45.05054 vs 45.05), so the probe is a request.
        fixes.value = RiderFix(north(60.0), accuracyMeters = 12f)
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)
        assertEquals(north(60.0), source.nearRequests.last().point)
        assertEquals(ProbeSource.Rider, (subject.dockState.value as OnDemandDockState.Bar).probe.source)

        fixes.value = RiderFix(north(60.0 + 200.0), accuracyMeters = 200f)
        advanceTimeBy(1)
        assertEquals("a 200 m accuracy fix is ignored", 2, source.nearRequests.size)
        fixes.value = RiderFix(north(60.0 + 150.0), accuracyMeters = 20f)
        advanceTimeBy(1)
        assertEquals(3, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `losing the rider fix re-probes at the map centre`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        fixes.value = RiderFix(north(40.0), accuracyMeters = 10f)
        advanceTimeBy(1)
        assertEquals(ProbeSource.Rider, (subject.dockState.value as OnDemandDockState.Bar).probe.source)

        fixes.value = null
        advanceTimeBy(1)
        assertEquals(ProbeSource.MapCenter, (subject.dockState.value as OnDemandDockState.Bar).probe.source)
        assertEquals(centre, source.nearRequests.last().point)
        subject.stop()
    }

    @Test
    fun `a return to a probed point within ten minutes is answered from the cache`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        camera.emit(street())
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)

        nowMs += 11 * 60_000
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        assertEquals("expired after ten minutes", 3, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `at nextChangeInstant the dock recomputes from the cached response without a request`() = runTest {
        nowMs = instant("2026-03-10T16:39:30-04:00").toEpochMilli()
        // Real-time booking (no pickup booking rule), so the service is open until the window ends at
        // 16:40; a same-day rule's notice cutoff would already have passed and read Unknown (§2.5).
        val realTimeService = service(rules = listOf(rule(bookingRuleId = null)), areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT)
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(realTimeService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertEquals(
            OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")),
            (subject.dockState.value as OnDemandDockState.Bar).matches.single().availability.status
        )

        nowMs = instant("2026-03-10T16:40:01-04:00").toEpochMilli()
        advanceTimeBy(32_000)
        assertTrue((subject.dockState.value as OnDemandDockState.Bar).matches.single().availability.status is OnDemandStatus.OpensAt)
        assertEquals(1, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `probeExact never reads the rider cache`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)

        source.near = OnDemandResult.Loaded(listOf(nearbyService))
        val exact = requireNotNull(subject.probeExact(north(20.0)))
        assertEquals(2, source.nearRequests.size)
        assertEquals("CC_CC2", exact.single().service.id)
        // The exact cache is its own: the same exact point again costs nothing, the rider point still reads inside.
        subject.probeExact(north(20.0))
        assertEquals(2, source.nearRequests.size)
        assertEquals("CC_CC1", (subject.dockState.value as OnDemandDockState.Bar).matches.single().service.id)
        subject.stop()
    }

    @Test
    fun `a failed probe far from the last state hides the dock and a near one keeps it`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)

        source.near = OnDemandResult.Failed(IOException("slow"))
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        assertTrue("within 100 m of the state's probe point? no — 150 m", subject.dockState.value is OnDemandDockState.Hidden)

        source.near = OnDemandResult.Loaded(listOf(insideService))
        camera.emit(street(north(400.0)))
        advanceTimeBy(1)
        assertTrue(subject.dockState.value is OnDemandDockState.Bar)
        val kept = subject.dockState.value

        // Foreground (trigger 3) re-probes the same point regardless of the failure; with the cache
        // expired it reaches the failing server.
        source.near = OnDemandResult.Failed(IOException("slow"))
        nowMs += 11 * 60_000
        subject.onForeground()
        advanceTimeBy(1)
        assertEquals(4, source.nearRequests.size)
        assertEquals("0 m from the state's probe point keeps it", kept, subject.dockState.value)

        // A failure is not itself a trigger: a 50 m settle is under the move threshold, so it must not
        // probe even though the last attempt failed.
        camera.emit(street(north(450.0)))
        advanceTimeBy(1)
        assertEquals("a 50 m settle after a failure does not probe", 4, source.nearRequests.size)
        assertEquals(kept, subject.dockState.value)

        // 150 m from the last probe point is a real trigger and hides the dock on this failure.
        camera.emit(street(north(550.0)))
        advanceTimeBy(1)
        assertEquals(5, source.nearRequests.size)
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        subject.stop()
    }

    @Test
    fun `after a failed probe the foreground retries the same point`() = runTest {
        val source = FakeDataSource(OnDemandResult.Failed(IOException("slow")))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)

        source.near = OnDemandResult.Loaded(listOf(insideService))
        subject.onForeground()
        advanceTimeBy(1)
        assertEquals(listOf(centre, centre), source.nearRequests.map { it.point })
        assertTrue(subject.dockState.value is OnDemandDockState.Bar)
        subject.stop()
    }

    @Test
    fun `a state kept through a failure still recomputes at nextChangeInstant`() = runTest {
        nowMs = instant("2026-03-10T16:39:00-04:00").toEpochMilli()
        val realTimeService = service(rules = listOf(rule(bookingRuleId = null)), areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT)
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(realTimeService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)

        // The first fix probes 60 m away (another cache cell) and fails: within 100 m, so the state is kept.
        source.near = OnDemandResult.Failed(IOException("slow"))
        fixes.value = RiderFix(north(60.0), accuracyMeters = 10f)
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)
        assertTrue((subject.dockState.value as OnDemandDockState.Bar).matches.single().availability.status is OnDemandStatus.OpenNow)

        nowMs = instant("2026-03-10T16:40:01-04:00").toEpochMilli()
        advanceTimeBy(62_000)
        assertTrue((subject.dockState.value as OnDemandDockState.Bar).matches.single().availability.status is OnDemandStatus.OpensAt)
        assertEquals(2, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `a 404 records absence and hides`() = runTest {
        val source = FakeDataSource(OnDemandResult.Unsupported)
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertTrue(support.isKnownUnsupported(endpoint))
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        camera.emit(street(north(500.0)))
        advanceTimeBy(1)
        assertEquals(1, source.nearRequests.size)
        assertNull(subject.probeExact(centre))
        subject.stop()
    }

    @Test
    fun `a deployment change hides, re-probes the new server and discards a geometry answer for the old`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        source.geometryGate = CompletableDeferred()
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertEquals(listOf("CC_CC1"), source.geometryRequests)

        deployment.value = "https://other.example.org/"
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)
        // The new server is asked for its own geometry; the old fetch was cancelled with the old state.
        assertEquals(listOf("CC_CC1", "CC_CC1"), source.geometryRequests)
        assertTrue(subject.edges.value.isEmpty())
        source.geometryGate?.complete(Unit)
        advanceTimeBy(1)
        assertEquals("https://other.example.org/", requireNotNull(subject.result.value).deployment)
        assertNotNull(subject.edges.value["CC_CC1"])
        subject.stop()
    }

    @Test
    fun `near-edge geometry is fetched for matched services and edges follow the probe point`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street(GeoPoint(45.0999, -85.1)))
        advanceTimeBy(1)
        val edge = requireNotNull(subject.edges.value["CC_CC1"])
        assertEquals(11.1, edge.distanceMeters, 0.3)
        assertNotNull(subject.geometry.value["CC_CC1"])

        camera.emit(street(GeoPoint(45.05, -85.1)))
        advanceTimeBy(1)
        assertEquals(1, source.geometryRequests.size)
        assertEquals(5560.0, requireNotNull(subject.edges.value["CC_CC1"]).distanceMeters, 3.0)
        subject.stop()
    }

    @Test
    fun `suppression hides the dock and lifting it restores the state`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        subject.setSuppressed(true)
        advanceTimeBy(1)
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        subject.setSuppressed(false)
        advanceTimeBy(1)
        assertTrue(subject.dockState.value is OnDemandDockState.Bar)
        assertEquals(1, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `probeExact works with the layer off and after stop`() = runTest {
        enabled.value = false
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        assertTrue("the layer off runs no dock probe", source.nearRequests.isEmpty())

        val exact = requireNotNull(subject.probeExact(centre))
        assertEquals("CC_CC1", exact.single().service.id)
        assertEquals(listOf(NearRequest(centre, 5_000, "none")), source.nearRequests)

        subject.stop()
        val afterStop = requireNotNull(subject.probeExact(north(20.0)))
        assertEquals("CC_CC1", afterStop.single().service.id)
        assertEquals(2, source.nearRequests.size)
    }

    @Test
    fun `the layer preference off hides the dock`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        enabled.value = false
        advanceTimeBy(1)
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        subject.stop()
    }
}
