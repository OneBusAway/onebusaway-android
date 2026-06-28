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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
/**
 * Stable UIAutomator/Compose-test handles for the trip-plan form. Surfaced as resource-ids by the
 * app-wide `testTagsAsResourceId` in HomeActivity, so the form can be driven semantically (focus a
 * field, tap a suggestion) without coordinate taps. The per-endpoint tags are `<prefix><suffix>`,
 * e.g. `tripPlanFromField`, `tripPlanToPill`, `tripPlanFromSuggestion`.
 */
object TripPlanTestTags {
    const val FROM_PREFIX = "tripPlanFrom"
    const val TO_PREFIX = "tripPlanTo"
    const val FIELD_SUFFIX = "Field"
    const val PILL_SUFFIX = "Pill"
    const val SUGGESTION_SUFFIX = "Suggestion"
}

@Composable
fun TripPlanForm(
    state: TripPlanFormState,
    onFromQueryChange: (String) -> Unit,
    onToQueryChange: (String) -> Unit,
    onSelectFrom: (TripEndpoint.Geocoded) -> Unit,
    onSelectTo: (TripEndpoint.Geocoded) -> Unit,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit,
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
            tagPrefix = TripPlanTestTags.FROM_PREFIX,
            endpoint = state.from,
            suggestions = state.fromSuggestions,
            onQueryChange = onFromQueryChange,
            onSelect = onSelectFrom,
            onClear = onClearFrom,
            onCurrentLocation = onFromCurrentLocation,
            onContacts = onFromContacts,
            onPickOnMap = onFromPickOnMap
        )
        TextButton(onClick = onReverse, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.tripplanner_reverse))
        }
        AddressField(
            label = stringResource(R.string.trip_plan_to),
            tagPrefix = TripPlanTestTags.TO_PREFIX,
            endpoint = state.to,
            suggestions = state.toSuggestions,
            onQueryChange = onToQueryChange,
            onSelect = onSelectTo,
            onClear = onClearTo,
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

/**
 * One trip-plan endpoint. A still-being-typed [TripEndpoint.FreeText] is an editable autocomplete
 * field; any resolved kind is shown as a cancellable pill sitting *inside* the same field, where the
 * text would be — the field is then inoperative (no typing) until the pill's ✕ clears it. The
 * contacts / current-location / pick-on-map shortcuts stay live in both states, so picking another
 * input method overrides the current pill.
 */
@Composable
private fun AddressField(
    label: String,
    tagPrefix: String,
    endpoint: TripEndpoint,
    suggestions: List<TripEndpoint.Geocoded>,
    onQueryChange: (String) -> Unit,
    onSelect: (TripEndpoint.Geocoded) -> Unit,
    onClear: () -> Unit,
    onCurrentLocation: () -> Unit,
    onContacts: () -> Unit,
    onPickOnMap: () -> Unit
) {
    when (endpoint) {
        is TripEndpoint.FreeText -> EditableAddressField(
            label = label,
            tagPrefix = tagPrefix,
            query = endpoint.query,
            suggestions = suggestions,
            onQueryChange = onQueryChange,
            onSelect = onSelect,
            onCurrentLocation = onCurrentLocation,
            onContacts = onContacts,
            onPickOnMap = onPickOnMap
        )
        else -> EndpointPillField(
            label = label,
            tagPrefix = tagPrefix,
            endpoint = endpoint,
            onClear = onClear,
            onCurrentLocation = onCurrentLocation,
            onContacts = onContacts,
            onPickOnMap = onPickOnMap
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableAddressField(
    label: String,
    tagPrefix: String,
    query: String,
    suggestions: List<TripEndpoint.Geocoded>,
    onQueryChange: (String) -> Unit,
    onSelect: (TripEndpoint.Geocoded) -> Unit,
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
                AddressActionIcons(
                    onContacts = onContacts,
                    onCurrentLocation = onCurrentLocation,
                    onPickOnMap = onPickOnMap
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tagPrefix + TripPlanTestTags.FIELD_SUFFIX)
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            suggestions.forEach { place ->
                DropdownMenuItem(
                    text = { Text(place.displayName) },
                    leadingIcon = if (place.isTransit) {
                        { BusIcon() }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(place)
                        expanded = false
                    },
                    modifier = Modifier.testTag(tagPrefix + TripPlanTestTags.SUGGESTION_SUFFIX)
                )
            }
        }
    }
}

/**
 * A resolved endpoint shown as a pill *inside* an outlined field. There is no editable text — the
 * pill occupies the field's inner content slot, so the field can't be typed into until ✕ clears it.
 * The action icons remain in the trailing slot so another input method can replace the pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointPillField(
    label: String,
    tagPrefix: String,
    endpoint: TripEndpoint,
    onClear: () -> Unit,
    onCurrentLocation: () -> Unit,
    onContacts: () -> Unit,
    onPickOnMap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val endpointText = endpointLabel(endpoint)
    // Read-only host text field gives the full-width sizing; we ignore its inner text field and drop
    // the pill into the OutlinedTextField decoration instead, so it renders where the text would be.
    BasicTextField(
        value = "",
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagPrefix + TripPlanTestTags.PILL_SUFFIX)
    ) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = endpointText, // non-empty so the label floats above the pill
            innerTextField = {
                InputChip(
                    selected = true,
                    onClick = onClear,
                    label = {
                        // Geocoder/contact names can be long; keep the pill to one line inside the
                        // singleLine field so it doesn't wrap over the trailing clear icon.
                        Text(endpointText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingIcon = if (endpoint.isTransit) {
                        { BusIcon() }
                    } else {
                        null
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.trip_plan_clear_endpoint)
                        )
                    }
                )
            },
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            label = { Text(label) },
            trailingIcon = {
                AddressActionIcons(
                    onContacts = onContacts,
                    onCurrentLocation = onCurrentLocation,
                    onPickOnMap = onPickOnMap
                )
            },
            contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 8.dp, bottom = 8.dp)
        )
    }
}

/** The contacts / current-location / pick-on-map shortcuts shared by both endpoint states. */
@Composable
private fun AddressActionIcons(
    onContacts: () -> Unit,
    onCurrentLocation: () -> Unit,
    onPickOnMap: () -> Unit
) {
    Row {
        IconButton(onClick = onContacts) {
            Icon(
                painter = painterResource(R.drawable.baseline_import_contacts_24),
                contentDescription = stringResource(R.string.trip_plan_contacts)
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
}

/** The user-visible label for a resolved endpoint; fixed kinds resolve a string resource. */
@Composable
private fun endpointLabel(endpoint: TripEndpoint): String = endpoint.displayText ?: when (endpoint) {
    is TripEndpoint.MapPoint -> stringResource(R.string.trip_plan_map_location)
    // Only the fixed-label kinds (CurrentLocation/MapPoint) have a null displayText.
    else -> stringResource(R.string.tripplanner_current_location)
}

@Composable
private fun BusIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_bus),
        contentDescription = null,
        tint = colorResource(R.color.material_gray)
    )
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
                from = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3),
                to = TripEndpoint.FreeText(""),
                dateTimeMillis = 0L,
                dateLabel = "June 10",
                timeLabel = "3:45 PM"
            ),
            onFromQueryChange = {}, onToQueryChange = {},
            onSelectFrom = {}, onSelectTo = {},
            onClearFrom = {}, onClearTo = {},
            onFromCurrentLocation = {}, onToCurrentLocation = {},
            onFromContacts = {}, onToContacts = {},
            onFromPickOnMap = {}, onToPickOnMap = {},
            onSetArriving = {}, onPickDate = {}, onPickTime = {},
            onReverse = {}, onAdvancedSettings = {}
        )
    }
}
