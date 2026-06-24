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
package org.onebusaway.android.ui.report.problem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.report.ReportFormSurface

/** Stateful entry point: collects the form state and forwards edits to the ViewModel. */
@Composable
fun ProblemReportRoute(viewModel: ProblemReportViewModel) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    ProblemReportForm(
        state = state,
        onCodeSelected = viewModel::onCodeSelected,
        onCommentChange = viewModel::onCommentChange,
        onVehicleToggle = viewModel::onVehicleToggle,
        onVehicleNumberChange = viewModel::onVehicleNumberChange
    )
}

/** Stateless problem form, shared by the stop and trip flows (trip adds the vehicle fields). */
@Composable
fun ProblemReportForm(
    state: ProblemFormState,
    onCodeSelected: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onVehicleToggle: (Boolean) -> Unit,
    onVehicleNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ReportFormSurface(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.kind == ProblemKind.TRIP && !state.headsign.isNullOrEmpty()) {
                Text(text = state.headsign, style = MaterialTheme.typography.titleMedium)
            }

            ProblemCodeDropdown(
                codes = state.codes,
                selectedIndex = state.selectedCodeIndex,
                onCodeSelected = onCodeSelected
            )

            OutlinedTextField(
                value = state.comment,
                onValueChange = onCommentChange,
                label = { Text(stringResource(R.string.report_problem_comment_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.kind == ProblemKind.TRIP) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onVehicleToggle(!state.onVehicle) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = state.onVehicle, onCheckedChange = onVehicleToggle)
                    Text(stringResource(R.string.report_problem_onvehicle_bus))
                }

                OutlinedTextField(
                    value = state.vehicleNumber,
                    onValueChange = onVehicleNumberChange,
                    enabled = state.onVehicle,
                    label = { Text(stringResource(R.string.report_problem_uservehicle_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = stringResource(
                    R.string.report_problem_hint, stringResource(R.string.app_name)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProblemCodeDropdown(
    codes: List<ProblemCode>,
    selectedIndex: Int,
    onCodeSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = codes[selectedIndex].label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            codes.forEachIndexed { index, code ->
                DropdownMenuItem(
                    text = { Text(code.label) },
                    onClick = {
                        expanded = false
                        onCodeSelected(index)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProblemReportFormStopPreview() {
    ObaTheme {
        ProblemReportForm(
            state = ProblemFormState(
                kind = ProblemKind.STOP,
                codes = ProblemCodes.stop(
                    listOf("Choose a problem", "Stop name is wrong", "Something else")
                ),
                selectedCodeIndex = 1,
                comment = "The sign says a different name"
            ),
            onCodeSelected = {}, onCommentChange = {}, onVehicleToggle = {}, onVehicleNumberChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProblemReportFormTripPreview() {
    ObaTheme {
        ProblemReportForm(
            state = ProblemFormState(
                kind = ProblemKind.TRIP,
                codes = ProblemCodes.trip(
                    listOf("Choose a problem", "The bus never came", "Something else")
                ),
                headsign = "Route 40 to Downtown",
                onVehicle = true,
                vehicleNumber = "1234"
            ),
            onCodeSelected = {}, onCommentChange = {}, onVehicleToggle = {}, onVehicleNumberChange = {}
        )
    }
}
