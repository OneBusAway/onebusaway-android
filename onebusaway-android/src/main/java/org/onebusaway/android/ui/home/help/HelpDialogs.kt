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

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.EtaPill

/**
 * The help-menu options, in the order of the `main_help_options` string-array. Dialog-opening actions
 * (legend / what's-new) are handled by [HelpViewModel]; the rest are Activity operations the host
 * carries out via the `onHelpAction` callback.
 */
enum class HelpAction { TUTORIALS, LEGEND, WHATS_NEW, AGENCIES, TWITTER, CONTACT_US }

/**
 * Self-rendering help feature module: draws the help menu / what's-new / legend dialogs from
 * [HelpViewModel] state, and auto-shows "What's New" once a region has resolved ([regionReady]). Legend
 * + what's-new taps transition the VM's dialog; the other menu actions (reset tutorials, agencies,
 * Twitter, contact us) are genuine Activity operations, forwarded through [onHelpAction]. The
 * tutorial opt-out's "yes" path shows the welcome tutorial via [onShowWelcomeTutorial].
 */
@Composable
fun HelpFeature(
    viewModel: HelpViewModel,
    onHelpAction: (HelpAction) -> Unit,
    onShowWelcomeTutorial: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val regionReady by viewModel.regionReady.collectAsStateWithLifecycle()
    // Auto-show "What's New" once a region has resolved (its content may need refreshed Regions API
    // data — the old onRegionResolved poke). maybeAutoShowWhatsNew is self-gating (shows at most once
    // per app-version bump), so re-running on recomposition / config change is safe.
    LaunchedEffect(regionReady) {
        if (regionReady) {
            viewModel.maybeAutoShowWhatsNew()
        }
    }
    when (state.dialog) {
        HelpDialog.Menu -> HelpMenuDialog(
            showContactUs = state.showContactUs,
            onItem = { action ->
                when (action) {
                    HelpAction.LEGEND -> viewModel.showLegend()
                    HelpAction.WHATS_NEW -> viewModel.showWhatsNew()
                    else -> {
                        viewModel.dismiss()
                        onHelpAction(action)
                    }
                }
            },
            onDismiss = viewModel::dismiss,
        )
        HelpDialog.WhatsNew -> WhatsNewDialog(
            onDismiss = {
                viewModel.dismiss()
                viewModel.maybeShowTutorialOptOut()
            }
        )
        HelpDialog.TutorialOptOut -> TutorialOptOutDialog(
            onYes = { viewModel.setTutorialsEnabled(true); onShowWelcomeTutorial() },
            onNo = { viewModel.setTutorialsEnabled(false) },
            onDismiss = viewModel::dismiss,
        )
        HelpDialog.Legend -> LegendDialog(onDismiss = viewModel::dismiss)
        HelpDialog.None -> Unit
    }
}

@Composable
private fun HelpMenuDialog(
    showContactUs: Boolean,
    onItem: (HelpAction) -> Unit,
    onDismiss: () -> Unit
) {
    val options = stringArrayResource(
        if (showContactUs) R.array.main_help_options else R.array.main_help_options_no_contact_us
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItem(HelpAction.entries[index]) }
                            .padding(vertical = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_help_close)) }
        }
    )
}

@Composable
private fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_help_whatsnew_title)) },
        text = {
            Text(
                text = stringResource(R.string.main_help_whatsnew),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_help_close)) }
        }
    )
}

@Composable
private fun TutorialOptOutDialog(onYes: () -> Unit, onNo: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tutorial_opt_out_dialog_title)) },
        text = {
            Text(stringResource(R.string.tutorial_opt_out_dialog_text, stringResource(R.string.app_name)))
        },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.rt_yes)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.rt_no)) } }
    )
}

/**
 * The arrival-color legend. Each row reuses the drawer peek's [EtaPill] (white text on the deviation
 * color, with the pulsing real-time dot) so the sample matches a stop's ETA. Order mirrors the legacy
 * legend: on-time / early / late / scheduled / canceled.
 */
@Composable
private fun LegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_help_legend_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LegendRow(R.color.stop_info_ontime, predicted = true, label = R.string.main_help_legend_ontime)
                LegendRow(R.color.stop_info_early, predicted = true, label = R.string.main_help_legend_early)
                LegendRow(R.color.stop_info_delayed, predicted = true, label = R.string.main_help_legend_late)
                LegendRow(R.color.stop_info_scheduled_time, predicted = false, label = R.string.main_help_legend_scheduled)
                LegendRow(
                    R.color.stop_info_scheduled_time,
                    predicted = false,
                    canceled = true,
                    label = R.string.main_help_legend_canceled
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.main_help_close)) }
        }
    )
}

@Composable
private fun LegendRow(
    @ColorRes color: Int,
    predicted: Boolean,
    @StringRes label: Int,
    canceled: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EtaPill(eta = 5L, color = colorResource(color), predicted = predicted, canceled = canceled)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}
