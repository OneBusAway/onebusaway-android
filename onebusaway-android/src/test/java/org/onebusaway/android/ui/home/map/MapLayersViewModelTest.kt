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
package org.onebusaway.android.ui.home.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.FakeDemoModeState
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.map.MapFlavourCapabilities
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MapLayersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val endpoint = "https://maglev.example.org/"

    /** The shipped defaults, set explicitly because the fake seeds un-set booleans from `observeValue`. */
    private fun defaultPrefs(bikeshare: Boolean = true) = FakePreferencesRepository(observeValue = false).apply {
        setBoolean(R.string.preference_key_show_ondemand_zones, true)
        setBoolean(R.string.preference_key_layer_bikeshare_visible, false)
        setBoolean(R.string.preference_key_layer_bikes_visible, true)
        setBoolean(R.string.preference_key_layer_scooters_visible, false)
        if (bikeshare) setString(R.string.preference_key_otp_api_url, "https://otp.example.org")
    }

    private fun viewModel(prefs: FakePreferencesRepository, support: OnDemandSupport = OnDemandSupport(), basemap: Boolean = true) = MapLayersViewModel(prefs, FakeRegionRepository(region(id = 1, obaBaseUrl = endpoint)), support, FakeDemoModeState(), MapFlavourCapabilities(hasBasemapChoice = basemap))

    @Test
    fun `google shows the basemap, maplibre does not`() = runTest {
        val google = viewModel(defaultPrefs())
        advanceUntilIdle()
        assertEquals(Basemap.STANDARD, google.state.value.basemap)
        val maplibre = viewModel(defaultPrefs(), basemap = false)
        advanceUntilIdle()
        assertNull(maplibre.state.value.basemap)
    }

    @Test
    fun `an unsupported deployment hides the transit group and no bikeshare hides rentals`() = runTest {
        val support = OnDemandSupport().apply { recordAbsent(endpoint) }
        val vm = viewModel(defaultPrefs(bikeshare = false), support)
        advanceUntilIdle()
        assertTrue(vm.state.value.transit.isEmpty())
        assertTrue(vm.state.value.rentals.isEmpty())
    }

    @Test
    fun `toggling bikes writes the layer preference and the master, and the badge counts it`() = runTest {
        val prefs = defaultPrefs()
        val vm = viewModel(prefs)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.enabledCount)
        vm.toggle(LayerTileId.BIKES)
        advanceUntilIdle()
        assertTrue(prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, false))
        assertTrue(prefs.getBoolean(R.string.preference_key_layer_bikes_visible, false))
        assertTrue(vm.state.value.rentals.first { it.id == LayerTileId.BIKES }.enabled)
        assertTrue(vm.state.value.differsFromDefaults)
        assertEquals(2, vm.state.value.enabledCount)

        vm.toggle(LayerTileId.BIKES)
        advanceUntilIdle()
        assertFalse(prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, true))
        assertFalse(vm.state.value.rentals.first { it.id == LayerTileId.BIKES }.enabled)
        // The bikes preference is now false where the default is true, so Reset stays offered.
        assertTrue(vm.state.value.differsFromDefaults)
        assertEquals(1, vm.state.value.enabledCount)
    }

    @Test
    fun `reset restores the defaults and the basemap`() = runTest {
        val prefs = defaultPrefs()
        val vm = viewModel(prefs)
        vm.setBasemap(Basemap.HYBRID)
        vm.toggle(LayerTileId.ON_DEMAND_ZONES)
        advanceUntilIdle()
        assertEquals(Basemap.HYBRID, vm.state.value.basemap)
        assertTrue(vm.state.value.differsFromDefaults)
        vm.reset()
        advanceUntilIdle()
        assertEquals(Basemap.STANDARD, vm.state.value.basemap)
        assertTrue(prefs.getBoolean(R.string.preference_key_show_ondemand_zones, false))
        assertFalse(vm.state.value.differsFromDefaults)
    }

    @Test
    fun `a 404 recorded after the first emission hides the transit group`() = runTest {
        val support = OnDemandSupport()
        val vm = viewModel(defaultPrefs(), support)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.transit.size)
        // No explicit refresh: the transit tile follows the observable absent set (PF-13), so this
        // alone must recompute the state — a one-shot isKnownUnsupported read would leave it stale.
        support.recordAbsent(endpoint)
        advanceUntilIdle()
        assertTrue(vm.state.value.transit.isEmpty())
    }
}
