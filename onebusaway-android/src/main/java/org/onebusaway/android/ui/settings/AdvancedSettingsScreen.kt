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

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.settings.components.ClickPreferenceItem
import org.onebusaway.android.ui.settings.components.EditTextPreferenceItem
import org.onebusaway.android.ui.settings.components.PreferenceCategory
import org.onebusaway.android.ui.settings.components.SwitchPreferenceItem

/**
 * The advanced settings NavHost destination ([org.onebusaway.android.ui.NavRoutes.SETTINGS_ADVANCED]).
 * The stateful [AdvancedSettingsRoute] owns the experimental-region confirmation dialogs, the invalid-URL
 * toasts, and the host-bound effects; the stateless [AdvancedSettingsScreen] renders the rows.
 */
@Composable
fun AdvancedSettingsRoute(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: AdvancedSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context.findActivity()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AdvancedSettingsEffect.GoHome -> onGoHome()
            }
        }
    }

    // Experimental-regions toggle: confirm before enabling, and before disabling while on an
    // experimental region (reproduces the old ExperimentalRegionsPreference dialogs).
    val onExperimentalToggle: (Boolean) -> Unit = { enabled ->
        when {
            enabled -> MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.preferences_experimental_regions_enable_warning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.setExperimentalRegions(enabled = true, regionWasExperimental = false)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

            state.currentRegionIsExperimental -> MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.preferences_experimental_regions_disable_warning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.setExperimentalRegions(enabled = false, regionWasExperimental = true)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

            else -> viewModel.setExperimentalRegions(enabled = false, regionWasExperimental = false)
        }
    }

    val appName = stringResource(R.string.app_name)
    AdvancedSettingsScreen(
        state = state,
        onBack = onBack,
        onExperimentalToggle = onExperimentalToggle,
        onDisplayTestAlerts = viewModel::onDisplayTestAlertsChanged,
        onObaUrlChange = { url ->
            when (viewModel.onCustomObaApiUrlChanged(url)) {
                UrlChangeResult.InvalidObaUrl -> {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.custom_api_url_error, appName),
                        Toast.LENGTH_SHORT,
                    ).show()
                    false
                }

                else -> true
            }
        },
        onOtpUrlChange = { url ->
            when (viewModel.onCustomOtpApiUrlChanged(url)) {
                UrlChangeResult.InvalidOtpUrl -> {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.custom_otp_api_url_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                    false
                }

                else -> true
            }
        },
        onPushFirebaseData = viewModel::onPushFirebaseData,
        onResetDonationTimestamps = viewModel::onResetDonationTimestamps,
    )
}

@Composable
fun AdvancedSettingsScreen(
    state: AdvancedSettingsUiState,
    onBack: () -> Unit,
    onExperimentalToggle: (Boolean) -> Unit,
    onDisplayTestAlerts: (Boolean) -> Unit,
    onObaUrlChange: (String) -> Boolean,
    onOtpUrlChange: (String) -> Boolean,
    onPushFirebaseData: () -> Unit,
    onResetDonationTimestamps: () -> Unit,
) {
    val appName = stringResource(R.string.app_name)
    Scaffold(
        topBar = {
            ObaTopAppBar(
                title = stringResource(R.string.preferences_category_advanced),
                onBack = onBack,
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            PreferenceCategory(stringResource(R.string.preferences_category_advanced)) {
                if (state.showExperimentalRegions) {
                    SwitchPreferenceItem(
                        title = stringResource(R.string.preferences_experimental_regions_title),
                        summary = stringResource(R.string.preferences_experimental_regions_summary),
                        checked = state.experimentalRegionsEnabled,
                        onCheckedChange = onExperimentalToggle,
                    )
                }
                SwitchPreferenceItem(
                    title = stringResource(R.string.display_test_alerts),
                    summary = stringResource(R.string.display_test_wide_alerts_for_regions),
                    checked = state.displayTestAlerts,
                    onCheckedChange = onDisplayTestAlerts,
                )
                EditTextPreferenceItem(
                    title = stringResource(R.string.preferences_oba_api_servername_title, appName),
                    summary = state.customObaApiUrlSummary,
                    currentValue = state.customObaApiUrl,
                    hint = stringResource(R.string.preferences_oba_api_servername_hint),
                    onValueChange = onObaUrlChange,
                )
                EditTextPreferenceItem(
                    title = stringResource(R.string.preferences_otp_api_servername_title),
                    summary = state.customOtpApiUrlSummary,
                    currentValue = state.customOtpApiUrl,
                    hint = stringResource(R.string.preferences_otp_api_servername_hint),
                    onValueChange = onOtpUrlChange,
                )
                ClickPreferenceItem(
                    title = stringResource(R.string.preferences_push_firebase_data_title),
                    summary = stringResource(R.string.preferences_push_firebase_data_summary),
                    onClick = onPushFirebaseData,
                )
                ClickPreferenceItem(
                    title = stringResource(R.string.preferences_reset_donation_timestamps_title),
                    summary = stringResource(R.string.preferences_reset_donation_timestamps_summary),
                    onClick = onResetDonationTimestamps,
                )
            }
        }
    }
}
