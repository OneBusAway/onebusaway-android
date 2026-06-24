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
package org.onebusaway.android.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The reusable Material3 building blocks for the Compose settings screens — the hand-rolled
 * replacement for the legacy `androidx.preference` XML widgets (there is no official Compose
 * preference library). Every item is stateless: the current value is hoisted to the caller (a settings
 * ViewModel), and a change is reported back through a callback. Visibility/enablement decisions live
 * in the caller too — an item that shouldn't show is simply not emitted.
 */

/** Disabled rows dim their text to the Material "disabled content" opacity. */
private const val DISABLED_ALPHA = 0.38f

/**
 * A titled group of preference rows — the replacement for `PreferenceCategory`. The caller emits the
 * member rows in [content]; a category that shouldn't appear is just not called.
 */
@Composable
fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        content()
    }
}

/**
 * The shared visual scaffold for a single preference row: a [title] with an optional [summary] in a
 * weighted column, an optional [trailing] control, and whole-row click (when [onClick] is non-null
 * and [enabled]). Matches the app's list-row metrics (16dp horizontal / 12dp vertical padding,
 * bodyLarge title, bodyMedium onSurfaceVariant summary).
 */
@Composable
private fun PreferenceRow(
    title: String,
    summary: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickable = onClick != null && enabled
    val rowModifier = modifier
        .fillMaxWidth()
        .then(if (clickable) Modifier.clickable(onClick = onClick!!) else Modifier)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** A toggle row (replaces `CheckBoxPreference`/`SwitchPreference`); the whole row flips the switch. */
@Composable
fun SwitchPreferenceItem(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PreferenceRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled,
            )
        },
    )
}

/** A plain clickable row with no value (replaces a click-action `Preference`). */
@Composable
fun ClickPreferenceItem(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PreferenceRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * A single-choice row (replaces `ListPreference`). Its summary is the entry for the current
 * [selectedValue]; tapping opens a Material3 single-choice dialog that applies a pick immediately
 * (matching the legacy dialog). [entries] are the human labels and [entryValues] the stored values,
 * positionally paired (as in the XML entries/entryValues arrays).
 */
@Composable
fun ListPreferenceItem(
    title: String,
    entries: List<String>,
    entryValues: List<String>,
    selectedValue: String?,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary = entries.getOrNull(entryValues.indexOf(selectedValue))
    ClickPreferenceItem(
        title = title,
        summary = summary,
        onClick = { showDialog = true },
        modifier = modifier,
        enabled = enabled,
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup()) {
                    entries.forEachIndexed { index, entry ->
                        val value = entryValues.getOrNull(index) ?: return@forEachIndexed
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == selectedValue,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showDialog = false
                                        if (value != selectedValue) onValueSelected(value)
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selectedValue, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(entry, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * A free-text row (replaces `EditTextPreference`). Tapping opens a dialog seeded with [currentValue];
 * confirming calls [onValueChange] with the trimmed text and keeps the dialog open if it returns
 * `false` (preserving the legacy `onPreferenceChange` reject-on-invalid contract, e.g. URL validation).
 */
@Composable
fun EditTextPreferenceItem(
    title: String,
    summary: String?,
    currentValue: String?,
    hint: String,
    onValueChange: (String) -> Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Uri,
) {
    var showDialog by remember { mutableStateOf(false) }
    ClickPreferenceItem(
        title = title,
        summary = summary,
        onClick = { showDialog = true },
        modifier = modifier,
        enabled = enabled,
    )
    if (showDialog) {
        var text by remember { mutableStateOf(currentValue.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(hint) },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onValueChange(text.trim())) showDialog = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceItemsPreview() {
    ObaTheme {
        Column {
            PreferenceCategory("Display") {
                var sw by remember { mutableStateOf(true) }
                SwitchPreferenceItem(
                    title = "Show negative arrivals",
                    summary = "Show buses that have already departed",
                    checked = sw,
                    onCheckedChange = { sw = it },
                )
                HorizontalDivider()
                ListPreferenceItem(
                    title = "Map mode",
                    entries = listOf("Normal", "Satellite"),
                    entryValues = listOf("normal", "satellite"),
                    selectedValue = "normal",
                    onValueSelected = {},
                )
                HorizontalDivider()
                ClickPreferenceItem(
                    title = "About",
                    summary = "Version, licenses, contributors",
                    onClick = {},
                )
                HorizontalDivider()
                EditTextPreferenceItem(
                    title = "Custom API URL",
                    summary = "Not set",
                    currentValue = null,
                    hint = "https://api.example.org",
                    onValueChange = { true },
                )
            }
        }
    }
}
