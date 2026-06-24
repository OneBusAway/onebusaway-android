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
package org.onebusaway.android.ui.home.drawer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule

internal class FakeNavItemsRepository(
    var availability: NavItemAvailability =
        NavItemAvailability(showReminders = false, planTripAvailable = false, payFareAvailable = false)
) : NavItemsRepository {
    override fun availability(): NavItemAvailability = availability
}

/**
 * Unit tests for [NavDrawerViewModel]'s region/feature gating (migrated from HomeViewModelTest when the
 * drawer gating became its own self-wired feature module). The availability re-pulls on region change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavDrawerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `drawer gating is surfaced from the repository availability at init`() = runTest {
        val repo = FakeNavItemsRepository(
            NavItemAvailability(showReminders = true, planTripAvailable = true, payFareAvailable = false)
        )
        val availability = NavDrawerViewModel(repo, FakeRegionRepository()).availability.value
        assertTrue(availability.showReminders)
        assertTrue(availability.planTripAvailable)
        assertFalse(availability.payFareAvailable)
    }

    @Test
    fun `a region change refreshes the drawer gating from current availability`() = runTest {
        val repo = FakeNavItemsRepository(NavItemAvailability(false, false, false))
        val regions = FakeRegionRepository()
        val vm = NavDrawerViewModel(repo, regions)
        advanceUntilIdle()
        assertFalse(vm.availability.value.payFareAvailable)

        // The region now supports fare payment; a region change picks it up without a host push.
        repo.availability = NavItemAvailability(
            showReminders = false, planTripAvailable = false, payFareAvailable = true
        )
        regions.emit(region(1))
        advanceUntilIdle()

        assertTrue(vm.availability.value.payFareAvailable)
    }
}
