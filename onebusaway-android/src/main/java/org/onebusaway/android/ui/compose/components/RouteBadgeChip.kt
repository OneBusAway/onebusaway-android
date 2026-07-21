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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A small route roundel — the route's short name on a chip tinted from its GTFS color (via
 * [rememberRouteBadgeColors]), or a neutral chip when the route has no color. The compact form used
 * where several routes sit in a row (the stop-focus header's subordinate routes, the trip-plan option
 * cards), as opposed to the large square [LineBadge].
 */
@Composable
fun RouteBadgeChip(shortName: String, routeColor: Int?, modifier: Modifier = Modifier) {
    val (container, content) = rememberRouteBadgeColors(routeColor)
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(1.dp)
    ) {
        Text(
            text = shortName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}
