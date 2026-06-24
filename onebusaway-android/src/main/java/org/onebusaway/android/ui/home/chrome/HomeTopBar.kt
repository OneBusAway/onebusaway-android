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
// `overflowModifier` is a named anchor a host attaches to the overflow (⋮) button for the onboarding
// spotlight — a sub-element modifier, not the bar's root `modifier` — so ModifierParameter's
// "name it modifier" rule doesn't apply here.
@file:Suppress("ModifierParameter")

package org.onebusaway.android.ui.home.chrome

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import org.onebusaway.android.R

/**
 * Home's Material3 top bar, replacing the hosted `MaterialToolbar` + options menu. It toggles between
 * two modes:
 *  - **idle** — the [title] (the selected nav item) with a hamburger that opens the drawer, plus a
 *    search action, a Sort action (when [showSort]), and an overflow with "Recent stops/routes" and a
 *    Clear item (when [showClear]).
 *  - **search** — tapping search flips the whole bar into an autofocused text field; submitting calls
 *    [onSearch] (which fires the legacy `ACTION_SEARCH` → `SearchActivity`); the back arrow exits.
 *
 * Sort/Clear and the per-tab [clearLabel] are gated by the caller from `HomeUiState`, so the bar stays
 * a pure function of state. Container color matches [org.onebusaway.android.ui.compose.components.ObaTopAppBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    title: String,
    showSort: Boolean,
    showClear: Boolean,
    @StringRes clearLabel: Int,
    onOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    onSort: () -> Unit,
    onClear: () -> Unit,
    onRecentStopsRoutes: () -> Unit,
    // Opaque anchor a host may attach to the overflow (⋮) button (e.g. for an onboarding spotlight).
    overflowModifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(false) }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
    if (searching) {
        SearchTopBar(
            colors = colors,
            onClose = { searching = false },
            onSubmit = { query ->
                searching = false
                onSearch(query)
            }
        )
    } else {
        TopAppBar(
            title = { Text(title) },
            colors = colors,
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, stringResource(R.string.navigation_drawer_open))
                }
            },
            actions = {
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Default.Search, stringResource(R.string.map_option_search))
                }
                if (showSort) {
                    IconButton(onClick = onSort) {
                        Icon(
                            painterResource(R.drawable.ic_action_content_sort),
                            stringResource(R.string.menu_option_sort_by)
                        )
                    }
                }
                HomeOverflow(showClear, clearLabel, onClear, onRecentStopsRoutes, overflowModifier)
            }
        )
    }
}

/** The overflow (⋮): always "Recent stops/routes", plus the per-tab Clear item on the starred tabs. */
@Composable
private fun HomeOverflow(
    showClear: Boolean,
    @StringRes clearLabel: Int,
    onClear: () -> Unit,
    onRecentStopsRoutes: () -> Unit,
    overflowModifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = overflowModifier) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.my_recent_menu_title)) },
                onClick = {
                    expanded = false
                    onRecentStopsRoutes()
                }
            )
            if (showClear) {
                DropdownMenuItem(
                    text = { Text(stringResource(clearLabel)) },
                    onClick = {
                        expanded = false
                        onClear()
                    }
                )
            }
        }
    }
}

/** The search-mode bar: a close arrow, an autofocused query field, and a clear button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    colors: TopAppBarColors,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        colors = colors,
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
            }
        },
        title = {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = LocalContentColor.current),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.isNotBlank()) onSubmit(query.trim()) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.titleLarge,
                                color = LocalContentColor.current.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Clear, stringResource(R.string.stop_info_clear))
                }
            }
        }
    )
}
