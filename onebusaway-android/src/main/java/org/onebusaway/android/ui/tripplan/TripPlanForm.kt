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
package org.onebusaway.android.ui.tripplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The trip-plan form: two autocomplete address fields (each with current-location + contacts
 * shortcuts), a leaving/arriving selector, and date/time pickers, plus reverse + advanced-settings
 * actions. Stateless and driven by [TripPlanFormState]; the date/time/contacts/current-location
 * actions are platform interactions launched by the host.
 */
@Composable
fun TripPlanForm(
    state: TripPlanFormState,
    onFromQueryChange: (String) -> Unit,
    onToQueryChange: (String) -> Unit,
    onSelectFrom: (PlaceItem) -> Unit,
    onSelectTo: (PlaceItem) -> Unit,
    onFromCurrentLocation: () -> Unit,
    onToCurrentLocation: () -> Unit,
    onFromContacts: () -> Unit,
    onToContacts: () -> Unit,
    onFromPickOnMap: () -> Unit,
    onToPickOnMap: () -> Unit,
    onSetArriving: (Boolean) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onReverse: () -> Unit,
    onAdvancedSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AddressField(
            label = stringResource(R.string.trip_plan_from),
            query = state.fromQuery,
            suggestions = state.fromSuggestions,
            onQueryChange = onFromQueryChange,
            onSelect = onSelectFrom,
            onCurrentLocation = onFromCurrentLocation,
            onContacts = onFromContacts,
            onPickOnMap = onFromPickOnMap
        )
        TextButton(onClick = onReverse, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.tripplanner_reverse))
        }
        AddressField(
            label = stringResource(R.string.trip_plan_to),
            query = state.toQuery,
            suggestions = state.toSuggestions,
            onQueryChange = onToQueryChange,
            onSelect = onSelectTo,
            onCurrentLocation = onToCurrentLocation,
            onContacts = onToContacts,
            onPickOnMap = onToPickOnMap
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeavingArrivingDropdown(
                arriving = state.arriving,
                onSetArriving = onSetArriving,
                modifier = Modifier.weight(1.2f)
            )
            OutlinedButton(onClick = onPickDate, modifier = Modifier.weight(1f)) {
                Text(state.dateLabel)
            }
            OutlinedButton(onClick = onPickTime, modifier = Modifier.weight(1f)) {
                Text(state.timeLabel)
            }
        }
        TextButton(onClick = onAdvancedSettings) {
            Text(stringResource(R.string.trip_plan_advanced_settings))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressField(
    label: String,
    query: String,
    suggestions: List<PlaceItem>,
    onQueryChange: (String) -> Unit,
    onSelect: (PlaceItem) -> Unit,
    onCurrentLocation: () -> Unit,
    onContacts: () -> Unit,
    onPickOnMap: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val showMenu = expanded && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                Row {
                    IconButton(onClick = onContacts) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_import_contacts_24),
                            contentDescription = stringResource(R.string.trip_plan_from)
                        )
                    }
                    IconButton(onClick = onCurrentLocation) {
                        Icon(
                            painter = painterResource(R.drawable.ic_my_location),
                            contentDescription = stringResource(R.string.tripplanner_current_location)
                        )
                    }
                    IconButton(onClick = onPickOnMap) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_location_map),
                            contentDescription = stringResource(R.string.trip_plan_pick_on_map)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            suggestions.forEach { place ->
                DropdownMenuItem(
                    text = { Text(place.displayName) },
                    leadingIcon = if (place.isTransit) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_bus),
                                contentDescription = null,
                                tint = colorResource(R.color.material_gray)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(place)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Leaving/arriving selector — the Compose equivalent of the legacy Spinner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeavingArrivingDropdown(
    arriving: Boolean,
    onSetArriving: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val leaving = stringResource(R.string.trip_plan_leaving)
    val arrivingLabel = stringResource(R.string.trip_plan_arriving)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (arriving) arrivingLabel else leaving,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(leaving) }, onClick = { expanded = false; onSetArriving(false) })
            DropdownMenuItem(text = { Text(arrivingLabel) }, onClick = { expanded = false; onSetArriving(true) })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TripPlanFormPreview() {
    ObaTheme {
        TripPlanForm(
            state = TripPlanFormState(
                fromQuery = "Current Location",
                toQuery = "",
                dateTimeMillis = 0L,
                dateLabel = "June 10",
                timeLabel = "3:45 PM"
            ),
            onFromQueryChange = {}, onToQueryChange = {},
            onSelectFrom = {}, onSelectTo = {},
            onFromCurrentLocation = {}, onToCurrentLocation = {},
            onFromContacts = {}, onToContacts = {},
            onFromPickOnMap = {}, onToPickOnMap = {},
            onSetArriving = {}, onPickDate = {}, onPickTime = {},
            onReverse = {}, onAdvancedSettings = {}
        )
    }
}
