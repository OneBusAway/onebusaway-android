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
package org.onebusaway.android.ui.report.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.ListUiState
import org.onebusaway.android.ui.compose.components.ListScreenScaffold
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Stateful entry point: collects the type list and forwards a tapped action to the host. */
@Composable
fun ReportTypeListRoute(
    viewModel: ReportTypeListViewModel,
    onBack: () -> Unit,
    onActionSelected: (ReportAction) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReportTypeListScreen(state = state, onBack = onBack, onActionSelected = onActionSelected)
}

/** Stateless "Send feedback" type list. */
@Composable
fun ReportTypeListScreen(
    state: ListUiState<ReportType>,
    onBack: () -> Unit,
    onActionSelected: (ReportAction) -> Unit
) {
    ListScreenScaffold(
        title = stringResource(R.string.navdrawer_item_send_feedback),
        onBack = onBack,
        state = state,
        onRetry = {},
        itemKey = { it.action }
    ) { type ->
        ReportTypeRow(type = type, onClick = { onActionSelected(type.action) })
    }
}

@Composable
private fun ReportTypeRow(type: ReportType, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (type.iconRes != 0) {
                Icon(
                    painter = painterResource(type.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = type.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = type.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportTypeListScreenPreview() {
    ObaTheme {
        ReportTypeListScreen(
            state = ListUiState.Success(
                listOf(
                    ReportType("Contact Customer Service", "Driver compliments, lost & found, etc.", R.drawable.ic_customer_service, ReportAction.CUSTOMER_SERVICE),
                    ReportType("Report a Stop Problem", "Missing route, wrong stop name, etc.", R.drawable.ic_stop_flag_triangle, ReportAction.STOP_PROBLEM),
                    ReportType("Report an Arrival Time Problem", "Vehicle never came, wrong time, etc.", R.drawable.ic_arrival_time, ReportAction.ARRIVAL_PROBLEM)
                )
            ),
            onBack = {},
            onActionSelected = {}
        )
    }
}
