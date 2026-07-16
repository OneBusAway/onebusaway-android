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
package org.onebusaway.android.ui.home.chrome

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.RouteRowContent
import org.onebusaway.android.ui.compose.components.StopRowContent
import org.onebusaway.android.ui.mylists.RecentItem

// ~4 rows (each ~48-56dp) stay visible; the rest scroll. A fixed cap, not content-driven, so the
// dropdown never grows to crowd the keyboard.
private val DROPDOWN_MAX_HEIGHT = 224.dp

/**
 * The recents dropdown that hangs inside the expanded search field: a "Recents" section header (so it's
 * clear these are recent shortcuts and submitting the field runs a full, separate search) over a
 * scrollable, height-capped list of the unified recent stops+routes. Each row carries a leading type icon
 * (stop_flag / ic_route — the glyphs the Recent tabs use) so the mixed list scans at a glance, then reuses
 * [StopRowContent] / [RouteRowContent] for the body. Tapping fires [onRecentStop] or
 * [onRecentRoute] — both reveal the stop / route on the map.
 *
 * Renders no surface of its own — the caller ([MapTopChrome]'s search field) provides the connected
 * container so the field + dropdown read as one expanded surface.
 */
@Composable
fun SearchRecentsDropdown(
    recents: List<RecentItem>,
    onRecentStop: (id: String, lat: Double, lon: Double) -> Unit,
    onRecentRoute: (routeId: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // A divider + muted section header separate the recents from the query field above and label
        // them, so the user reads them as recents (a shortcut), distinct from submitting a full search.
        HorizontalDivider()
        Text(
            text = stringResource(R.string.search_recents_header),
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        LazyColumn(Modifier.heightIn(max = DROPDOWN_MAX_HEIGHT)) {
            itemsIndexed(recents, key = { _, item -> item.key }) { index, item ->
                if (index > 0) HorizontalDivider()
                when (item) {
                    is RecentItem.Stop -> RecentRow(
                        icon = R.drawable.stop_flag,
                        onClick = { onRecentStop(item.stop.id, item.stop.lat, item.stop.lon) },
                    ) {
                        StopRowContent(
                            name = item.stop.name,
                            direction = item.stop.rawDirection.orEmpty(),
                            isFavorite = item.stop.isFavorite,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    is RecentItem.Route -> RecentRow(
                        icon = R.drawable.ic_route,
                        onClick = { onRecentRoute(item.route.id) },
                    ) {
                        RouteRowContent(
                            shortName = item.route.shortName,
                            longName = item.route.longName,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** One dropdown row: a leading type [icon] (muted tint) then the caller's row [content], clickable. */
@Composable
private fun RecentRow(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorResource(R.color.navdrawer_icon_tint),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        content()
    }
}
