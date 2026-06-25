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
package org.onebusaway.android.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.ListUiState

/**
 * Scaffold for a fetch-once list screen: a back-navigation [TopAppBar] over a body driven by
 * [ListUiState] (centered spinner / list / error-with-retry). Rows differ per screen, so the
 * caller supplies [itemContent]; an [emptyContent] renders when a successful load has no items.
 *
 * @param actions optional [TopAppBar] action items (e.g. a refresh button)
 * @param emptyContent shown centered in the body on Success with an empty list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ListScreenScaffold(
    title: String,
    onBack: () -> Unit,
    state: ListUiState<T>,
    onRetry: () -> Unit,
    itemKey: (T) -> Any,
    actions: @Composable RowScope.() -> Unit = {},
    emptyContent: @Composable BoxScope.() -> Unit = {},
    itemContent: @Composable (T) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                },
                actions = actions
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                ListUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))

                is ListUiState.Success -> if (state.items.isEmpty()) {
                    emptyContent()
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.items, key = itemKey) { itemContent(it) }
                    }
                }

                ListUiState.Error -> ErrorContent(onRetry, Modifier.align(Alignment.Center))
            }
        }
    }
}
