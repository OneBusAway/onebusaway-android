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
package org.onebusaway.android.ui.regions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.ListUiState
import org.onebusaway.android.ui.compose.components.ListScreenScaffold
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.RegionUtils

/**
 * Stateful entry point for the region picker screen: collects the ViewModel's state and wires
 * UI events back to it. Region selection is a suspend operation (the region is applied off the main
 * thread) and is terminal — the host navigates away — so its result is delivered through
 * [onRegionSelected] once the selection completes.
 */
@Composable
fun RegionsRoute(
    viewModel: RegionsViewModel,
    onBack: () -> Unit,
    onRegionSelected: (autoSelectDisabled: Boolean) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    RegionsScreen(
        state = state,
        onRetry = { viewModel.load() },
        onRefresh = { viewModel.load(refresh = true) },
        onRegionClick = { region ->
            scope.launch { onRegionSelected(viewModel.selectRegion(region)) }
        },
        onBack = onBack
    )
}

/** Stateless screen content, fully driven by [ListUiState] — previewable and testable. */
@Composable
fun RegionsScreen(
    state: ListUiState<RegionItem>,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRegionClick: (RegionItem) -> Unit,
    onBack: () -> Unit
) {
    ListScreenScaffold(
        title = stringResource(R.string.preferences_region_title),
        onBack = onBack,
        state = state,
        onRetry = onRetry,
        itemKey = { it.id },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_navigation_refresh),
                    contentDescription = stringResource(R.string.region_option_refresh),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { region ->
        RegionRow(region, onRegionClick)
    }
}

@Composable
private fun RegionRow(region: RegionItem, onClick: (RegionItem) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(region) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // alpha(0f) (rather than omitting the icon) keeps rows aligned, like the legacy
            // layout's INVISIBLE check mark
            Icon(
                painter = painterResource(R.drawable.ic_checkmark_holo_light),
                contentDescription = if (region.isCurrent) {
                    stringResource(R.string.checkmark_description)
                } else {
                    null
                },
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(if (region.isCurrent) 1f else 0f)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
                )
                Text(
                    text = distanceText(region.distanceMeters),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        // Slight divider between region entries, matching the pre-migration list.
        HorizontalDivider()
    }
}

/**
 * Formats a distance in meters as miles or kilometers per the user's units preference
 * (matching the legacy picker), or "unavailable" when no distance is known.
 */
@Composable
private fun distanceText(distanceMeters: Float?): String {
    if (distanceMeters == null) {
        return stringResource(R.string.region_unavailable)
    }
    val context = LocalContext.current
    val metric = remember { PreferenceUtils.getUnitsAreMetricFromPreferences(context) }
    val format = remember { NumberFormat.getInstance().apply { maximumFractionDigits = 1 } }
    return if (metric) {
        val km = distanceMeters / 1000.0
        pluralStringResource(R.plurals.distance_kilometers, km.toInt(), format.format(km))
    } else {
        val miles = distanceMeters * RegionUtils.METERS_TO_MILES
        pluralStringResource(R.plurals.distance_miles, miles.toInt(), format.format(miles))
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionsScreenSuccessPreview() {
    ObaTheme {
        RegionsScreen(
            state = ListUiState.Success(
                listOf(
                    RegionItem(1, "Puget Sound", 1500f, isCurrent = true),
                    RegionItem(2, "Tampa Bay", 4_500_000f, isCurrent = false),
                    RegionItem(3, "No-location Region", null, isCurrent = false)
                )
            ),
            onRetry = {}, onRefresh = {}, onRegionClick = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionsScreenLoadingPreview() {
    ObaTheme {
        RegionsScreen(
            state = ListUiState.Loading,
            onRetry = {}, onRefresh = {}, onRegionClick = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionsScreenErrorPreview() {
    ObaTheme {
        RegionsScreen(
            state = ListUiState.Error,
            onRetry = {}, onRefresh = {}, onRegionClick = {}, onBack = {}
        )
    }
}
