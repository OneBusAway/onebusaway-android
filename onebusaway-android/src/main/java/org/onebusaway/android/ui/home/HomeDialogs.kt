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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.onebusaway.android.R
import org.onebusaway.android.io.elements.ObaRegion

/**
 * Which shared Home dialog is showing. The help / what's-new / legend dialogs are their own feature
 * module now (see [org.onebusaway.android.ui.home.help.HelpViewModel] / [org.onebusaway.android.ui.home.help.HelpFeature]); only the forced-choice region picker remains here.
 */
sealed interface HomeDialog {
    object None : HomeDialog

    /** The forced-choice region picker (old ObaRegionsTask.haveUserChooseRegion), keyed by [regions]. */
    data class ChooseRegion(val regions: List<ObaRegion>) : HomeDialog
}

/** Renders the shared Home dialogs, keyed by [HomeUiState.dialog] — just the region picker. */
@Composable
fun HomeDialogs(dialog: HomeDialog, onRegionChosen: (ObaRegion) -> Unit) {
    when (dialog) {
        is HomeDialog.ChooseRegion -> RegionChooserDialog(dialog.regions, onRegionChosen)
        HomeDialog.None -> Unit
    }
}

/**
 * The forced-choice region picker (old ObaRegionsTask.haveUserChooseRegion): a non-dismissible
 * dialog of usable regions (pre-filtered + sorted by the repository). The user must pick one — there
 * is no cancel, and back/scrim do nothing, since the app can't function without a region.
 */
@Composable
private fun RegionChooserDialog(regions: List<ObaRegion>, onRegionChosen: (ObaRegion) -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.region_choose_region)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                regions.forEach { region ->
                    Text(
                        text = region.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRegionChosen(region) }
                            .padding(vertical = 16.dp)
                    )
                }
            }
        },
        confirmButton = { }
    )
}
