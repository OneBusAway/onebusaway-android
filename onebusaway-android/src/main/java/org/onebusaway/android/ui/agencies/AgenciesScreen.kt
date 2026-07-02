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
package org.onebusaway.android.ui.agencies

import org.onebusaway.android.models.AgencyContact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.ListUiState
import org.onebusaway.android.ui.compose.components.ListScreenScaffold
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.ExternalIntents

/**
 * Stateful entry point for the supported agencies screen: collects the ViewModel's state and
 * wires UI events back to it.
 */
@Composable
fun AgenciesRoute(viewModel: AgenciesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AgenciesScreen(
        state = state,
        onRetry = viewModel::load,
        onAgencyClick = { agency ->
            agency.url?.let { ExternalIntents.goToUrl(context, it) }
        },
        onBack = onBack
    )
}

/** Stateless screen content, fully driven by [ListUiState] — previewable and testable. */
@Composable
fun AgenciesScreen(
    state: ListUiState<AgencyContact>,
    onRetry: () -> Unit,
    onAgencyClick: (AgencyContact) -> Unit,
    onBack: () -> Unit
) {
    ListScreenScaffold(
        title = stringResource(R.string.agencies_title),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        itemKey = { it.id },
        emptyContent = {
            Text(
                text = stringResource(R.string.agencies_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        }
    ) { agency ->
        AgencyRow(agency, onAgencyClick)
    }
}

@Composable
private fun AgencyRow(agency: AgencyContact, onClick: (AgencyContact) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = agency.url != null) { onClick(agency) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_maps_directions_bus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(agency.name, style = MaterialTheme.typography.bodyLarge)
                if (agency.url != null) {
                    Text(
                        text = agency.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        // Slight divider between agency entries, matching the pre-migration list.
        HorizontalDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun AgenciesScreenSuccessPreview() {
    ObaTheme {
        AgenciesScreen(
            state = ListUiState.Success(
                listOf(
                    AgencyContact("1", "King County Metro", null, "https://kingcounty.gov/metro", null),
                    AgencyContact("40", "Sound Transit", null, "https://soundtransit.org", null),
                    AgencyContact("97", "No-website Transit", null, null, null)
                )
            ),
            onRetry = {}, onAgencyClick = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AgenciesScreenLoadingPreview() {
    ObaTheme {
        AgenciesScreen(state = ListUiState.Loading, onRetry = {}, onAgencyClick = {}, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AgenciesScreenErrorPreview() {
    ObaTheme {
        AgenciesScreen(state = ListUiState.Error, onRetry = {}, onAgencyClick = {}, onBack = {})
    }
}
