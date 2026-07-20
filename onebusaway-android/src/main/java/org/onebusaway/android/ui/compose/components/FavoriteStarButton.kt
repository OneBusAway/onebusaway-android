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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

/**
 * A star toggle button for favoriting/unfavoriting a route: a filled star when [isFavorite], an outline
 * otherwise, with the matching add/remove-star content description. [onClick] fires on tap. [iconSize]
 * sizes the star (the Material default 24dp when unset).
 *
 * The shared form of the filled-vs-outline star that several screens (arrivals rows/panel, search
 * results) currently spell out inline; new call sites should use this rather than repeat the ternary.
 */
@Composable
fun FavoriteStarButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(
                if (isFavorite) R.drawable.star else R.drawable.star_outline
            ),
            contentDescription = stringResource(
                if (isFavorite) {
                    R.string.bus_options_menu_remove_star
                } else {
                    R.string.bus_options_menu_add_star
                }
            ),
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
