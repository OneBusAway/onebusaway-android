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
package org.onebusaway.android.ui.home.widealert

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule

private class FakeWideAlertsRepository(private val alerts: List<WideAlert>) : WideAlertsRepository {
    override fun wideAlerts(regionId: String): Flow<WideAlert> = flow {
        alerts.forEach { emit(it) }
    }
}

/**
 * Unit tests for [WideAlertViewModel] (migrated from HomeViewModelTest when the wide alert became its own
 * self-wired feature module). It streams the current region's alerts and clears on dismiss.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WideAlertViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a wide alert surfaces for the current region and is cleared on dismiss`() = runTest {
        val alert = WideAlert("Title", "Message", "https://example.org")
        val regions = FakeRegionRepository()
        val vm = WideAlertViewModel(FakeWideAlertsRepository(listOf(alert)), regions)
        assertNull(vm.wideAlert.value)

        regions.emit(region(1)) // a current region streams its wide alerts
        advanceUntilIdle()
        assertEquals(alert, vm.wideAlert.value)

        vm.dismiss()
        assertNull(vm.wideAlert.value)
    }
}
