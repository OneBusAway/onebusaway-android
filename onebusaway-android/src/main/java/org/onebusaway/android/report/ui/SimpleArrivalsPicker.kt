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
package org.onebusaway.android.report.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.components.ArrivalCard
import org.onebusaway.android.ui.arrivals.components.ArrivalRowContent
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.components.LoadingContent

/**
 * A lightweight arrivals picker for the report flow: lists a stop's upcoming arrivals and reports
 * the chosen one back via [onPick]. Reuses the arrivals [ArrivalsViewModel] (configured to show all
 * routes) and the shared row content, but with no per-arrival menu — tapping a row picks it.
 *
 * Hosted inside the report flow's vertically-scrolling Column (which measures children with unbounded
 * height), so this uses a plain [Column], not a LazyColumn — a scrollable component can't be measured with
 * infinite max height.
 */
@Composable
fun SimpleArrivalsPicker(
    viewModel: ArrivalsViewModel,
    onPick: (ArrivalInfo) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The picker is short-lived; a one-shot load is enough (no 60s polling like the live screens).
    LaunchedEffect(viewModel) { viewModel.refresh() }
    // Wrap in a Surface so the rows get the theme's content color (the report container has none,
    // which would otherwise leave the default text black/invisible on its dark background).
    Surface(Modifier.fillMaxWidth()) {
        when (val current = state) {
            is ArrivalsUiState.Content ->
                if (current.arrivals.isEmpty()) {
                    EmptyTrip()
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        current.arrivals.forEach { arrival ->
                            ArrivalCard(onClick = { onPick(arrival) }) {
                                ArrivalRowContent(
                                    arrival,
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }

            is ArrivalsUiState.Error -> EmptyTrip()

            ArrivalsUiState.Loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingContent()
            }
        }
    }
}

@Composable
private fun EmptyTrip() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.ri_no_trip),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}
