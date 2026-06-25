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
package org.onebusaway.android.ui.tripinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Stateful entry point: collects the ViewModel state and wires the form callbacks. */
@Composable
fun TripInfoRoute(
    viewModel: TripInfoViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onShowRoute: () -> Unit,
    onShowStop: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TripInfoScreen(
        state = state,
        onBack = onBack,
        onSave = onSave,
        onDelete = onDelete,
        onShowRoute = onShowRoute,
        onShowStop = onShowStop,
        onTripNameChange = viewModel::setTripName,
        onReminderSelected = viewModel::setReminderSelection
    )
}

/** Stateless screen content, fully driven by [TripInfoUiState] — previewable and testable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripInfoScreen(
    state: TripInfoUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onShowRoute: () -> Unit,
    onShowStop: () -> Unit,
    onTripNameChange: (String) -> Unit,
    onReminderSelected: (Int) -> Unit
) {
    val content = state as? TripInfoUiState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = onSave) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_content_save),
                                contentDescription = stringResource(R.string.trip_info_save),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!content.isNewTrip) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_action_content_discard),
                                    contentDescription = stringResource(R.string.trip_info_delete),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        OverflowMenu(onShowRoute, onShowStop)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (content) {
                null -> LoadingContent(Modifier.align(Alignment.Center))
                else -> TripInfoForm(content, onTripNameChange, onReminderSelected)
            }
            if (content?.isSaving == true) {
                SavingOverlay()
            }
        }
    }
}

/** The "Show Route" / "Show Stop" items, behind the toolbar's overflow (⋮) button. */
@Composable
private fun OverflowMenu(onShowRoute: () -> Unit, onShowStop: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trip_info_option_showroute)) },
                onClick = {
                    expanded = false
                    onShowRoute()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trip_info_option_showstop)) },
                onClick = {
                    expanded = false
                    onShowStop()
                }
            )
        }
    }
}

/** The trip header (stop, route - headsign, departure) over the reminder time + name form. */
@Composable
private fun TripInfoForm(
    content: TripInfoUiState.Content,
    onTripNameChange: (String) -> Unit,
    onReminderSelected: (Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // The stop name can be empty when the stop was never cached locally — skip the blank line.
            if (content.stopName.isNotEmpty()) {
                Text(
                    text = content.stopName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Text(
                text = listOf(content.routeName, content.headsign)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ${stringResource(R.string.trip_info_separator)} "),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = content.departureText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.trip_info_reminder_header),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            ReminderTimeDropdown(
                options = content.reminderOptions,
                selection = content.reminderSelection,
                onSelected = onReminderSelected
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = content.tripName,
                onValueChange = onTripNameChange,
                label = { Text(stringResource(R.string.trip_info_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The reminder lead-time selector — the Compose equivalent of the legacy Spinner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDropdown(
    options: List<String>,
    selection: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.getOrElse(selection) { "" },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    }
                )
            }
        }
    }
}

/** A dimmed, input-blocking overlay with a spinner, shown while the alarm is being registered. */
@Composable
private fun SavingOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            // Swallow taps so the form can't be edited mid-save.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------------------------------
// Preview

@Preview(showBackground = true)
@Composable
private fun TripInfoPreview() {
    ObaTheme {
        TripInfoScreen(
            state = TripInfoUiState.Content(
                stopName = "Othello Station - Bay 2",
                routeName = "Route 36",
                headsign = "Downtown Seattle",
                departureText = "Departs at 3:32 PM",
                reminderOptions = listOf("1 minute", "3 minutes", "5 minutes", "10 minutes"),
                reminderSelection = 3,
                tripName = "To work",
                isNewTrip = false
            ),
            onBack = {}, onSave = {}, onDelete = {}, onShowRoute = {}, onShowStop = {},
            onTripNameChange = {}, onReminderSelected = {}
        )
    }
}
