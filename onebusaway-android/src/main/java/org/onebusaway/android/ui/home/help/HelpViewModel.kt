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
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.ui.tutorial.TutorialPrefs

/** Which help dialog is showing — the dialog state this help feature module owns. */
sealed interface HelpDialog {
    object None : HelpDialog
    object Menu : HelpDialog
    object WhatsNew : HelpDialog
    object Legend : HelpDialog
    object TutorialOptOut : HelpDialog
}

/** The help feature's state: which dialog is up + whether the menu offers "contact us". */
data class HelpUiState(val dialog: HelpDialog = HelpDialog.None, val showContactUs: Boolean = true)

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
    private val regionRepository: RegionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HelpUiState())
    val state: StateFlow<HelpUiState> = _state.asStateFlow()

    // Whether a region has resolved — gates the auto-show of "What's New". Reads the shared
    // [RegionRepository.regionPresent] predicate; [HelpFeature] reads it. Mirrors the manual
    // collect-into-MutableStateFlow idiom used by the other feature VMs (WeatherViewModel) rather than a
    // SharingStarted.Eagerly stateIn, whose never-completing collector leaks across JVM unit tests.
    private val _regionReady = MutableStateFlow(regionRepository.region.value != null)
    val regionReady: StateFlow<Boolean> = _regionReady.asStateFlow()

    init {
        viewModelScope.launch {
            regionRepository.regionPresent.collect { _regionReady.value = it }
        }
    }

    /**
     * The Twitter/X URL to open from the help menu: the current region's own Twitter URL when it has
     * one, else the OneBusAway default. The host fires the ACTION_VIEW; choosing the URL is VM work, so
     * it's resolved here rather than in the Activity.
     */
    fun twitterUrl(): String =
        regionRepository.region.value?.twitterUrl?.takeUnless { it.isEmpty() } ?: TWITTER_URL

    /**
     * Open the help menu (from the nav drawer's Help item). "Contact us" is hidden when a custom OBA API
     * URL is set (there's no region contact email to reach) — derived here rather than passed by the host.
     */
    fun showMenu() {
        val customApiUrl = prefs.getString(R.string.preference_key_oba_api_url, null)
        _state.update { it.copy(dialog = HelpDialog.Menu, showContactUs = customApiUrl.isNullOrEmpty()) }
    }

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
    }
}
