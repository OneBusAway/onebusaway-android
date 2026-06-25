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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * A reusable informational dialog: a tinted [icon], a [title], a [body] (with bare URLs linkified),
 * an optional "never show again"-style opt-out checkbox, and confirm / optional dismiss buttons. The
 * Compose replacement for the app's several `MaterialAlertDialogBuilder.setView(...)` text+checkbox
 * dialogs (change-location-mode, destination-reminder beta, fare-payment warning).
 *
 * [onOptOut] is invoked on every checkbox toggle (matching the legacy persist-on-change behavior);
 * pass null to omit the checkbox. The dialog is non-cancelable by default (matches the originals).
 */
@Composable
fun OptOutInfoDialog(
    title: String,
    icon: Painter,
    iconTint: Color,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    optOutLabel: String? = null,
    onOptOut: ((Boolean) -> Unit)? = null,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(painter = icon, contentDescription = null, tint = iconTint) },
        title = { Text(title) },
        text = {
            Column {
                Text(remember(body, linkColor) { linkifyUrls(body, linkColor) })
                if (optOutLabel != null && onOptOut != null) {
                    var optedOut by remember { mutableStateOf(false) }
                    SwitchRow(
                        label = optOutLabel,
                        checked = optedOut,
                        onCheckedChange = { optedOut = it; onOptOut(it) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = dismissText?.let { label ->
            { TextButton(onClick = onDismiss) { Text(label) } }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}
