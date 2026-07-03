/*
 * Copyright (C) 2010-2017 Brian Ferris (bdferris@onebusaway.org),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation,
 * Open Transit Software Foundation
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

import org.onebusaway.android.ui.HomeActivity
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.onebusaway.android.R
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.settings.components.ClickPreferenceItem
import org.onebusaway.android.ui.settings.components.ListPreferenceItem
import org.onebusaway.android.ui.settings.components.PreferenceCategory
import org.onebusaway.android.ui.settings.components.SwitchPreferenceItem
import org.onebusaway.android.backup.BackupUtils

/**
 * The settings NavHost destination (former `SettingsActivity`) — now a pure-Compose
 * screen backed by [SettingsViewModel]. The stateful [SettingsRoute] owns the ringtone/backup
 * ActivityResult launchers and the host-bound effects; the stateless [SettingsScreen] renders the
 * preference categories. The Advanced sub-screen is its own destination ([NavRoutes.SETTINGS_ADVANCED]).
 */
@Composable
fun SettingsRoute(
    onNavigateToRegions: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onBack: () -> Unit,
    onRecreate: () -> Unit,
    onGoHomeResetTutorial: () -> Unit,
    onOpenDonate: () -> Unit,
    onOpenPoweredByOba: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current.findActivity()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data!!.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.onRingtonePicked(uri?.toString() ?: "")
        }
    }
    val saveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { BackupUtils.save(activity, it) }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // BackupUtils restores the DB + toasts; then re-resolve the region (the restored data may
                // imply a different one); the forced picker is raised reactively if it's ambiguous.
                BackupUtils.restore(activity, uri) {
                    viewModel.refreshRegionAfterRestore()
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.RecreateActivity -> onRecreate()
                SettingsEffect.GoHomeResetTutorial -> onGoHomeResetTutorial()
            }
        }
    }

    // The "check region" dialog — shown once when the report flow launches settings with the
    // EXTRA_SHOW_CHECK_REGION_DIALOG extra.
    LaunchedEffect(Unit) {
        if (activity.intent.getBooleanExtra(HomeActivity.EXTRA_SHOW_CHECK_REGION_DIALOG, false)) {
            RegionEntryPoint.get(activity).currentRegion()?.let { region ->
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.preference_region_dialog_title))
                    .setMessage(
                        activity.getString(R.string.preference_region_dialog_message, region.name)
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            activity.intent.removeExtra(HomeActivity.EXTRA_SHOW_CHECK_REGION_DIALOG)
        }
    }

    val actions = SettingsActions(
        onAutoSelectRegion = viewModel::onAutoSelectRegionChanged,
        onShowNegativeArrivals = viewModel::onShowNegativeArrivalsChanged,
        onHideAlerts = viewModel::onHideAlertsChanged,
        onShowZoomControls = viewModel::onShowZoomControlsChanged,
        onDisplayWeatherView = viewModel::onDisplayWeatherViewChanged,
        onShowAvailableStudies = viewModel::onShowAvailableStudiesChanged,
        onShowTutorialScreens = viewModel::onShowTutorialScreensChanged,
        onLeftHandMode = viewModel::onLeftHandModeChanged,
        onShowHeaderArrivals = viewModel::onShowHeaderArrivalsChanged,
        onVibrateAllowed = viewModel::onVibrateAllowedChanged,
        onTripPlanNotifications = viewModel::onTripPlanNotificationsChanged,
        onAnalytics = viewModel::onAnalyticsChanged,
        onShareDestinationLogs = viewModel::onShareDestinationLogsChanged,
        onMapMode = viewModel::onMapModeChanged,
        onPreferredUnits = viewModel::onPreferredUnitsChanged,
        onPreferredTempUnits = viewModel::onPreferredTempUnitsChanged,
        onAppTheme = viewModel::onAppThemeChanged,
        onRegionClick = onNavigateToRegions,
        onRingtoneClick = {
            ringtoneLauncher.launch(buildRingtonePickerIntent(viewModel.ringtoneValue))
        },
        onSaveBackup = { saveBackupLauncher.launch(BackupUtils.buildCreateBackupFileIntent()) },
        onRestoreBackup = { restoreBackupLauncher.launch(BackupUtils.buildSelectBackupFileIntent()) },
        onAdvancedClick = onNavigateToAdvanced,
        onTutorialClick = viewModel::onTutorialClicked,
        onDonateClick = onOpenDonate,
        onPoweredByObaClick = {
            viewModel.onPoweredByObaClicked()
            onOpenPoweredByOba()
        },
        onAboutClick = {
            viewModel.onAboutClicked()
            onNavigateToAbout()
        },
    )

    SettingsScreen(state = state, onBack = onBack, actions = actions)
}

/** All the user actions the [SettingsScreen] can fire, wired by [SettingsRoute]. */
class SettingsActions(
    val onAutoSelectRegion: (Boolean) -> Unit,
    val onShowNegativeArrivals: (Boolean) -> Unit,
    val onHideAlerts: (Boolean) -> Unit,
    val onShowZoomControls: (Boolean) -> Unit,
    val onDisplayWeatherView: (Boolean) -> Unit,
    val onShowAvailableStudies: (Boolean) -> Unit,
    val onShowTutorialScreens: (Boolean) -> Unit,
    val onLeftHandMode: (Boolean) -> Unit,
    val onShowHeaderArrivals: (Boolean) -> Unit,
    val onVibrateAllowed: (Boolean) -> Unit,
    val onTripPlanNotifications: (Boolean) -> Unit,
    val onAnalytics: (Boolean) -> Unit,
    val onShareDestinationLogs: (Boolean) -> Unit,
    val onMapMode: (String) -> Unit,
    val onPreferredUnits: (String) -> Unit,
    val onPreferredTempUnits: (String) -> Unit,
    val onAppTheme: (String) -> Unit,
    val onRegionClick: () -> Unit,
    val onRingtoneClick: () -> Unit,
    val onSaveBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onAdvancedClick: () -> Unit,
    val onTutorialClick: () -> Unit,
    val onDonateClick: () -> Unit,
    val onPoweredByObaClick: () -> Unit,
    val onAboutClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    actions: SettingsActions,
) {
    val appName = stringResource(R.string.app_name)
    Scaffold(
        topBar = {
            ObaTopAppBar(title = stringResource(R.string.navdrawer_item_settings), onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.showRegionCategory) {
                PreferenceCategory(stringResource(R.string.preferences_category_location)) {
                    ClickPreferenceItem(
                        title = stringResource(R.string.preferences_region_title),
                        summary = state.regionSummary,
                        onClick = actions.onRegionClick,
                    )
                    SwitchPreferenceItem(
                        title = stringResource(R.string.preferences_auto_select_region_title),
                        summary = stringResource(R.string.preferences_auto_select_region_summary),
                        checked = state.autoSelectRegion,
                        onCheckedChange = actions.onAutoSelectRegion,
                    )
                }
            }

            PreferenceCategory(stringResource(R.string.preferences_category_display)) {
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_show_negative_arrivals_title),
                    summary = stringResource(R.string.preferences_show_negative_arrivals_summary),
                    checked = state.showNegativeArrivals,
                    onCheckedChange = actions.onShowNegativeArrivals,
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_hide_alerts_title),
                    summary = stringResource(R.string.preferences_hide_alerts_summary),
                    checked = state.hideAlerts,
                    onCheckedChange = actions.onHideAlerts,
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_show_zoom_controls_title),
                    summary = stringResource(R.string.preferences_show_zoom_controls_summary),
                    checked = state.showZoomControls,
                    onCheckedChange = actions.onShowZoomControls,
                )
                val mapOptions = stringArrayResource(R.array.preferred_map_options).toList()
                ListPreferenceItem(
                    title = stringResource(R.string.preferences_preferred_maps_title),
                    entries = mapOptions,
                    entryValues = mapOptions,
                    selectedValue = state.mapMode,
                    onValueSelected = actions.onMapMode,
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_show_weather_view),
                    summary = stringResource(R.string.preferences_show_weather_view_on_map),
                    checked = state.displayWeatherView,
                    onCheckedChange = actions.onDisplayWeatherView,
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_show_available_studies_title),
                    summary = stringResource(R.string.preferences_show_available_studies_body),
                    checked = state.showAvailableStudies,
                    onCheckedChange = actions.onShowAvailableStudies,
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_show_tutorial_screens_title),
                    summary = stringResource(R.string.preferences_show_tutorial_screens_summary),
                    checked = state.showTutorialScreens,
                    onCheckedChange = actions.onShowTutorialScreens,
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_left_hand_mode_title),
                    summary = stringResource(R.string.preferences_left_hand_mode_summary),
                    checked = state.leftHandMode,
                    onCheckedChange = actions.onLeftHandMode,
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_show_header_arrivals_title),
                    summary = stringResource(R.string.preferences_show_header_arrivals_summary),
                    checked = state.showHeaderArrivals,
                    onCheckedChange = actions.onShowHeaderArrivals,
                )
                val unitOptions = stringArrayResource(R.array.preferred_units_options).toList()
                ListPreferenceItem(
                    title = stringResource(R.string.preferences_preferred_units_title),
                    entries = unitOptions,
                    entryValues = unitOptions,
                    selectedValue = state.preferredUnits,
                    onValueSelected = actions.onPreferredUnits,
                )
                val tempUnitOptions = stringArrayResource(R.array.preferred_temp_unit_options).toList()
                ListPreferenceItem(
                    title = stringResource(R.string.preferred_temperature_unit),
                    entries = tempUnitOptions,
                    entryValues = tempUnitOptions,
                    selectedValue = state.preferredTempUnits,
                    onValueSelected = actions.onPreferredTempUnits,
                )
                val themeOptions = stringArrayResource(R.array.app_theme_options).toList()
                ListPreferenceItem(
                    title = stringResource(R.string.preferences_app_theme_title),
                    entries = themeOptions,
                    entryValues = themeOptions,
                    selectedValue = state.appTheme,
                    onValueSelected = actions.onAppTheme,
                )
            }

            if (state.showNotificationsCategory) {
                PreferenceCategory(stringResource(R.string.preferences_category_notifications)) {
                    ClickPreferenceItem(
                        title = stringResource(R.string.preferences_preferred_sound_title),
                        summary = stringResource(R.string.preferences_preferred_sound_summary, appName),
                        onClick = actions.onRingtoneClick,
                    )
                    SwitchPreferenceItem(
                        title = stringResource(R.string.preferences_preferred_vibration_title),
                        summary = stringResource(R.string.preferences_preferred_vibration_summary, appName),
                        checked = state.vibrateAllowed,
                        onCheckedChange = actions.onVibrateAllowed,
                    )
                    if (state.showTripPlanNotifications) {
                        SwitchPreferenceItem(
                            title = stringResource(R.string.preferences_trip_plan_notifications_title),
                            summary = stringResource(R.string.preferences_trip_plan_notifications_summary),
                            checked = state.tripPlanNotifications,
                            onCheckedChange = actions.onTripPlanNotifications,
                        )
                    }
                }
            }

            PreferenceCategory(stringResource(R.string.preferences_category_backup)) {
                ClickPreferenceItem(
                    title = stringResource(R.string.preferences_save_title),
                    summary = stringResource(R.string.preferences_save_summary),
                    onClick = actions.onSaveBackup,
                )
                ClickPreferenceItem(
                    title = stringResource(R.string.preferences_restore_title),
                    summary = stringResource(R.string.preferences_restore_summary, appName),
                    onClick = actions.onRestoreBackup,
                )
            }

            PreferenceCategory(stringResource(R.string.preferences_category_advanced)) {
                ClickPreferenceItem(
                    title = stringResource(R.string.preferences_category_advanced),
                    summary = stringResource(R.string.preferences_screen_advanced_summary),
                    onClick = actions.onAdvancedClick,
                )
            }

            PreferenceCategory(stringResource(R.string.preferences_category_about)) {
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_analytics_title),
                    summary = stringResource(R.string.preferences_analytics_summary),
                    checked = state.analyticsEnabled,
                    onCheckedChange = actions.onAnalytics,
                )
                ClickPreferenceItem(
                    title = stringResource(R.string.preferences_tutorial_title),
                    summary = stringResource(R.string.preferences_tutorial_summary),
                    onClick = actions.onTutorialClick,
                )
                if (state.showDonate) {
                    ClickPreferenceItem(
                        title = stringResource(R.string.preferences_donate_title),
                        summary = stringResource(R.string.preferences_donate_summary, appName),
                        onClick = actions.onDonateClick,
                    )
                }
                if (state.showPoweredByOba) {
                    ClickPreferenceItem(
                        title = stringResource(R.string.preferences_powered_by_oba_title, appName),
                        summary = stringResource(R.string.preferences_powered_by_oba_summary),
                        onClick = actions.onPoweredByObaClick,
                    )
                }
                ClickPreferenceItem(
                    title = stringResource(R.string.preferences_about_title),
                    summary = stringResource(R.string.preferences_about_summary),
                    onClick = actions.onAboutClick,
                )
            }

            if (state.showUserLogsCategory) {
                PreferenceCategory(stringResource(R.string.preferences_category_user_logs)) {
                    SwitchPreferenceItem(
                        title = stringResource(R.string.preferences_user_share_destination_logs_title),
                        summary = stringResource(
                            R.string.preferences_user_share_destination_logs_summary, appName
                        ),
                        checked = state.shareDestinationLogs,
                        onCheckedChange = actions.onShareDestinationLogs,
                    )
                }
            }
        }
    }
}

/** Builds the system ringtone-picker intent, pre-selecting [existingValue] if set (ported verbatim). */
private fun buildRingtonePickerIntent(existingValue: String?): Intent {
    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
    intent.putExtra(
        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    )
    if (existingValue != null) {
        val existingUri = if (existingValue.isEmpty()) null else Uri.parse(existingValue)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
    }
    return intent
}
