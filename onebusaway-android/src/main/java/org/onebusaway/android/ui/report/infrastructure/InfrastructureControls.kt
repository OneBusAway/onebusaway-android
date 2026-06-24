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
package org.onebusaway.android.ui.report.infrastructure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

/**
 * The map-overlay controls for the infrastructure-issue screen: bus-stop header, address search
 * field, the sectioned service spinner, and the "tap a stop" prompt. Rendered in the report flow's
 * vertically-scrolling Column, so it does not scroll itself and is wrapped in a [Surface] for a visible
 * content color on the dark report background.
 */
@Composable
fun InfrastructureControls(
    state: InfrastructureIssueUiState,
    onAddressSearch: (String) -> Unit,
    onServiceSelected: (Int) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.busStopName?.let { name ->
                Text(text = name, style = MaterialTheme.typography.titleMedium)
            }

            AddressField(address = state.address, onSearch = onAddressSearch)

            if (state.servicesVisible) {
                ServiceSpinner(
                    services = state.services,
                    selectedIndex = state.selectedIndex,
                    onServiceSelected = onServiceSelected
                )
            }

            if (state.showStopPrompt) {
                Text(
                    text = stringResource(R.string.report_dialog_stop_header),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddressField(address: String, onSearch: (String) -> Unit) {
    // Reset the editable draft whenever a fresh reverse-geocoded address arrives.
    var query by remember(address) { mutableStateOf(address) }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.rt_address_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceSpinner(
    services: List<ServiceListItem>,
    selectedIndex: Int,
    onServiceSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (val item = services.getOrNull(selectedIndex)) {
        is ServiceListItem.Hint -> item.label
        is ServiceListItem.Category -> item.name
        else -> ""
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.ri_service_default)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            services.forEachIndexed { index, item ->
                when (item) {
                    is ServiceListItem.Section -> Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    is ServiceListItem.Hint -> DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = { expanded = false; onServiceSelected(index) }
                    )

                    is ServiceListItem.Category -> DropdownMenuItem(
                        text = { Text(item.name) },
                        onClick = { expanded = false; onServiceSelected(index) }
                    )
                }
            }
        }
    }
}
