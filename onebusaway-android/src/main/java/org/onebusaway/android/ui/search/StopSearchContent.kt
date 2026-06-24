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
package org.onebusaway.android.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.MenuHeader
import org.onebusaway.android.ui.compose.components.StopRowContent

/** The stop search tab: search box + results, with a long-press menu per row. */
@Composable
fun StopSearchContent(
    viewModel: SearchViewModel<StopSearchResult>,
    onStopClick: (StopSearchResult) -> Unit,
    onShowOnMap: (StopSearchResult) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        onQueryChange = viewModel::onQueryChange,
        searchHint = stringResource(R.string.search_stop_hint),
        idleHint = stringResource(R.string.find_hint_nofavoritestops),
        state = state,
        itemKey = { it.id }
    ) { stop ->
        StopSearchRow(
            stop = stop,
            onStopClick = onStopClick,
            onShowOnMap = onShowOnMap
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopSearchRow(
    stop: StopSearchResult,
    onStopClick: (StopSearchResult) -> Unit,
    onShowOnMap: (StopSearchResult) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        StopRowContent(
            name = stop.name,
            direction = stop.direction,
            isFavorite = stop.isFavorite,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onStopClick(stop) },
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            MenuHeader(stop.name)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.my_context_get_stop_info)) },
                onClick = {
                    menuExpanded = false
                    onStopClick(stop)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.my_context_showonmap)) },
                onClick = {
                    menuExpanded = false
                    onShowOnMap(stop)
                }
            )
        }
    }
}
