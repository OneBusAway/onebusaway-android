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
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.nav.NavRoutes

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandServiceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FakeDataSource(var result: OnDemandResult<OnDemandService>) : OnDemandDataSource {
        val requested = mutableListOf<String>()
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun service(id: String): OnDemandResult<OnDemandService> {
            requested += id
            return result
        }
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    }

    private val service = OnDemandService("5088_77652", "5088", "5088_77652", "DOT Paratransit", OnDemandServiceKind.ZONE, agencyTimezone = "America/Los_Angeles")

    private fun viewModel(source: OnDemandDataSource) = OnDemandServiceViewModel(SavedStateHandle(mapOf(NavRoutes.ARG_ONDEMAND_SERVICE_ID to "5088_77652")), source)

    @Test
    fun `loads the service named by the nav arg`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(service))
        val vm = viewModel(source)
        assertEquals(listOf("5088_77652"), source.requested)
        val content = vm.state.value as OnDemandServiceUiState.Content
        assertEquals("DOT Paratransit", content.service.name)
    }

    @Test
    fun `an OBA 404 is not found and a transport failure is an error`() = runTest {
        assertEquals(OnDemandServiceUiState.NotFound, viewModel(FakeDataSource(OnDemandResult.Failed(ObaApiException(404)))).state.value)
        assertEquals(OnDemandServiceUiState.Error, viewModel(FakeDataSource(OnDemandResult.Failed(IOException("offline")))).state.value)
        assertEquals(OnDemandServiceUiState.Error, viewModel(FakeDataSource(OnDemandResult.Unsupported)).state.value)
    }

    @Test
    fun `retry re-requests`() = runTest {
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
        override suspend fun service(id: String): OnDemandResult<OnDemandService> = CompletableDeferred<OnDemandResult<OnDemandService>>().also { pending += it }.await()
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    }

    @Test
    fun `a slow earlier load cannot overwrite a retry`() = runTest {
        val source = GatedDataSource()
        val vm = viewModel(source)
        vm.retry()
        source.pending[1].complete(OnDemandResult.Loaded(service))
        source.pending[0].complete(OnDemandResult.Failed(IOException("offline")))
        assertTrue(vm.state.value is OnDemandServiceUiState.Content)
    }
}
