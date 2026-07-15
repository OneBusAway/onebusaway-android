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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.mylists.RecentItem
import org.onebusaway.android.ui.mylists.filterRecents

/**
 * Home's floating map chrome that replaces the old solid `TopAppBar`: the map now runs edge-to-edge and
 * these controls float over its top edge (mirroring the bottom [org.onebusaway.android.ui.home.map.MapChrome]
 * FABs). A menu (☰) FAB in the top-start corner opens the drawer, and an always-editable search field fills
 * the rest of the row — it reads as a text field, not a button, so there is no button-to-field flip: tapping
 * it focuses the field directly. When focused, the field expands into a connected surface hosting a
 * [SearchRecentsDropdown] of the user's [recents] (filtered live by the query); tapping a row fires
 * [onRecentStop] / [onRecentRoute], while submitting still calls [onSearch] (the full `ACTION_SEARCH` →
 * `SearchActivity`), so recents are a shortcut distinct from a full search.
 *
 * The caller supplies the status-bar inset (this composable adds only its own FAB margins) and the
 * [recents] + tap lambdas, so it stays a pure, VM-free function of state. The menu FAB carries the
 * my-location FAB's accent branding (white on [R.color.theme_accent] green); the search field stays a
 * neutral light surface so the query reads clearly.
 */
@Composable
fun MapTopChrome(
    onOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    // The unified recent stops+routes list for the search dropdown, and the row-tap handlers.
    recents: List<RecentItem>,
    onRecentStop: (id: String, name: String?) -> Unit,
    onRecentRoute: (routeId: String) -> Unit,
    // Opaque anchor a host may attach to the menu (☰) FAB (e.g. for an onboarding spotlight).
    menuModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val margin = dimensionResource(R.dimen.fab_margin_horizontal)
    val focusManager = LocalFocusManager.current
    // The search field's focus is owned here so a full-screen tap-catcher can sit behind the chrome and
    // dismiss the field/dropdown on any tap outside it.
    var searchFocused by remember { mutableStateOf(false) }
    // System back collapses the field (dismisses the keyboard + dropdown) instead of leaving the screen.
    BackHandler(enabled = searchFocused) { focusManager.clearFocus() }

    Box(modifier.fillMaxSize()) {
        // While focused, an invisible full-screen catcher behind the chrome clears focus on any tap
        // outside the field/dropdown (which are drawn above it) — defocusing collapses the dropdown.
        if (searchFocused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = margin)
                .padding(top = TOP_CHROME_TOP_MARGIN),
            horizontalArrangement = Arrangement.spacedBy(margin),
            // Top-aligned so the menu FAB stays put at the top of the row as the search field expands its
            // dropdown downward (rather than re-centering against the taller field).
            verticalAlignment = Alignment.Top,
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
                recents = recents,
                focused = searchFocused,
                onFocusChange = { searchFocused = it },
                // Leaving search (submit / recents tap) clears focus first, so the field collapses and
                // returning to HOME doesn't strand it expanded.
                onSubmit = { query ->
                    focusManager.clearFocus()
                    onSearch(query)
                },
                onRecentStop = { id, name ->
                    focusManager.clearFocus()
                    onRecentStop(id, name)
                },
                onRecentRoute = { routeId ->
                    focusManager.clearFocus()
                    onRecentRoute(routeId)
                },
                onDismiss = { focusManager.clearFocus() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The always-visible search field: a floating rounded pill with a leading search glyph, an inline query
 * field (never autofocused, so the keyboard only appears once the user taps it), and a trailing X that
 * clears the query when there's text and otherwise (on a focused, blank field) dismisses the search.
 * Submitting via the IME "search" action forwards a non-blank, trimmed query to [onSubmit].
 *
 * When [focused] with matching [recents], the pill morphs into a connected rounded surface that hosts the
 * [SearchRecentsDropdown] beneath the query row (one surface, one shadow — so the field + dropdown read as
 * a single expanded control). Tapping a recent row fires [onRecentStop] / [onRecentRoute]; submitting runs
 * a full search (distinct from the recents). Focus is owned by the caller ([MapTopChrome]), which reports
 * changes via [onFocusChange] and drives collapse (clearing focus) on submit / recents tap / tap-outside.
 */
@Composable
private fun SearchField(
    recents: List<RecentItem>,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onRecentStop: (id: String, name: String?) -> Unit,
    onRecentRoute: (routeId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    // Shown as the visual placeholder AND set as the field's accessibility label — decorationBox text
    // alone isn't exposed to screen readers, so BasicTextField needs the label in semantics too.
    val hint = stringResource(R.string.search_hint)

    // The recents to show while focused: the full list when blank, live-filtered by the query otherwise.
    // Expand only when there's something to show — a query with no recent match just relies on submit.
    val shown = remember(recents, query) { filterRecents(recents, query) }
    val expanded = focused && shown.isNotEmpty()

    // Only the bottom corners morph: a full pill when collapsed, with its bottom squared slightly when it
    // expands so it reads as one connected surface with the dropdown beneath it.
    val pillRadius = TOP_CHROME_HEIGHT / 2
    val bottomRadius = if (expanded) EXPANDED_BOTTOM_RADIUS else pillRadius
    val shape = RoundedCornerShape(
        topStart = pillRadius, topEnd = pillRadius,
        bottomStart = bottomRadius, bottomEnd = bottomRadius,
    )

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOP_CHROME_HEIGHT)
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
                        .onFocusChanged { onFocusChange(it.isFocused) }
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
                // The X shows whenever the field is active — there's text, or the field is focused with the
                // recents list showing. It clears the query when there's text; on an already-blank field
                // (recents showing) the same X dismisses the search (clears focus, collapsing the dropdown).
                // The glyph is the same either way (per design); the a11y label tracks the actual action.
                if (query.isNotEmpty() || expanded) {
                    val hasText = query.isNotEmpty()
                    IconButton(onClick = { if (hasText) query = "" else onDismiss() }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(
                                if (hasText) R.string.stop_info_clear else R.string.search_close
                            ),
                        )
                    }
                }
            }
            if (expanded) {
                SearchRecentsDropdown(
                    recents = shown,
                    onRecentStop = onRecentStop,
                    onRecentRoute = onRecentRoute,
                )
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

// The search field's bottom-corner radius while expanded: squared off from the full pill so it connects
// to the recents dropdown beneath it.
private val EXPANDED_BOTTOM_RADIUS = 20.dp

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

/**
 * The floating top chrome's total vertical footprint in **pixels**: the caller-measured [statusBarTopPx]
 * plus [MAP_TOP_CHROME_CLEARANCE]. Fed into the map's top content padding so the Google compass and any
 * centered content clear the menu/search FAB row. Shares [MAP_TOP_CHROME_CLEARANCE] with
 * [mapTopChromeOverlayInset], so the map padding can't drift from the overlays that line up against the
 * same row. Deliberately not `@Composable`: the caller reads the snapshot-backed status-bar inset inside
 * a `snapshotFlow` so inset changes don't recompose the map.
 */
fun mapTopChromeInsetPx(statusBarTopPx: Int, density: Density): Int =
    statusBarTopPx + with(density) { MAP_TOP_CHROME_CLEARANCE.roundToPx() }
