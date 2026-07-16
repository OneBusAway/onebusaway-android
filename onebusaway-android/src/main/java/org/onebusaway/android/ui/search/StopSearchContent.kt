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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.StopRowContent

/** The stop search tab: search box + results. Tapping a row reveals that stop on the map. */
@Composable
fun StopSearchContent(
    viewModel: SearchViewModel<StopSearchResult>,
    onStopClick: (StopSearchResult) -> Unit,
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
        StopRowContent(
            name = stop.name,
            direction = stop.direction,
            isFavorite = stop.isFavorite,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onStopClick(stop) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
