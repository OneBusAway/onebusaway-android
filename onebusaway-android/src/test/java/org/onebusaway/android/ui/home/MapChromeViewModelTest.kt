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
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.FakeDemoModeState
import org.onebusaway.android.map.MapFlavourCapabilities
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

    /** Neither on-demand support nor a basemap choice matters to a test unless it says otherwise. */
    private val noExtraSurfaces = MapFlavourCapabilities(hasBasemapChoice = false)

    /**
     * A prefs fake with the rental button's own visibility switched on.
     *
     * [FakePreferencesRepository] seeds an un-set boolean from its `observeValue` rather than from the
     * default the caller declares, so the button-visible preference — which ships on — would otherwise
     * read off here and hide the layers FAB in every test that asserts on it.
     */
    private fun rentalButtonShown() = FakePreferencesRepository(observeValue = false).apply {
        setBoolean(R.string.preference_key_show_rental_button, true)
    }

    @Test
    fun `the zoom-controls preference flips the gate reactively`() = runTest {
        val prefs = rentalButtonShown() // start gates off
        val vm = MapChromeViewModel(prefs, FakeRegionRepository(), FakeDemoModeState(), OnDemandSupport(), noExtraSurfaces)
        advanceUntilIdle()
        assertFalse(vm.state.value.zoomControls)

        prefs.setBoolean(R.string.preference_key_show_zoom_controls, true)
        advanceUntilIdle()
        assertTrue(vm.state.value.zoomControls)
    }

    @Test
    fun `the layers FAB follows bikeshare-enabled derived from the OTP URL`() = runTest {
        val prefs = rentalButtonShown()
        val vm = MapChromeViewModel(prefs, FakeRegionRepository(), FakeDemoModeState(), OnDemandSupport(), noExtraSurfaces)
        advanceUntilIdle()
        assertFalse(vm.state.value.layersFab) // no region, no custom OTP URL

        prefs.setString(R.string.preference_key_otp_api_url, "https://otp.example.org")
        advanceUntilIdle()
        assertTrue(vm.state.value.layersFab)
    }

    @Test
    fun `demo mode enables the layers FAB with no region at all`() = runTest {
        // The scripted tutorial's micromobility step has to have a button to point at, wherever the
        // rider actually is and whether or not their region publishes bikeshare (#2164).
        val demo = FakeDemoModeState()
        val vm = MapChromeViewModel(rentalButtonShown(), FakeRegionRepository(), demo, OnDemandSupport(), noExtraSurfaces)
        advanceUntilIdle()
        assertFalse(vm.state.value.layersFab)

        demo.set(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.layersFab)

        demo.set(false)
        advanceUntilIdle()
        assertFalse("leaving the tour must take the button away again", vm.state.value.layersFab)
    }

    @Test
    fun `a region supporting OTP bikeshare enables the layers FAB`() = runTest {
        val regions = FakeRegionRepository()
        val vm = MapChromeViewModel(rentalButtonShown(), regions, FakeDemoModeState(), OnDemandSupport(), noExtraSurfaces)
        advanceUntilIdle()
        assertFalse(vm.state.value.layersFab)

        regions.emit(region(1, supportsOtpBikeshare = true))
        advanceUntilIdle()
        assertTrue(vm.state.value.layersFab)
    }

    @Test
    fun `without bikeshare the layers button still shows when on-demand may be supported or a basemap choice exists`() = runTest {
        val prefs = rentalButtonShown()
        val endpoint = "https://maglev.example.org/"
        val regions = FakeRegionRepository(region(id = 1, obaBaseUrl = endpoint))
        val support = OnDemandSupport()
        val vm = MapChromeViewModel(prefs, regions, FakeDemoModeState(), support, noExtraSurfaces)
        advanceUntilIdle()
        assertTrue(vm.state.value.layersFab)

        // PF-13: the button follows the observable absent set, so a 404 recorded after the first
        // emission must hide it on its own — no refresh call exists to ask for it any more.
        support.recordAbsent(endpoint)
        advanceUntilIdle()
        assertFalse(vm.state.value.layersFab)

        val google = MapChromeViewModel(prefs, regions, FakeDemoModeState(), support, MapFlavourCapabilities(hasBasemapChoice = true))
        advanceUntilIdle()
        assertTrue(google.state.value.layersFab)
    }
}
