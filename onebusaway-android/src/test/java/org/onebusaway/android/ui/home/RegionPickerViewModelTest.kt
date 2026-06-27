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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.RegionState
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule

/**
 * Unit tests for [RegionPickerViewModel]: it exposes the repository's [RegionState.NeedsManualChoice] as the
 * [RegionPickerViewModel.picker] list and resolves it via [RegionPickerViewModel.choose] — the forced picker
 * is now driven entirely off the repository, not HomeViewModel/HomeUiState.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegionPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `exposes the regions while resolution needs a manual choice`() = runTest {
        val regions = listOf(region(1), region(2))
        val repo = FakeRegionRepository().apply { emitState(RegionState.NeedsManualChoice(regions)) }
        val vm = RegionPickerViewModel(repo)
        advanceUntilIdle()

        assertEquals(regions, vm.picker.value)
    }

    @Test
    fun `picker is null when no manual choice is needed`() = runTest {
        val vm = RegionPickerViewModel(FakeRegionRepository()) // initial state is Active(null)
        advanceUntilIdle()

        assertNull(vm.picker.value)
    }

    @Test
    fun `choose applies the region and clears the picker`() = runTest {
        val regions = listOf(region(1), region(2))
        val repo = FakeRegionRepository().apply { emitState(RegionState.NeedsManualChoice(regions)) }
        val vm = RegionPickerViewModel(repo)
        advanceUntilIdle()

        val chosen = regions[1]
        vm.choose(chosen)
        advanceUntilIdle()

        // choose() drives the repository to Active(chosen), so the picker reactively returns to null.
        assertEquals(listOf(chosen), repo.chosen)
        assertNull(vm.picker.value)
    }
}
