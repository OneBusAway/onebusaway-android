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
// `menuModifier` is a named anchor a host attaches to the menu (☰) FAB for the onboarding spotlight —
// a sub-element modifier, not the chrome's root `modifier` — so ModifierParameter's "name it modifier"
// rule doesn't apply here.
@file:Suppress("ModifierParameter")

package org.onebusaway.android.ui.home.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

/**
 * Home's floating map chrome that replaces the old solid `TopAppBar`: the map now runs edge-to-edge and
 * these controls float over its top edge (mirroring the bottom [org.onebusaway.android.ui.home.map.MapChrome]
 * FABs). A menu (☰) FAB in the top-start corner opens the drawer (where "Recent stops/routes" lives), and
 * an always-editable search field fills the rest of the row — it reads as a text field, not a button, so
 * there is no button-to-field flip: tapping it focuses the field directly. Submitting calls [onSearch]
 * (which fires the legacy `ACTION_SEARCH` → `SearchActivity`).
 *
 * The caller supplies the status-bar inset (this composable adds only its own FAB margins), so it stays a
 * pure function of state. The menu FAB carries the my-location FAB's accent branding (white on
 * [R.color.theme_accent] green); the search field stays a neutral light surface so the query reads clearly.
 */
@Composable
fun MapTopChrome(
    onOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    // Opaque anchor a host may attach to the menu (☰) FAB (e.g. for an onboarding spotlight).
    menuModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val margin = dimensionResource(R.dimen.fab_margin_horizontal)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = margin)
            .padding(top = TOP_CHROME_TOP_MARGIN),
        horizontalArrangement = Arrangement.spacedBy(margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingActionButton(
            onClick = onOpenDrawer,
            containerColor = colorResource(R.color.theme_accent),
            contentColor = Color.White,
            modifier = menuModifier.size(TOP_CHROME_HEIGHT),
        ) {
            Icon(Icons.Default.Menu, stringResource(R.string.navigation_drawer_open))
        }
        SearchField(
            onSubmit = onSearch,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The always-visible search field: a floating rounded pill with a leading search glyph, an inline query
 * field (never autofocused, so the keyboard only appears once the user taps it), and a clear button that
 * shows once there is text. Submitting via the IME "search" action forwards a non-blank, trimmed query to
 * [onSubmit].
 */
@Composable
private fun SearchField(
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    // Shown as the visual placeholder AND set as the field's accessibility label — decorationBox text
    // alone isn't exposed to screen readers, so BasicTextField needs the label in semantics too.
    val hint = stringResource(R.string.search_hint)
    Surface(
        modifier = modifier.height(TOP_CHROME_HEIGHT),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.map_option_search),
                tint = LocalContentColor.current.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.isNotBlank()) onSubmit(query.trim()) }
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = hint },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalContentColor.current.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Clear, stringResource(R.string.stop_info_clear))
                }
            }
        }
    }
}

// The floating chrome sits this far below the status-bar inset the caller supplies; the horizontal margin
// + the menu-FAB-to-field gap reuse @dimen/fab_margin_horizontal, matching the bottom chrome.
private val TOP_CHROME_TOP_MARGIN = 8.dp

// Both the menu FAB and the search field are sized to this height — ~90% of the default 56dp FAB, so the
// top chrome sits a touch more compactly over the map.
private val TOP_CHROME_HEIGHT = 50.dp

/**
 * Vertical footprint of the floating top chrome below the status-bar inset: its top margin + the row
 * height + a small gap. Top-of-map overlays (in HomeScreen and MapFeature) offset by this so they clear
 * the menu FAB / search field — the single source of truth, so resizing the row re-clears every overlay.
 */
internal val MAP_TOP_CHROME_CLEARANCE = TOP_CHROME_TOP_MARGIN + TOP_CHROME_HEIGHT + 8.dp

/**
 * Insets a top-of-map overlay layer to sit below the floating chrome: the status-bar inset plus
 * [MAP_TOP_CHROME_CLEARANCE]. Both HomeScreen (its overlay container) and MapFeature (the stops-notice
 * pill) apply this one modifier, so the status-bar + clearance handling can't drift between them.
 */
fun Modifier.mapTopChromeOverlayInset(): Modifier =
    this.statusBarsPadding().padding(top = MAP_TOP_CHROME_CLEARANCE)
