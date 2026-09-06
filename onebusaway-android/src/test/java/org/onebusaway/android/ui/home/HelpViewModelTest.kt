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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.arrivals.arrivalDisplayDefault
import org.onebusaway.android.ui.home.help.HelpDialog
import org.onebusaway.android.ui.home.help.HelpViewModel

/**
 * Unit tests for [HelpViewModel]'s dialog-state transitions (migrated from HomeViewModelTest when help
 * became its own feature module), migration eligibility, and region-derived Twitter URL.
 */
class HelpViewModelTest {

    @Test
    fun `migration choice precedes release notes and persists the selected default`() {
        val prefs = FakePreferencesRepository().apply { setInt("whatsNewVer", 1) }
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        assertEquals(HelpDialog.ArrivalDisplay, vm.state.value.dialog)
        vm.chooseArrivalDisplayDefault(ArrivalDisplayMode.TIME)
        assertEquals(ArrivalDisplayMode.TIME, prefs.arrivalDisplayDefault())
        assertEquals(HelpDialog.WhatsNew, vm.state.value.dialog)
        vm.dismiss()
        vm.maybeShowTutorialOptOut()
        assertEquals(HelpDialog.TutorialOptOut, vm.state.value.dialog)
        vm.setTutorialsEnabled(false)
        vm.maybeShowStartup()
        assertEquals(HelpDialog.None, vm.state.value.dialog)
        val nextLaunch = viewModel(prefs)
        nextLaunch.maybeShowStartup()
        assertEquals(HelpDialog.None, nextLaunch.state.value.dialog)
    }

    @Test
    fun `dismissed migration stays eligible after release notes advance the version marker`() {
        val prefs = FakePreferencesRepository().apply { setInt("whatsNewVer", 154) }
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        assertEquals(HelpDialog.ArrivalDisplay, vm.state.value.dialog)
        vm.finishArrivalDisplayChoice()
        assertEquals(null, prefs.getString(ArrivalDisplayMode.PREFERENCE_KEY, null))
        prefs.setInt("whatsNewVer", 156)
        val nextLaunch = viewModel(prefs)
        nextLaunch.maybeShowStartup()
        assertEquals(HelpDialog.ArrivalDisplay, nextLaunch.state.value.dialog)
    }

    @Test
    fun `only recorded releases through 26_1 are eligible`() {
        for (version in listOf(1, 153, 154, 155, 156, 157)) {
            val prefs = FakePreferencesRepository().apply { setInt("whatsNewVer", version) }
            val vm = viewModel(prefs)
            vm.maybeShowStartup()
            assertEquals("Previous version $version", version <= 154, vm.state.value.dialog == HelpDialog.ArrivalDisplay)
        }
    }

    @Test
    fun `fresh installs never become migration candidates on later launches`() {
        val prefs = FakePreferencesRepository()
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        assertEquals(HelpDialog.WhatsNew, vm.state.value.dialog)
        assertEquals(BuildConfig.VERSION_CODE, prefs.getInt("whatsNewVer", 0))

        // Debug builds reuse an old versionCode; the recorded source must still exclude them.
        val nextLaunch = viewModel(prefs)
        nextLaunch.maybeShowStartup()
        assertEquals(HelpDialog.None, nextLaunch.state.value.dialog)
    }

    @Test
    fun `a default chosen in settings suppresses migration and existing dialogs are respected`() {
        val prefs = FakePreferencesRepository().apply {
            setInt("whatsNewVer", 1)
            setString(ArrivalDisplayMode.PREFERENCE_KEY, "time")
        }
        val vm = viewModel(prefs)
        vm.showLegend()
        vm.maybeShowStartup()
        assertEquals(HelpDialog.Legend, vm.state.value.dialog)
        vm.dismiss()
        vm.maybeShowStartup()
        assertEquals(HelpDialog.WhatsNew, vm.state.value.dialog)
    }

    private fun viewModel(
        prefs: FakePreferencesRepository = FakePreferencesRepository(),
        regionRepo: FakeRegionRepository = FakeRegionRepository()
    ) = HelpViewModel(prefs, regionRepo)

    @Test
    fun `showing the menu hides contact-us when a custom OBA API URL is set`() {
        val prefs = FakePreferencesRepository().apply {
            setString(R.string.preference_key_oba_api_url, "https://my.custom.oba")
        }
        val vm = viewModel(prefs = prefs)
        vm.showMenu()
        assertEquals(HelpDialog.Menu, vm.state.value.dialog)
        assertFalse(vm.state.value.showContactUs)
    }

    @Test
    fun `showing the menu shows contact-us with no custom API URL`() {
        val vm = viewModel()
        vm.showMenu()
        assertEquals(HelpDialog.Menu, vm.state.value.dialog)
        assertTrue(vm.state.value.showContactUs)
    }

    @Test
    fun `legend and what's-new transition the dialog, dismiss clears it`() {
        val vm = viewModel()
        vm.showLegend()
        assertEquals(HelpDialog.Legend, vm.state.value.dialog)
        vm.showWhatsNew()
        assertEquals(HelpDialog.WhatsNew, vm.state.value.dialog)
        vm.dismiss()
        assertEquals(HelpDialog.None, vm.state.value.dialog)
    }

    @Test
    fun `twitterUrl uses the current region's url when it has one`() {
        val vm = viewModel(regionRepo = FakeRegionRepository(region(1, twitterUrl = "https://x.com/sound_transit")))
        assertEquals("https://x.com/sound_transit", vm.twitterUrl())
    }

    @Test
    fun `twitterUrl falls back to the default when the region has none`() {
        val vm = viewModel(regionRepo = FakeRegionRepository(region(1)))
        assertEquals(HelpViewModel.TWITTER_URL, vm.twitterUrl())
    }

    @Test
    fun `twitterUrl falls back to the default when no region is set`() {
        val vm = viewModel(regionRepo = FakeRegionRepository(initial = null))
        assertEquals(HelpViewModel.TWITTER_URL, vm.twitterUrl())
    }
}
