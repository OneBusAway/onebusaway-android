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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

/** Shared navigation keeps both migration choices in one numbered sequence. */
@Composable
internal fun MigrationDialog(
    title: String,
    page: Int,
    pageCount: Int,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val pageDescription = stringResource(R.string.migration_page, page, pageCount)
    AlertDialog(
        onDismissRequest = onBack ?: onDismiss,
        title = { Text(title) },
        text = content,
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clearAndSetSemantics { contentDescription = pageDescription },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    repeat(pageCount) { index ->
                        val selected = index + 1 == page
                        val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        Box(
                            Modifier.size(8.dp)
                                .border(1.dp, color, CircleShape)
                                .background(if (selected) color else Color.Transparent, CircleShape)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onBack?.invoke() }, enabled = onBack != null) {
                        Text(stringResource(R.string.migration_back))
                    }
                    TextButton(onClick = onContinue) { Text(stringResource(R.string.migration_continue)) }
                }
            }
        }
    )
}
