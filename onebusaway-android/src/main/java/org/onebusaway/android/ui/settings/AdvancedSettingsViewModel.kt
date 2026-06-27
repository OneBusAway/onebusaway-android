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
package org.onebusaway.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.elements.ObaRegion
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.ApiUrlValidator
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.RegionState
import org.onebusaway.android.region.RegionStatus
import org.onebusaway.android.travelbehavior.io.coroutines.FirebaseDataPusher

/** One-shot actions the advanced settings screen routes back to its host. */
sealed interface AdvancedSettingsEffect {
    /** The custom OBA URL was cleared: re-initialize regions by returning home. */
    object GoHome : AdvancedSettingsEffect
}

/**
 * State + actions for the advanced settings screen (custom OBA/OTP API URLs, experimental regions,
 * debug data push). Carries over the side effects of the old `AdvancedSettingsFragment`: URL
 * validation, region clearing/refresh, analytics, and the OTP re-home signal — the Activity-bound
 * ones surface as [effects].
 */
@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val regionRepository: RegionRepository,
) : ViewModel() {

    init {
        // The old fragment reset this on every (re)display of the advanced screen.
        Application.get().setUseOldOtpApiUrlVersion(false)
    }

    val state: StateFlow<AdvancedSettingsUiState> =
        combine(prefs.observeChanges(), regionRepository.region) { _, region -> compute(region) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                compute(regionRepository.region.value),
            )

    private val _effects = Channel<AdvancedSettingsEffect>(Channel.BUFFERED)
    val effects: Flow<AdvancedSettingsEffect> = _effects.receiveAsFlow()

    private fun compute(region: ObaRegion?): AdvancedSettingsUiState = buildAdvancedSettingsUiState(
        prefs = AdvancedPrefSnapshot(
            experimentalRegionsEnabled = prefs.getBoolean(R.string.preference_key_experimental_regions, false),
            displayTestAlerts = prefs.getBoolean(R.string.preferences_display_test_alerts, false),
            customObaApiUrl = prefs.getString(R.string.preference_key_oba_api_url, null),
            customOtpApiUrl = prefs.getString(R.string.preference_key_otp_api_url, null),
        ),
        region = region?.let { AdvancedRegionInfo(it.experimental) },
        useFixedRegion = BuildConfig.USE_FIXED_REGION,
        obaBrandedSummary = context.getString(
            R.string.preferences_oba_api_servername_summary,
            context.getString(R.string.app_name),
        ),
        otpDefaultSummary = context.getString(R.string.preferences_otp_api_servername_summary),
    )

    fun onDisplayTestAlertsChanged(value: Boolean) =
        prefs.setBoolean(R.string.preferences_display_test_alerts, value)

    /**
     * Apply an experimental-regions toggle (the screen owns the enable/disable confirmation dialogs).
     * Mirrors the legacy flow: clear the region first when turning off an experimental region, persist,
     * log analytics, then re-resolve the region directly (the forced picker, if needed, is driven reactively
     * off the repository — [org.onebusaway.android.ui.home.RegionPickerViewModel] — and shows over this screen).
     */
    fun setExperimentalRegions(enabled: Boolean, regionWasExperimental: Boolean) {
        if (!enabled && regionWasExperimental) regionRepository.clear()
        prefs.setBoolean(R.string.preference_key_experimental_regions, enabled)
        reportPreferencesEvent(
            context,
            if (enabled) R.string.analytics_label_button_press_experimental_on
            else R.string.analytics_label_button_press_experimental_off
        )
        viewModelScope.launch {
            resetOtpVersionOnRegionChange(regionRepository.refresh(), regionRepository.state) {
                prefs.setBoolean(R.string.preference_key_otp_api_url_version, false)
            }
        }
    }

    /**
     * Apply an edit to the custom OBA API URL. Returns the outcome so the dialog can stay open on an
     * invalid URL (matching the legacy reject-on-invalid behavior).
     */
    fun onCustomObaApiUrlChanged(url: String): UrlChangeResult {
        val trimmed = url.trim()
        return if (trimmed.isNotEmpty()) {
            if (!ApiUrlValidator.validateUrl(trimmed)) {
                UrlChangeResult.InvalidObaUrl
            } else {
                prefs.setString(R.string.preference_key_oba_api_url, trimmed)
                regionRepository.clear()
                UrlChangeResult.Accepted
            }
        } else {
            prefs.setString(R.string.preference_key_oba_api_url, "")
            _effects.trySend(AdvancedSettingsEffect.GoHome)
            UrlChangeResult.ClearedGoHome
        }
    }

    /**
     * Apply an edit to the custom OTP API URL. Returns the outcome (see [onCustomObaApiUrlChanged]).
     * No host re-home effect is emitted: the settings subtree compares this persisted URL on entry vs.
     * exit (see SettingsRehomeEffect) and re-homes if it changed.
     */
    fun onCustomOtpApiUrlChanged(url: String): UrlChangeResult {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && !ApiUrlValidator.validateUrl(trimmed)) {
            return UrlChangeResult.InvalidOtpUrl
        }
        prefs.setString(R.string.preference_key_otp_api_url, trimmed)
        return UrlChangeResult.Accepted
    }

    fun onPushFirebaseData() = FirebaseDataPusher().push(context)

    fun onResetDonationTimestamps() {
        Application.getDonationsManager().setDonationRequestReminderDate(null)
        Application.getDonationsManager().setDonationRequestDismissedDate(null)
    }
}

/**
 * The domain rule that a toggle-initiated experimental-region *change* resets the OTP API version to the
 * current default ([resetOtpVersion]). The repository's own [RegionRepository.applyRegion] reset covers
 * regions that carry an `otpBaseUrl`; this covers the toggle case uniformly (incl. `otpBaseUrl == null`).
 * On a forced manual choice the region hasn't changed yet — await the user's pick (the next
 * [RegionState.Active] on [state]) first. Pure (no Context/Application), so it's unit-tested directly.
 */
internal suspend fun resetOtpVersionOnRegionChange(
    status: RegionStatus,
    state: StateFlow<RegionState>,
    resetOtpVersion: () -> Unit,
) {
    when (status) {
        is RegionStatus.Changed -> resetOtpVersion()
        is RegionStatus.NeedsManualSelection -> {
            state.filterIsInstance<RegionState.Active>().first()
            resetOtpVersion()
        }
        RegionStatus.Unchanged, RegionStatus.Skipped, RegionStatus.Failed, is RegionStatus.Fixed -> Unit
    }
}
