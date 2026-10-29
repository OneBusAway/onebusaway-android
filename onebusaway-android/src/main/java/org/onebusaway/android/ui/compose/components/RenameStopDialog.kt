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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import org.onebusaway.android.R

/**
 * Gives a stop a name of the rider's own (#2414) — the inline editor the 26.1.0 arrivals header had,
 * as a dialog both the focus banner and the stop lists can raise.
 *
 * The field opens on [currentName] (the custom name, or the official one when there is none) with it
 * all selected, so typing replaces it. [onSave] gets the raw text; the store decides what it means
 * (`normalizedUserName`: blank, or the official name, removes the custom name). "Use official name"
 * says that removal out loud, and only shows when there is a custom name to remove.
 */
@Composable
fun RenameStopDialog(
    currentName: String,
    officialName: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(currentName, selection = TextRange(0, currentName.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val commit = { name: String ->
        onSave(name)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stop_info_option_editname)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                supportingText = officialName?.takeIf { it.isNotBlank() }?.let {
                    { Text(stringResource(R.string.stop_info_official_stop_name_label, it)) }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { commit(text.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            // Here, in the dialog window's own composition, so the field is attached before it's asked.
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { commit(text.text) }) { Text(stringResource(R.string.stop_info_save)) }
        },
        dismissButton = {
            if (officialName != null && currentName != officialName) {
                TextButton(onClick = { commit("") }) { Text(stringResource(R.string.stop_info_use_official_name)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
