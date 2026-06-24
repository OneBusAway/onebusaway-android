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
package org.onebusaway.android.ui.report.customerservice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** Stateful entry point: collects the ViewModel's state and forwards contact actions to the host. */
@Composable
fun CustomerServiceRoute(
    viewModel: CustomerServiceViewModel,
    onBack: () -> Unit,
    onEmail: (AgencyContact) -> Unit,
    onWeb: (AgencyContact) -> Unit,
    onPhone: (AgencyContact) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CustomerServiceScreen(
        state = state,
        onRetry = viewModel::load,
        onEmail = onEmail,
        onWeb = onWeb,
        onPhone = onPhone,
        onBack = onBack
    )
}

/** Stateless screen content, fully driven by [ListUiState] — previewable and testable. */
@Composable
fun CustomerServiceScreen(
    state: ListUiState<AgencyContact>,
    onRetry: () -> Unit,
    onEmail: (AgencyContact) -> Unit,
    onWeb: (AgencyContact) -> Unit,
    onPhone: (AgencyContact) -> Unit,
    onBack: () -> Unit
) {
    ListScreenScaffold(
        title = stringResource(R.string.rt_customer_service),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        itemKey = { it.id }
    ) { agency ->
        AgencyContactRow(agency, onEmail, onWeb, onPhone)
    }
}

@Composable
private fun AgencyContactRow(
    agency: AgencyContact,
    onEmail: (AgencyContact) -> Unit,
    onWeb: (AgencyContact) -> Unit,
    onPhone: (AgencyContact) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = agency.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (agency.email != null) {
                ContactIconButton(R.drawable.ic_customer_service_email, onClick = { onEmail(agency) })
            }
            if (agency.url != null) {
                ContactIconButton(R.drawable.ic_customer_service_web, onClick = { onWeb(agency) })
            }
            if (agency.phone != null) {
                ContactIconButton(R.drawable.ic_customer_service_phone, onClick = { onPhone(agency) })
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ContactIconButton(iconRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CustomerServiceScreenPreview() {
    ObaTheme {
        CustomerServiceScreen(
            state = ListUiState.Success(
                listOf(
                    AgencyContact("1", "King County Metro", "rider@kingcounty.gov", "https://kingcounty.gov/metro", "206-555-0100"),
                    AgencyContact("40", "Sound Transit", null, "https://soundtransit.org", "888-555-7433"),
                    AgencyContact("97", "Phone-only Transit", null, null, "206-555-2000")
                )
            ),
            onRetry = {}, onEmail = {}, onWeb = {}, onPhone = {}, onBack = {}
        )
    }
}
