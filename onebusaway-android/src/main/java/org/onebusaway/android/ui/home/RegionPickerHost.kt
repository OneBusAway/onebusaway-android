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

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.region.Region

/**
 * Renders the forced-choice region picker when [RegionPickerViewModel] reports the repository needs a manual
 * selection, or a retryable "couldn't load regions" dialog when resolution failed catastrophically. Hosted
 * at the activity's setContent root (a sibling of the NavHost) so the dialogs — each in their own window —
 * overlay whatever screen triggered the refresh (Home on cold launch, or the Advanced settings screen when
 * the experimental-regions toggle forces a re-resolve).
 */
@Composable
fun RegionPickerHost() {
    val viewModel: RegionPickerViewModel = hiltViewModel()
    val regions by viewModel.picker.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    // NeedsManualChoice and Failed are mutually exclusive repository states, so at most one shows.
    // The region pending removal confirmation, if the rider long-pressed a custom one.
    var removing by remember { mutableStateOf<Region?>(null) }

    regions?.let { RegionChooserDialog(it, viewModel::choose, onRemoveCustom = { removing = it }) }
    if (failed) RegionLoadFailedDialog(onRetry = viewModel::retry)
    removing?.let { region ->
        RemoveCustomRegionDialog(
            region = region,
            onConfirm = {
                removing = null
                viewModel.remove(region)
            },
            onDismiss = { removing = null }
        )
    }
}

/**
 * Confirms removing a custom region (#2027). Separate from the picker's own dialog so a mis-hit
 * long-press can't delete anything, and worded to make clear only the rider's own added region goes —
 * the directory regions in the same list are untouched.
 */
@Composable
private fun RemoveCustomRegionDialog(region: Region, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.region_remove_custom_title, region.name)) },
        text = { Text(stringResource(R.string.region_remove_custom_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.region_remove_custom_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * The catastrophic-failure affordance (no network, no cache, server unreachable): a dialog explaining the
 * app couldn't load region info with a Retry that re-attempts resolution. Dismissible (back/scrim) unlike
 * the forced picker — a cached region may still let the app function, so we don't hard-block the user.
 */
@Composable
private fun RegionLoadFailedDialog(onRetry: () -> Unit) {
    // Dismiss simply drops the dialog for this attempt; the state re-surfaces on the next failed refresh.
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.region_load_failed_title)) },
        text = { Text(stringResource(R.string.region_load_failed_message)) },
        confirmButton = {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.dismiss)) }
        }
    )
}

/**
 * The forced-choice region picker (old ObaRegionsTask.haveUserChooseRegion): a non-dismissible dialog of
 * usable regions (pre-filtered + sorted by the repository). The user must pick one — there is no cancel, and
 * back/scrim do nothing, since the app can't function without a region.
 */
@Composable
private fun RegionChooserDialog(
    regions: List<Region>,
    onRegionChosen: (Region) -> Unit,
    onRemoveCustom: (Region) -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.region_choose_region)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Read once for the whole list, not once per row: at most one row is custom.
                val removeHint = stringResource(R.string.region_remove_custom_hint)
                regions.forEach { region ->
                    RegionRow(region, removeHint, onRegionChosen, onRemoveCustom)
                }
            }
        },
        confirmButton = { }
    )
}

/**
 * One region in the picker. A custom region the rider added (#2027) additionally long-presses to remove
 * — the only way back out of a bad `add-region` link short of clearing app data. Directory regions have
 * no long-press, so there is nothing to mis-hit on the rows the rider can't delete anyway.
 */
@Composable
private fun RegionRow(
    region: Region,
    removeHint: String,
    onRegionChosen: (Region) -> Unit,
    onRemoveCustom: (Region) -> Unit
) {
    Text(
        text = region.name,
        modifier = Modifier
            .fillMaxWidth()
            // A null onLongClick behaves exactly like clickable, and onLongClickLabel is what announces
            // the gesture to a screen reader — for which it is the only route to removal.
            .combinedClickable(
                onClick = { onRegionChosen(region) },
                onLongClickLabel = removeHint.takeIf { region.custom },
                onLongClick = if (region.custom) ({ onRemoveCustom(region) }) else null
            )
            .padding(vertical = 16.dp)
    )
}
