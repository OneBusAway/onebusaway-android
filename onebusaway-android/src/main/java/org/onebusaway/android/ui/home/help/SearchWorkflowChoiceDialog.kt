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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.MigrationChoice
import org.onebusaway.android.ui.compose.components.MigrationDialog
import org.onebusaway.android.ui.searchresults.SearchResultMode

/** Offline illustrations show the two search destinations without requesting tiles or transit data. */
@Composable
internal fun SearchWorkflowChoiceDialog(
    onSave: (SearchResultMode) -> Unit,
    onDismiss: () -> Unit,
    page: Int = 1,
    pageCount: Int = 1,
    initial: SearchResultMode = SearchResultMode.LISTS
) {
    var selected by rememberSaveable(initial) { mutableStateOf(initial) }
    MigrationDialog(
        title = stringResource(R.string.search_result_mode_migration_title),
        page = page,
        pageCount = pageCount,
        onContinue = { onSave(selected) },
        onDismiss = onDismiss,
        onBack = null,
        content = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(AnnotatedString.fromHtml(stringResource(R.string.search_result_mode_migration_body)))
                listOf(SearchResultMode.LISTS, SearchResultMode.MAP).forEach { mode ->
                    MigrationChoice(
                        title = stringResource(if (mode == SearchResultMode.MAP) R.string.search_result_mode_migration_map else R.string.search_result_mode_migration_lists),
                        description = stringResource(if (mode == SearchResultMode.MAP) R.string.search_result_mode_map_description else R.string.search_result_mode_lists_description),
                        previous = mode == SearchResultMode.LISTS,
                        selected = selected == mode,
                        onSelect = { selected = mode },
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SearchWorkflowPreview(mode, onSelect = { selected = mode })
                    }
                }
            }
        }
    )
}
