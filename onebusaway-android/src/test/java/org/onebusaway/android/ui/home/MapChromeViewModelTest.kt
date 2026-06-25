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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.home.map.MapChromeViewModel

/**
 * Unit tests for [MapChromeViewModel]'s reactive chrome-gate derivation (migrated from HomeViewModelTest
 * when the map chrome became its own self-wired feature module). The gates derive from prefs + region.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapChromeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the zoom-controls preference flips the gate reactively`() = runTest {
        val prefs = FakePreferencesRepository(observeValue = false) // start gates off
        val vm = MapChromeViewModel(prefs, FakeRegionRepository())
        advanceUntilIdle()
        assertFalse(vm.state.value.zoomControls)

        prefs.setBoolean(R.string.preference_key_show_zoom_controls, true)
        advanceUntilIdle()
        assertTrue(vm.state.value.zoomControls)
    }

    @Test
    fun `the bikeshare-layer preference flips the active tint reactively`() = runTest {
        val prefs = FakePreferencesRepository(observeValue = false)
        // A custom OTP URL makes bikeshare enabled (the layers FAB shows); the visible pref drives active.
        prefs.setString(R.string.preference_key_otp_api_url, "https://otp.example.org")
        prefs.setBoolean(R.string.preference_key_layer_bikeshare_visible, true)
        val vm = MapChromeViewModel(prefs, FakeRegionRepository())
        advanceUntilIdle()
        assertTrue(vm.state.value.layersFab)
        assertTrue(vm.state.value.bikeshareActive)

        prefs.setBoolean(R.string.preference_key_layer_bikeshare_visible, false)
        advanceUntilIdle()
        assertFalse(vm.state.value.bikeshareActive)
        assertTrue(vm.state.value.layersFab) // still enabled, just not active
    }

    @Test
    fun `the layers FAB follows bikeshare-enabled derived from the OTP URL`() = runTest {
        val prefs = FakePreferencesRepository(observeValue = false)
        val vm = MapChromeViewModel(prefs, FakeRegionRepository())
        advanceUntilIdle()
        assertFalse(vm.state.value.layersFab) // no region, no custom OTP URL

        prefs.setString(R.string.preference_key_otp_api_url, "https://otp.example.org")
        advanceUntilIdle()
        assertTrue(vm.state.value.layersFab)
    }

    @Test
    fun `a region supporting OTP bikeshare enables the layers FAB`() = runTest {
        val regions = FakeRegionRepository()
        val vm = MapChromeViewModel(FakePreferencesRepository(observeValue = false), regions)
        advanceUntilIdle()
        assertFalse(vm.state.value.layersFab)

        regions.emit(region(1, supportsOtpBikeshare = true))
        advanceUntilIdle()
        assertTrue(vm.state.value.layersFab)
    }
}
