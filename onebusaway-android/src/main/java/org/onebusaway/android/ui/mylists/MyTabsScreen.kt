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
package org.onebusaway.android.ui.mylists

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.ObaTopAppBar

/** A labelled overflow action (the per-tab "Clear" item) for [MyTab]. */
data class TabAction(@StringRes val labelRes: Int, val onClick: () -> Unit)

/**
 * One tab in a [MyTabsScreen]: its stable [tag] (deep-link / last-tab persistence), [titleRes] +
 * [iconRes] label, an optional Sort action ([onSort], rendered as a toolbar icon) and Clear action
 * ([clear], rendered as an overflow item), and its [content].
 */
data class MyTab(
    val tag: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val onSort: (() -> Unit)? = null,
    val clear: TabAction? = null,
    val content: @Composable () -> Unit,
)

/**
 * The Compose host for the `My*` tab activities, replacing the legacy `MyTabActivityBase`
 * (`TabLayout` + `ViewPager2` + `FragmentStateAdapter`): an [ObaTopAppBar] whose Sort/Clear actions
 * track the visible page, over a [TabRow] + swipeable [HorizontalPager] of [tabs].
 *
 * The starting page is the deep-link [initialTag], else the last-viewed tab [persistedTag], else the
 * first tab (the legacy `restoreDefaultTab` precedence). Rotation keeps the live page via
 * [rememberPagerState]. On exit the current tag is reported to [onPersistTag], but only when this
 * launch wasn't a deep-link (mirrors the old `onDestroy` + `mDefaultTab == null` guard). The pref
 * I/O is hoisted to the caller (which injects the seam) so this stays a pure composable.
 */
@Composable
fun MyTabsScreen(
    @StringRes titleRes: Int,
    tabs: List<MyTab>,
    initialTag: String?,
    persistedTag: String?,
    onPersistTag: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initialPage = remember {
        initialTag?.let { tabs.indexOfTag(it) }
            ?: persistedTag?.let { tabs.indexOfTag(it) }
            ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }

    DisposableEffect(Unit) {
        onDispose {
            if (initialTag == null) {
                onPersistTag(tabs[pagerState.currentPage].tag)
            }
        }
    }

    Scaffold(
        topBar = {
            val current = tabs[pagerState.currentPage]
            ObaTopAppBar(title = stringResource(titleRes), onBack = onBack) {
                current.onSort?.let { sort ->
                    IconButton(onClick = sort) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_content_sort),
                            contentDescription = stringResource(R.string.menu_option_sort_by)
                        )
                    }
                }
                current.clear?.let { ClearOverflow(it) }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.titleRes)) },
                        icon = { Icon(painterResource(tab.iconRes), contentDescription = null) }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                tabs[page].content()
            }
        }
    }
}

/** The overflow (⋮) holding a single per-tab Clear item. */
@Composable
private fun ClearOverflow(clear: TabAction) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(clear.labelRes)) },
                onClick = {
                    expanded = false
                    clear.onClick()
                }
            )
        }
    }
}

private fun List<MyTab>.indexOfTag(tag: String): Int? =
    indexOfFirst { it.tag == tag }.takeIf { it >= 0 }
