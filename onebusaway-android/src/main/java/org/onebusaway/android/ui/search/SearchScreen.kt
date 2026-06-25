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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.LoadingContent

/**
 * Shared incremental-search screen: a search box above a state-driven body. Embedded inside
 * the My Routes / My Stops tab activities, so there is no app bar here.
 *
 * @param idleHint hint shown when the search box is empty (what to type and why)
 * @param itemContent row content for one search result
 */
@Composable
fun <T> SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    searchHint: String,
    idleHint: String,
    state: SearchUiState<T>,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(searchHint) },
            singleLine = true,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.stop_info_clear)
                        )
                    }
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(Modifier.fillMaxSize()) {
            when (state) {
                SearchUiState.Idle -> HintText(idleHint)

                SearchUiState.Searching -> LoadingContent(
                    Modifier.align(Alignment.Center)
                )

                is SearchUiState.Results -> if (state.items.isEmpty()) {
                    HintText(stringResource(R.string.find_hint_noresults))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.items, key = itemKey) { item ->
                            itemContent(item)
                        }
                    }
                }

                SearchUiState.Error -> HintText(stringResource(R.string.generic_comm_error))
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    )
}
