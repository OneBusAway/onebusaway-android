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
package org.onebusaway.android.ui.home.help

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.RegionState
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.searchresults.SearchResultMode
import org.onebusaway.android.ui.tutorial.TutorialPrefs

/** Which help dialog is showing — the dialog state this help feature module owns. */
sealed interface HelpDialog {
    object None : HelpDialog
    object Menu : HelpDialog
    object SearchWorkflow : HelpDialog
    object ArrivalDisplay : HelpDialog
    object WhatsNew : HelpDialog
    object Legend : HelpDialog
    object TutorialOptOut : HelpDialog
}

/** The help feature's state: which dialog is up + whether the menu offers "contact us". */
data class HelpUiState(
    val dialog: HelpDialog = HelpDialog.None,
    val showContactUs: Boolean = true,
    val migrationPages: List<HelpDialog> = emptyList()
)

/**
 * Owns the help / what's-new / legend dialogs as a feature module (mirrors the other home feature
 * modules). Holds the dialog state, the what's-new version check, and the region-derived Twitter URL;
 * the menu *actions* that do things (reset tutorials, agencies, open the Twitter URL, contact us) are
 * genuine Activity operations and stay in HomeActivity, reached via the `onHelpAction` callback
 * [HelpFeature] forwards.
 */
@HiltViewModel
class HelpViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val regionRepository: RegionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HelpUiState())
    val state: StateFlow<HelpUiState> = _state.asStateFlow()

    // Active(null) is a resolved custom API endpoint and must receive migration choices too.
    val regionState: StateFlow<RegionState> get() = regionRepository.state

    /**
     * The Twitter/X URL to open from the help menu: the current region's own Twitter URL when it has
     * one, else the OneBusAway default. The host fires the ACTION_VIEW; choosing the URL is VM work, so
     * it's resolved here rather than in the Activity.
     */
    fun twitterUrl(): String = regionRepository.region.value?.twitterUrl?.takeUnless { it.isEmpty() } ?: TWITTER_URL

    /**
     * Open the help menu (from the nav drawer's Help item). "Contact us" is hidden when a custom OBA API
     * URL is set (there's no region contact email to reach) — derived here rather than passed by the host.
     */
    fun showMenu() {
        val customApiUrl = prefs.getString(R.string.preference_key_oba_api_url, null)
        _state.update { it.copy(dialog = HelpDialog.Menu, showContactUs = customApiUrl.isNullOrEmpty()) }
    }

    private var startupPresented = false

    /** One startup sequence: default chooser, release notes, then the tutorial invitation. */
    fun maybeShowStartup() {
        if (startupPresented || _state.value.dialog != HelpDialog.None) return
        startupPresented = true
        // Both choices ship after 26.2.1 and apply to every existing installation, including
        // fresh installs of 26.2.x. Do not reuse the earlier legacy-only migration marker.
        val sourceVersion = captureMigrationSource()
        val pages = buildList {
            if (sourceVersion > 0 && prefs.getString(SearchResultMode.PREFERENCE_KEY, null) == null) add(HelpDialog.SearchWorkflow)
            if (sourceVersion > 0 && prefs.getString(ArrivalDisplayMode.PREFERENCE_KEY, null) == null) add(HelpDialog.ArrivalDisplay)
        }
        _state.update { it.copy(migrationPages = pages, dialog = pages.firstOrNull() ?: HelpDialog.None) }
        if (pages.isEmpty()) maybeAutoShowWhatsNew()
    }

    fun previousMigrationPage() {
        _state.update { state ->
            val previous = state.migrationPages.getOrNull(state.migrationPages.indexOf(state.dialog) - 1)
            if (previous == null) state else state.copy(dialog = previous)
        }
    }

    fun chooseSearchResultMode(mode: SearchResultMode) {
        prefs.setString(SearchResultMode.PREFERENCE_KEY, mode.value)
        finishMigrationPage()
    }

    fun chooseArrivalDisplayDefault(mode: ArrivalDisplayMode) {
        prefs.setString(ArrivalDisplayMode.PREFERENCE_KEY, mode.value)
        finishMigrationPage()
    }

    /** Advance through the pages captured at startup; dismissing leaves an unsaved choice owed next launch. */
    fun finishMigrationPage() {
        val state = _state.value
        val index = state.migrationPages.indexOf(state.dialog)
        if (index < 0) return
        val next = state.migrationPages.getOrNull(index + 1)
        _state.update { it.copy(dialog = next ?: HelpDialog.None) }
        if (next == null && !maybeAutoShowWhatsNew()) maybeShowTutorialOptOut()
    }

    // Keep the source across launches before What's New advances its marker, so deferring a
    // choice neither loses upgrade eligibility nor opts a fresh install in.
    private fun captureMigrationSource(): Int = prefs.getInt(LAYOUT_MIGRATION_SOURCE_VERSION, -1).takeUnless { it == -1 }
        ?: prefs.getInt(WHATS_NEW_VER, 0).also { prefs.setInt(LAYOUT_MIGRATION_SOURCE_VERSION, it) }

    fun showWhatsNew() = _state.update { it.copy(dialog = HelpDialog.WhatsNew) }

    fun showLegend() = _state.update { it.copy(dialog = HelpDialog.Legend) }

    fun dismiss() = _state.update { it.copy(dialog = HelpDialog.None) }

    /** After what's-new, offer the tutorial opt-out once (gated by TUTORIAL_OPT_OUT_DIALOG). */
    fun maybeShowTutorialOptOut() {
        if (prefs.getBoolean(TutorialPrefs.TUTORIAL_OPT_OUT_DIALOG, true)) {
            _state.update { it.copy(dialog = HelpDialog.TutorialOptOut) }
            // Only offer it once.
            prefs.setBoolean(TutorialPrefs.TUTORIAL_OPT_OUT_DIALOG, false)
        }
    }

    /** Records the opt-out choice (enable/disable tutorial popups) and closes the dialog. */
    fun setTutorialsEnabled(enabled: Boolean) {
        prefs.setBoolean(R.string.preference_key_show_tutorial_screens, enabled)
        dismiss()
    }

    /**
     * Show "What's New" if a newer version was just installed; returns whether it was (the activity uses
     * that to refresh the region-gated drawer items).
     */
    fun maybeAutoShowWhatsNew(): Boolean {
        val newVer = BuildConfig.VERSION_CODE
        if (prefs.getInt(WHATS_NEW_VER, 0) < newVer) {
            showWhatsNew()
            prefs.setInt(WHATS_NEW_VER, newVer)
            return true
        }
        return false
    }

    companion object {
        /** Fallback Twitter/X URL when the current region defines none (ported from HomeActivity). */
        const val TWITTER_URL = "http://mobile.twitter.com/onebusaway"

        private const val WHATS_NEW_VER = "whatsNewVer"
        private const val LAYOUT_MIGRATION_SOURCE_VERSION = "post_26_2_1_layout_migration_source_version"
    }
}
