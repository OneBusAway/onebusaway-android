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
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.region.Region
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.ServiceAlertDao
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.ui.tutorial.TutorialPrefs
import org.onebusaway.android.util.ThemeUtils

/** One-shot actions the root settings screen can only perform with its host Activity. */
sealed interface SettingsEffect {
    /** Re-create the Activity so a just-applied app theme takes effect. */
    object RecreateActivity : SettingsEffect

    /** Reset-tutorials was tapped: return home (and replay the tutorials there). */
    object GoHomeResetTutorial : SettingsEffect
}

/**
 * State + actions for the root Compose settings screen. Reads/writes go through the
 * [PreferencesRepository] seam; the immutable [SettingsUiState] is recomputed whenever any preference
 * or the current region changes. Action methods carry over every change-listener/click side effect
 * the old `SettingsFragment` performed; the few that need an Activity are emitted as [effects].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val serviceAlertDao: ServiceAlertDao,
    private val importGate: ImportGate,
    private val obaAnalytics: ObaAnalytics,
) : ViewModel() {

    private val env = SettingsEnvironment(
        useFixedRegion = BuildConfig.USE_FIXED_REGION,
        sdkInt = Build.VERSION.SDK_INT,
        isObaFlavor = BuildFlavorUtils.isOBABuildFlavor(),
    )

    val state: StateFlow<SettingsUiState> =
        combine(prefs.observeChanges(), regionRepository.region) { _, region -> compute(region) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                compute(regionRepository.region.value),
            )

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    private fun compute(region: Region?): SettingsUiState = buildSettingsUiState(
        prefs = readSnapshot(),
        region = region?.let { RegionSummaryInfo(it.name.orEmpty(), !it.otpBaseUrl.isNullOrEmpty()) },
        env = env,
        customApiRegionSummary = context.getString(R.string.preferences_region_summary_custom_api),
    )

    private fun readSnapshot() = SettingsPrefSnapshot(
        autoSelectRegion = prefs.getBoolean(R.string.preference_key_auto_select_region, true),
        showNegativeArrivals = prefs.getBoolean(R.string.preference_key_show_negative_arrivals, true),
        hideAlerts = prefs.getBoolean(R.string.preference_key_hide_alerts, false),
        showZoomControls = prefs.getBoolean(R.string.preference_key_show_zoom_controls, false),
        displayWeatherView = prefs.getBoolean(R.string.preference_key_display_weather_view, true),
        showAvailableStudies = prefs.getBoolean(R.string.preference_key_show_available_studies, true),
        showTutorialScreens = prefs.getBoolean(R.string.preference_key_show_tutorial_screens, true),
        leftHandMode = prefs.getBoolean(R.string.preference_key_left_hand_mode, false),
        showHeaderArrivals = prefs.getBoolean(R.string.preference_key_show_header_arrivals, false),
        vibrateAllowed = prefs.getBoolean(R.string.preference_key_preference_vibrate_allowed, true),
        tripPlanNotifications = prefs.getBoolean(R.string.preference_key_trip_plan_notifications, true),
        analyticsEnabled = prefs.getBoolean(R.string.preferences_key_analytics, true),
        shareDestinationLogs = prefs.getBoolean(R.string.preferences_key_user_share_destination_logs, true),
        mapMode = prefs.getString(
            R.string.preference_key_map_mode,
            context.getString(R.string.preferences_preferred_map_option_normal2d),
        ),
        preferredUnits = prefs.getString(
            R.string.preference_key_preferred_units,
            context.getString(R.string.preferences_preferred_units_option_automatic),
        ),
        preferredTempUnits = prefs.getString(
            R.string.preference_key_preferred_temperature_units,
            context.getString(R.string.preferences_preferred_units_option_automatic),
        ),
        appTheme = prefs.getString(
            R.string.preference_key_app_theme,
            context.getString(R.string.preferences_app_theme_option_system_default),
        ),
    )

    // region Toggle actions

    fun onAutoSelectRegionChanged(value: Boolean) {
        prefs.setBoolean(R.string.preference_key_auto_select_region, value)
        reportPreferencesEvent(
            context,
            if (value) R.string.analytics_label_button_press_auto
            else R.string.analytics_label_button_press_manual
        )
    }

    fun onShowNegativeArrivalsChanged(value: Boolean) {
        prefs.setBoolean(R.string.preference_key_show_negative_arrivals, value)
        obaAnalytics.setShowDepartedVehicles(value)
    }

    fun onHideAlertsChanged(value: Boolean) {
        prefs.setBoolean(R.string.preference_key_hide_alerts, value)
        if (value) viewModelScope.launch { importGate.awaitReady(); serviceAlertDao.setAllHidden(1) }
    }

    /**
     * Re-resolve the region after a backup restore: the restored data may imply a different region. The
     * repository raises the forced picker (driven reactively off its state) if the choice is ambiguous.
     */
    fun refreshRegionAfterRestore() {
        viewModelScope.launch { regionRepository.refresh() }
    }

    fun onShowZoomControlsChanged(value: Boolean) =
        prefs.setBoolean(R.string.preference_key_show_zoom_controls, value)

    fun onDisplayWeatherViewChanged(value: Boolean) =
        prefs.setBoolean(R.string.preference_key_display_weather_view, value)

    fun onShowAvailableStudiesChanged(value: Boolean) =
        prefs.setBoolean(R.string.preference_key_show_available_studies, value)

    fun onShowTutorialScreensChanged(value: Boolean) =
        prefs.setBoolean(R.string.preference_key_show_tutorial_screens, value)

    fun onLeftHandModeChanged(value: Boolean) {
        prefs.setBoolean(R.string.preference_key_left_hand_mode, value)
        obaAnalytics.setLeftHanded(value)
    }

    fun onShowHeaderArrivalsChanged(value: Boolean) =
        prefs.setBoolean(R.string.preference_key_show_header_arrivals, value)

    fun onVibrateAllowedChanged(value: Boolean) =
        prefs.setBoolean(R.string.preference_key_preference_vibrate_allowed, value)

    fun onTripPlanNotificationsChanged(value: Boolean) =
        prefs.setBoolean(R.string.preference_key_trip_plan_notifications, value)

    fun onAnalyticsChanged(value: Boolean) {
        prefs.setBoolean(R.string.preferences_key_analytics, value)
        obaAnalytics.setSendAnonymousData(value)
    }

    fun onShareDestinationLogsChanged(value: Boolean) =
        prefs.setBoolean(R.string.preferences_key_user_share_destination_logs, value)

    // endregion

    // region List actions

    fun onMapModeChanged(value: String) = prefs.setString(R.string.preference_key_map_mode, value)

    fun onPreferredUnitsChanged(value: String) =
        prefs.setString(R.string.preference_key_preferred_units, value)

    fun onPreferredTempUnitsChanged(value: String) =
        prefs.setString(R.string.preference_key_preferred_temperature_units, value)

    fun onAppThemeChanged(value: String) {
        prefs.setString(R.string.preference_key_app_theme, value)
        ThemeUtils.setAppTheme(context, value)
        _effects.trySend(SettingsEffect.RecreateActivity)
    }

    // endregion

    // region Click actions

    /** The currently-selected notification ringtone URI (for seeding the picker), or null. */
    val ringtoneValue: String?
        get() = prefs.getString(R.string.preference_key_notification_sound, null)

    /** Persists the ringtone the host's picker returned (empty string == silent). */
    fun onRingtonePicked(value: String) =
        prefs.setString(R.string.preference_key_notification_sound, value)

    fun onTutorialClicked() {
        reportPreferencesEvent(context, R.string.analytics_label_button_press_tutorial)
        TutorialPrefs.resetAllTutorials(context)
        _effects.trySend(SettingsEffect.GoHomeResetTutorial)
    }

    fun onPoweredByObaClicked() =
        reportPreferencesEvent(context, R.string.analytics_label_button_press_powered_by_oba)

    fun onAboutClicked() = reportPreferencesEvent(context, R.string.analytics_label_button_press_about)

    // endregion
}
