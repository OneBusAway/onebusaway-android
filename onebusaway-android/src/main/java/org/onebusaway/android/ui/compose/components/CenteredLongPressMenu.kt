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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A secondary-action menu opened by long press. A dialog supplies one consistent screen-centered
 * position regardless of which row or horizontally scrolled ETA pill launched it; the content stays
 * ordinary Material [androidx.compose.material3.DropdownMenuItem] rows.
 */
@Composable
internal fun CenteredLongPressMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 196.dp, max = 320.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

/** A labelled menu item with an optional decorative leading [icon]. */
@Composable
internal fun MenuRow(textRes: Int, icon: ImageVector? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        onClick = onClick,
        leadingIcon = icon?.let { { Icon(imageVector = it, contentDescription = null) } }
    )
}
