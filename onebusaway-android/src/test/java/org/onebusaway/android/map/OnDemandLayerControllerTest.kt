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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.GeoPoint

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandLayerControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FakeDataSource(var result: OnDemandResult<List<OnDemandService>>) : OnDemandDataSource {
        val requests = mutableListOf<CameraSnapshot>()
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> {
            requests += viewport
            return result
        }
        override suspend fun service(id: String, geometryDetail: String): OnDemandResult<OnDemandService> = OnDemandResult.Failed(IOException("unused"))
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Failed(IOException("unused"))
    }

    private class FakeDemoMode : DemoModeState {
        override val active: StateFlow<Boolean> = MutableStateFlow(false)
        override val isActive: Boolean get() = active.value
    }

    private val endpoint = "https://maglev.example.org/"
    private val camera = MutableSharedFlow<CameraSnapshot>(replay = 1)
    private val renderState = MapRenderState()
    private val prefs = FakePreferencesRepository()
    private val support = OnDemandSupport()
    private val regions = FakeRegionRepository(region(id = 1, obaBaseUrl = endpoint))

    private val viewport = CameraSnapshot(
        center = GeoPoint(38.83, -77.05),
        zoom = 12.0,
        latSpan = 0.1,
        lonSpan = 0.1,
        southWest = GeoPoint(38.78, -77.10),
        northEast = GeoPoint(38.88, -77.00)
    )

    private val square = listOf(GeoPoint(38.8, -77.1), GeoPoint(38.8, -77.0), GeoPoint(38.9, -77.0), GeoPoint(38.9, -77.1), GeoPoint(38.8, -77.1))

    private fun service(id: String = "5088_77652", polygons: Int = 1) = OnDemandService(
        id = id,
        agencyId = "5088",
        routeId = id,
        name = "DOT Paratransit",
        kind = OnDemandServiceKind.ZONE,
        areas = listOf(ServiceArea("5088_area_1449", null, null, GeoPoint(38.8, -77.1), GeoPoint(38.9, -77.0), List(polygons) { listOf(square) }, null, null)),
        routeColor = 0xFF112233.toInt()
    )

    private fun controller(
        source: OnDemandDataSource,
        scope: kotlinx.coroutines.CoroutineScope,
        regionRepository: FakeRegionRepository = regions
    ) = OnDemandLayerController(camera, renderState, source, support, prefs, regionRepository, FakeDemoMode(), scope)

    @Test
    fun `a settled viewport loads zones with the route colour`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)

        assertEquals(1, source.requests.size)
        val zones = renderState.snapshot.value.onDemandZones
        assertEquals(1, zones.size)
        assertEquals("5088_77652", zones[0].serviceId)
        assertEquals(0xFF112233.toInt(), zones[0].color)
        assertEquals(square, zones[0].rings[0])
        subject.stop()
    }

    @Test
    fun `a multipolygon area becomes one zone per polygon`() {
        assertEquals(2, zonePolygons(listOf(service(polygons = 2))).size)
    }

    @Test
    fun `the preference off clears zones and stops requesting`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)
        assertEquals(1, renderState.snapshot.value.onDemandZones.size)

        prefs.setBoolean(R.string.preference_key_show_ondemand_zones, false)
        advanceTimeBy(1)
        assertTrue(renderState.snapshot.value.onDemandZones.isEmpty())
        camera.emit(viewport.copy(center = GeoPoint(38.84, -77.06)))
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)
        subject.stop()
    }

    @Test
    fun `unsupported is recorded and not re-probed`() = runTest {
        val source = FakeDataSource(OnDemandResult.Unsupported)
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)
        assertTrue(support.isKnownUnsupported(endpoint))
        assertTrue(renderState.snapshot.value.onDemandZones.isEmpty())

        camera.emit(viewport.copy(center = GeoPoint(38.84, -77.06)))
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)
        subject.stop()
    }

    @Test
    fun `a transient failure keeps the previous zones and does not disable the region`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)

        source.result = OnDemandResult.Failed(IOException("slow"))
        camera.emit(viewport.copy(center = GeoPoint(38.84, -77.06)))
        advanceTimeBy(1)
        assertEquals(2, source.requests.size)
        assertEquals(1, renderState.snapshot.value.onDemandZones.size)
        assertFalse(support.isKnownUnsupported(endpoint))
        subject.stop()
    }

    @Test
    fun `switching regions re-queries the new deployment`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)

        regions.emit(region(id = 2, obaBaseUrl = "https://other.example.org/"))
        advanceTimeBy(1)
        assertEquals(2, source.requests.size)
        subject.stop()
    }

    @Test
    fun `a custom API URL with no region loads zones and keys support on that URL`() = runTest {
        val customUrl = "https://custom.example.org/"
        prefs.setString(R.string.preference_key_oba_api_url, customUrl)
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope, regionRepository = FakeRegionRepository(null))
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)

        assertEquals(1, source.requests.size)
        assertEquals(1, renderState.snapshot.value.onDemandZones.size)

        source.result = OnDemandResult.Unsupported
        camera.emit(viewport.copy(center = GeoPoint(38.84, -77.06)))
        advanceTimeBy(1)
        assertTrue(support.isKnownUnsupported(customUrl))
        subject.stop()
    }

    @Test
    fun `a custom API URL takes the region's place as the deployment`() = runTest {
        support.recordAbsent(endpoint)
        prefs.setString(R.string.preference_key_oba_api_url, "https://custom.example.org/")
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)

        assertEquals(1, source.requests.size)
        assertEquals(1, renderState.snapshot.value.onDemandZones.size)
        subject.stop()
    }
}
