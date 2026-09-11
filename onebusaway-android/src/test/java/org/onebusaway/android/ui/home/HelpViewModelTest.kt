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
import org.onebusaway.android.ui.searchresults.SearchResultMode
import org.onebusaway.android.ui.searchresults.searchResultMode
import org.onebusaway.android.ui.tutorial.TutorialPrefs

/**
 * Unit tests for [HelpViewModel]'s dialog-state transitions (migrated from HomeViewModelTest when help
 * became its own feature module), migration eligibility, and region-derived Twitter URL.
 */
class HelpViewModelTest {

    @Test
    fun `search migration includes March and 26_2_1 upgrades independently of arrival ordering`() {
        for (version in listOf(153, 154, 156, 157)) {
            val prefs = FakePreferencesRepository().apply {
                setInt("whatsNewVer", version)
                // A rider who installed 26.2.x fresh has this legacy-only source recorded as zero.
                setInt("arrival_display_migration_source_version", 0)
                setString(ArrivalDisplayMode.PREFERENCE_KEY, "time")
            }
            val vm = viewModel(prefs)
            vm.maybeShowStartup()
            assertEquals(HelpDialog.SearchWorkflow, vm.state.value.dialog)
            assertEquals(SearchResultMode.MAP, prefs.searchResultMode())
            vm.chooseSearchResultMode(SearchResultMode.LISTS)
            assertEquals(SearchResultMode.LISTS, prefs.searchResultMode())
            assertEquals(ArrivalDisplayMode.TIME, prefs.arrivalDisplayDefault())
            val nextLaunch = viewModel(prefs)
            nextLaunch.maybeShowStartup()
            assertFalse(nextLaunch.state.value.dialog == HelpDialog.SearchWorkflow)
        }
    }

    @Test
    fun `search choice precedes arrival choice and does not choose arrival ordering`() {
        val prefs = FakePreferencesRepository().apply { setInt("whatsNewVer", 153) }
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        assertEquals(HelpDialog.SearchWorkflow, vm.state.value.dialog)
        assertEquals(listOf(HelpDialog.SearchWorkflow, HelpDialog.ArrivalDisplay), vm.state.value.migrationPages)
        vm.chooseSearchResultMode(SearchResultMode.MAP)
        assertEquals(HelpDialog.ArrivalDisplay, vm.state.value.dialog)
        assertEquals(null, prefs.getString(ArrivalDisplayMode.PREFERENCE_KEY, null))
    }

    @Test
    fun `back revisits the search choice without completing arrival migration`() {
        val prefs = FakePreferencesRepository().apply { setInt("whatsNewVer", 1) }
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        vm.previousMigrationPage()
        assertEquals(HelpDialog.SearchWorkflow, vm.state.value.dialog)
        vm.chooseSearchResultMode(SearchResultMode.LISTS)
        vm.previousMigrationPage()
        assertEquals(HelpDialog.SearchWorkflow, vm.state.value.dialog)
        assertEquals(SearchResultMode.LISTS, prefs.searchResultMode())
        assertEquals(null, prefs.getString(ArrivalDisplayMode.PREFERENCE_KEY, null))
        vm.chooseSearchResultMode(SearchResultMode.MAP)
        assertEquals(HelpDialog.ArrivalDisplay, vm.state.value.dialog)
        assertEquals(2, vm.state.value.migrationPages.size)
        vm.chooseArrivalDisplayDefault(ArrivalDisplayMode.TIME)
        assertEquals(HelpDialog.WhatsNew, vm.state.value.dialog)
        assertEquals(SearchResultMode.MAP, prefs.searchResultMode())
        assertEquals(ArrivalDisplayMode.TIME, prefs.arrivalDisplayDefault())
    }

    @Test
    fun `migration pages include only the choices owed at startup`() {
        for (savedKey in listOf(SearchResultMode.PREFERENCE_KEY, ArrivalDisplayMode.PREFERENCE_KEY)) {
            val prefs = FakePreferencesRepository().apply {
                setInt("whatsNewVer", 153)
                setString(savedKey, if (savedKey == SearchResultMode.PREFERENCE_KEY) "map" else "route")
            }
            val vm = viewModel(prefs)
            vm.maybeShowStartup()
            val expected = if (savedKey == SearchResultMode.PREFERENCE_KEY) HelpDialog.ArrivalDisplay else HelpDialog.SearchWorkflow
            assertEquals(listOf(expected), vm.state.value.migrationPages)
            vm.previousMigrationPage()
            assertEquals(expected, vm.state.value.dialog)
        }
    }

    @Test
    fun `last migration page offers tutorials when release notes were already read`() {
        for (savedKey in listOf(SearchResultMode.PREFERENCE_KEY, ArrivalDisplayMode.PREFERENCE_KEY)) {
            val prefs = FakePreferencesRepository().apply {
                setInt("whatsNewVer", BuildConfig.VERSION_CODE)
                setInt("post_26_2_1_layout_migration_source_version", 153)
                setString(savedKey, if (savedKey == SearchResultMode.PREFERENCE_KEY) "map" else "route")
            }
            val vm = viewModel(prefs)
            vm.maybeShowStartup()
            assertEquals(1, vm.state.value.migrationPages.size)
            vm.finishMigrationPage()
            assertEquals(HelpDialog.TutorialOptOut, vm.state.value.dialog)
        }
    }

    @Test
    fun `dismissed search choice remains owed after the release marker changes`() {
        val prefs = FakePreferencesRepository().apply { setInt("whatsNewVer", 157) }
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        vm.finishMigrationPage()
        assertEquals(null, prefs.getString(SearchResultMode.PREFERENCE_KEY, null))
        val nextLaunch = viewModel(prefs)
        nextLaunch.maybeShowStartup()
        assertEquals(HelpDialog.SearchWorkflow, nextLaunch.state.value.dialog)
    }

    @Test
    fun `migration choice precedes release notes and persists the selected default`() {
        val prefs = FakePreferencesRepository().apply {
            setString(SearchResultMode.PREFERENCE_KEY, "map")
            setInt("whatsNewVer", 1)
        }
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
        val prefs = FakePreferencesRepository().apply {
            setString(SearchResultMode.PREFERENCE_KEY, "map")
            setInt("whatsNewVer", 154)
        }
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        assertEquals(HelpDialog.ArrivalDisplay, vm.state.value.dialog)
        vm.finishMigrationPage()
        assertEquals(null, prefs.getString(ArrivalDisplayMode.PREFERENCE_KEY, null))
        prefs.setString(SearchResultMode.PREFERENCE_KEY, "map")
        prefs.setInt("whatsNewVer", 156)
        val nextLaunch = viewModel(prefs)
        nextLaunch.maybeShowStartup()
        assertEquals(HelpDialog.ArrivalDisplay, nextLaunch.state.value.dialog)
    }

    @Test
    fun `both migration pages include all existing releases through 26_2_1`() {
        for (version in listOf(1, 153, 154, 155, 156, 157)) {
            val prefs = FakePreferencesRepository().apply {
                setInt("whatsNewVer", version)
                // An older migration decision must not exclude 26.2.x installations.
                setInt("arrival_display_migration_source_version", 0)
            }
            val vm = viewModel(prefs)
            vm.maybeShowStartup()
            assertEquals(
                "Previous version $version",
                listOf(HelpDialog.SearchWorkflow, HelpDialog.ArrivalDisplay),
                vm.state.value.migrationPages
            )
        }
    }

    @Test
    fun `fresh installs never become migration candidates on later launches`() {
        val prefs = FakePreferencesRepository()
        val vm = viewModel(prefs)
        vm.maybeShowStartup()
        assertEquals(HelpDialog.WhatsNew, vm.state.value.dialog)
        assertEquals(BuildConfig.VERSION_CODE, prefs.getInt("whatsNewVer", 0))
        assertTrue(vm.state.value.migrationPages.isEmpty())
        assertEquals(SearchResultMode.MAP, prefs.searchResultMode())
        assertEquals(ArrivalDisplayMode.ROUTE, prefs.arrivalDisplayDefault())

        // Debug builds reuse an old versionCode; the recorded source must still exclude them.
        val nextLaunch = viewModel(prefs)
        nextLaunch.maybeShowStartup()
        assertEquals(HelpDialog.None, nextLaunch.state.value.dialog)
    }

    @Test
    fun `a default chosen in settings suppresses migration and existing dialogs are respected`() {
        val prefs = FakePreferencesRepository().apply {
            setString(SearchResultMode.PREFERENCE_KEY, "map")
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

    @Test
    fun `choose your layout reopens both pages whatever is saved and finishing owes nothing`() {
        val prefs = FakePreferencesRepository().apply {
            setString(SearchResultMode.PREFERENCE_KEY, "lists")
            setString(ArrivalDisplayMode.PREFERENCE_KEY, "time")
            // Release notes unread and the tutorial offer still owed: neither may follow a revisit.
            setInt("whatsNewVer", 0)
        }
        val vm = viewModel(prefs)
        vm.showMenu()
        vm.showLayoutChoices()
        assertEquals(listOf(HelpDialog.SearchWorkflow, HelpDialog.ArrivalDisplay), vm.state.value.migrationPages)
        assertEquals(HelpDialog.SearchWorkflow, vm.state.value.dialog)
        vm.chooseSearchResultMode(SearchResultMode.MAP)
        assertEquals(HelpDialog.ArrivalDisplay, vm.state.value.dialog)
        vm.chooseArrivalDisplayDefault(ArrivalDisplayMode.ROUTE)
        assertEquals(HelpDialog.None, vm.state.value.dialog)
        assertEquals(SearchResultMode.MAP, prefs.searchResultMode())
        assertEquals(ArrivalDisplayMode.ROUTE, prefs.arrivalDisplayDefault())
        assertEquals(0, prefs.getInt("whatsNewVer", 0))
        assertTrue(prefs.getBoolean(TutorialPrefs.TUTORIAL_OPT_OUT_DIALOG, true))
    }

    @Test
    fun `a dismissed revisit closes without a release-notes or tutorial follow-up`() {
        val prefs = FakePreferencesRepository().apply { setInt("whatsNewVer", 0) }
        val vm = viewModel(prefs)
        vm.showLayoutChoices()
        vm.finishMigrationPage()
        vm.finishMigrationPage()
        assertEquals(HelpDialog.None, vm.state.value.dialog)
        assertFalse(vm.state.value.revisitingLayout)
        assertEquals(null, prefs.getString(SearchResultMode.PREFERENCE_KEY, null))
    }

    @Test
    fun `pages start on the saved choice and otherwise on the classic layout`() {
        val unsaved = viewModel(FakePreferencesRepository())
        assertEquals(SearchResultMode.LISTS, unsaved.searchWorkflowStart())
        assertEquals(ArrivalDisplayMode.TIME, unsaved.arrivalDisplayStart())
        val saved = viewModel(
            FakePreferencesRepository().apply {
                setString(SearchResultMode.PREFERENCE_KEY, "map")
                setString(ArrivalDisplayMode.PREFERENCE_KEY, "route")
            }
        )
        assertEquals(SearchResultMode.MAP, saved.searchWorkflowStart())
        assertEquals(ArrivalDisplayMode.ROUTE, saved.arrivalDisplayStart())
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
