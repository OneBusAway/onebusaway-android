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
package org.onebusaway.android.ui.report.open311

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.ErrorContent
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.report.ReportFormSurface
import org.onebusaway.android.util.BitmapUtils

/** Stateful entry point: binds the ViewModel's load/form state to the screen and image actions. */
@Composable
fun Open311Route(
    viewModel: Open311ProblemViewModel,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    Open311Screen(
        loadState = loadState,
        form = form,
        onRetry = viewModel::load,
        onMainDescriptionChange = viewModel::setMainDescription,
        onFieldTextChange = viewModel::setFieldText,
        onSingleChoice = viewModel::setSingleChoice,
        onMultiChoice = viewModel::setMultiChoice,
        onAnonymousChange = viewModel::setAnonymous,
        onFirstNameChange = viewModel::setFirstName,
        onLastNameChange = viewModel::setLastName,
        onEmailChange = viewModel::setEmail,
        onPhoneChange = viewModel::setPhone,
        onTakePhoto = onTakePhoto,
        onPickFromGallery = onPickFromGallery
    )
}

/** Stateless Open311 form, fully driven by [loadState] + [form]. */
@Composable
fun Open311Screen(
    loadState: Open311LoadState,
    form: Open311FormState,
    onRetry: () -> Unit,
    onMainDescriptionChange: (String) -> Unit,
    onFieldTextChange: (Int, String) -> Unit,
    onSingleChoice: (Int, String?) -> Unit,
    onMultiChoice: (Int, String, Boolean) -> Unit,
    onAnonymousChange: (Boolean) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    ReportFormSurface {
        when (loadState) {
            Open311LoadState.LOADING -> LoadingContent(Modifier.padding(32.dp))
            Open311LoadState.ERROR -> ErrorContent(onRetry = onRetry)
            Open311LoadState.LOADED -> Open311FormContent(
                form = form,
                onMainDescriptionChange = onMainDescriptionChange,
                onFieldTextChange = onFieldTextChange,
                onSingleChoice = onSingleChoice,
                onMultiChoice = onMultiChoice,
                onAnonymousChange = onAnonymousChange,
                onFirstNameChange = onFirstNameChange,
                onLastNameChange = onLastNameChange,
                onEmailChange = onEmailChange,
                onPhoneChange = onPhoneChange,
                onTakePhoto = onTakePhoto,
                onPickFromGallery = onPickFromGallery
            )
        }
    }
}

@Composable
private fun Open311FormContent(
    form: Open311FormState,
    onMainDescriptionChange: (String) -> Unit,
    onFieldTextChange: (Int, String) -> Unit,
    onSingleChoice: (Int, String?) -> Unit,
    onMultiChoice: (Int, String, Boolean) -> Unit,
    onAnonymousChange: (Boolean) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        form.headsign?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

        form.descriptionLines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedTextField(
            value = form.mainDescription,
            onValueChange = onMainDescriptionChange,
            label = { Text(stringResource(R.string.ri_desc_hint)) },
            modifier = Modifier.fillMaxWidth()
        )

        form.fields.forEach { field ->
            Open311FieldInput(field, form, onFieldTextChange, onSingleChoice, onMultiChoice)
        }

        ImageAttachment(form.imagePath, onTakePhoto, onPickFromGallery)

        HorizontalDivider()

        AnonymousAndContact(
            form = form,
            onAnonymousChange = onAnonymousChange,
            onFirstNameChange = onFirstNameChange,
            onLastNameChange = onLastNameChange,
            onEmailChange = onEmailChange,
            onPhoneChange = onPhoneChange
        )
    }
}

@Composable
private fun Open311FieldInput(
    field: Open311Field,
    form: Open311FormState,
    onFieldTextChange: (Int, String) -> Unit,
    onSingleChoice: (Int, String?) -> Unit,
    onMultiChoice: (Int, String, Boolean) -> Unit
) {
    val label = if (field.required) {
        stringResource(R.string.ri_field_required_label, field.label)
    } else {
        field.label
    }
    when (field.type) {
        Open311FieldType.TEXT, Open311FieldType.NUMBER, Open311FieldType.DATETIME -> OutlinedTextField(
            value = form.textValue(field.code),
            onValueChange = { onFieldTextChange(field.code, it) },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = field.type.keyboardType()),
            modifier = Modifier.fillMaxWidth()
        )

        Open311FieldType.SINGLE_CHOICE -> Column(Modifier.selectableGroup()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            val selected = form.singleChoice(field.code)
            field.options.forEach { option ->
                val isSelected = option.key == selected
                ChoiceOptionRow(
                    name = option.name,
                    modifier = Modifier.selectable(
                        selected = isSelected,
                        onClick = { onSingleChoice(field.code, option.key) }
                    ),
                    control = { RadioButton(selected = isSelected, onClick = null) }
                )
            }
        }

        Open311FieldType.MULTI_CHOICE -> Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            val selected = form.multiChoice(field.code)
            field.options.forEach { option ->
                val checked = option.key in selected
                ChoiceOptionRow(
                    name = option.name,
                    modifier = Modifier.toggleable(
                        value = checked,
                        onValueChange = { onMultiChoice(field.code, option.key, it) }
                    ),
                    control = { Checkbox(checked = checked, onCheckedChange = null) }
                )
            }
        }
    }
}

/** A single radio/checkbox option row: the control plus its label, sharing one click target. */
@Composable
private fun ChoiceOptionRow(
    name: String,
    modifier: Modifier,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        control()
        Text(name)
    }
}

@Composable
private fun ImageAttachment(
    imagePath: String?,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val thumbnail = rememberThumbnail(imagePath)
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTakePhoto) {
                Text(stringResource(R.string.ri_button_camera))
            }
            OutlinedButton(onClick = onPickFromGallery) {
                Text(stringResource(R.string.ri_button_gallery))
            }
        }
    }
}

@Composable
private fun AnonymousAndContact(
    form: Open311FormState,
    onAnonymousChange: (Boolean) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = form.anonymous, onValueChange = onAnonymousChange),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = form.anonymous, onCheckedChange = null)
            Text(stringResource(R.string.ri_anonymous_checkbox))
        }

        val enabled = !form.anonymous
        ContactField(form.contact.firstName, onFirstNameChange, R.string.ri_user_name_hint, enabled)
        ContactField(form.contact.lastName, onLastNameChange, R.string.ri_user_lastname_hint, enabled)
        ContactField(
            form.contact.email, onEmailChange, R.string.ri_user_email_hint, enabled, KeyboardType.Email
        )
        ContactField(
            form.contact.phone, onPhoneChange, R.string.ri_user_phone_hint, enabled, KeyboardType.Phone
        )
    }
}

@Composable
private fun ContactField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun Open311FieldType.keyboardType(): KeyboardType = when (this) {
    Open311FieldType.NUMBER -> KeyboardType.Number
    else -> KeyboardType.Text
}

/** Decodes a small thumbnail for [path] off the main thread; null while loading or on failure. */
@Composable
private fun rememberThumbnail(path: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapUtils.decodeSampledBitmapFromFile(path, 192, 192)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return state.value
}
