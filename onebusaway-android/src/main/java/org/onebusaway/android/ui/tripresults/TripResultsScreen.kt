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
package org.onebusaway.android.ui.tripresults

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.directions.realtime.RealtimeService
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.map.DirectionsMapViewModel
import org.onebusaway.android.map.compose.NoOpObaMapCallbacks
import org.onebusaway.android.map.compose.ObaMap
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.opentripplanner.api.model.Itinerary

/**
 * The results header: the (1–3) itinerary option cards plus the list/map tab row. Shown above the
 * directions list / map frame. Empty until the first [TripResultsUiState.Success].
 */
@Composable
fun TripResultsHeader(
    state: TripResultsUiState,
    onSelectOption: (Int) -> Unit,
    onTabSelected: (showMap: Boolean) -> Unit
) {
    val success = state as? TripResultsUiState.Success ?: return
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            success.options.forEachIndexed { index, option ->
                OptionCard(
                    option = option,
                    selected = index == success.selectedIndex,
                    onClick = { onSelectOption(index) }
                )
            }
        }
        val selectedTab = if (success.showMap) 1 else 0
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(false) },
                text = { Text(stringResource(R.string.trip_plan_list_view)) },
                icon = { Icon(painterResource(R.drawable.ic_list), contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(true) },
                text = { Text(stringResource(R.string.trip_plan_map_view)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrivals_styleb_action_map),
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@Composable
private fun RowScope.OptionCard(
    option: ItineraryOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = colorResource(
        if (selected) R.color.trip_plan_card_background_selected else R.color.trip_plan_card_background
    )
    val textColor = colorResource(
        if (selected) R.color.trip_plan_header_text_selected else R.color.trip_plan_header_text
    )
    Surface(
        color = background,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(option.durationText, style = MaterialTheme.typography.bodyMedium, color = textColor)
            Text(option.intervalText, style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
    }
}

/**
 * The directions list (or the loading/error state). Shown when the list tab is selected; the map tab
 * shows the declarative [ObaMap] instead.
 */
@Composable
fun TripResultsList(state: TripResultsUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.md_theme_surfaceContainer))
    ) {
        when (state) {
            TripResultsUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))

            is TripResultsUiState.Error -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )

            is TripResultsUiState.Success -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.directions) { _, item ->
                    DirectionRow(item)
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Stateful trip-results entry hosted inline in the trip-plan bottom sheet (Tier 1: replaces the former
 * `TripResultsFragment`). Owns the [TripResultsViewModel] (option cards + directions) and a
 * directions-mode [DirectionsMapViewModel]; renders the header plus either the directions list or the declarative
 * [ObaMap] per the list/map tab, frames the selected itinerary once the map settles, and starts the
 * background trip-update poller when the user has trip-update notifications enabled.
 */
@Composable
fun TripResults(
    itineraries: List<Itinerary>,
    modifier: Modifier = Modifier,
) {
    val resultsViewModel: TripResultsViewModel = hiltViewModel()
    val mapViewModel: DirectionsMapViewModel = hiltViewModel(key = "tripResultsMap")
    val state by resultsViewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Re-frame the selected itinerary on the next camera idle (set on itinerary / map-shown change).
    var needsFraming by remember { mutableStateOf(false) }

    // Seed from the completed plan + point the map at the first itinerary (the old bindResults).
    LaunchedEffect(itineraries) {
        resultsViewModel.setItineraries(itineraries, initialIndex = 0, showMap = false)
        itineraries.firstOrNull()?.let {
            mapViewModel.showItinerary(it)
            needsFraming = true
        }
        maybeStartTripUpdates(activity, itineraries, index = 0)
    }

    // Follow the selected option onto the map (the old observeSelection).
    LaunchedEffect(resultsViewModel) {
        resultsViewModel.selectedItinerary.collect { (index, itinerary) ->
            mapViewModel.showItinerary(itinerary)
            needsFraming = true
            maybeStartTripUpdates(activity, itineraries, index)
        }
    }

    val showMap = (state as? TripResultsUiState.Success)?.showMap == true

    // Frame the itinerary once the map is shown + settled (the old observeMapReady): the map VM
    // publishes its camera on each idle, so the first non-null camera while shown is "map ready".
    LaunchedEffect(resultsViewModel, showMap) {
        mapViewModel.camera.collect { camera ->
            if (camera != null && needsFraming && showMap) {
                needsFraming = false
                mapViewModel.frameDirections()
            }
        }
    }

    Column(modifier) {
        TripResultsHeader(
            state = state,
            onSelectOption = resultsViewModel::selectOption,
            onTabSelected = { show ->
                resultsViewModel.toggleMap(show)
                if (show) needsFraming = true
            },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showMap) {
                ObaMap(
                    host = mapViewModel.host,
                    callbacks = NoOpObaMapCallbacks,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TripResultsList(state)
            }
        }
    }
}

/**
 * Starts the background trip-update poller for the selected itinerary when trip-update notifications are
 * enabled — ported verbatim from the former `TripResultsFragment.maybeStartRealtimeUpdates`.
 */
private fun maybeStartTripUpdates(activity: Activity, itineraries: List<Itinerary>, index: Int) {
    val bundle = Bundle().apply {
        putSerializable(OTPConstants.ITINERARIES, ArrayList(itineraries))
        putInt(OTPConstants.SELECTED_ITINERARY, index)
    }
    val context = activity.applicationContext
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(Application.CHANNEL_TRIP_PLAN_UPDATES_ID)
        if (channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE) {
            RealtimeService.start(activity, bundle)
        }
    } else if (PreferencesEntryPoint.get(context)
            .getBoolean(R.string.preference_key_trip_plan_notifications, true)
    ) {
        RealtimeService.start(activity, bundle)
    }
}

@Composable
private fun DirectionRow(item: DirectionItem) {
    var expanded by remember { mutableStateOf(false) }
    val hasSubItems = item.subItems.isNotEmpty()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasSubItems) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            DirectionIcon(item.iconRes)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (item.isTransit) FontWeight.Medium else FontWeight.Normal
                )
                item.placeAndHeadsign?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.agency?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.extra?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hasSubItems) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) {
            item.subItems.forEach { sub -> SubDirectionRow(sub) }
        }
    }
}

@Composable
private fun SubDirectionRow(item: DirectionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DirectionIcon(item.iconRes)
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A 24dp step icon (gray-tinted, matching the legacy adapter), or blank space to keep alignment. */
@Composable
private fun DirectionIcon(iconRes: Int) {
    if (iconRes != DirectionItem.NO_ICON) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colorResource(R.color.trip_option_icon_tint),
            modifier = Modifier.size(24.dp)
        )
    } else {
        Spacer(Modifier.size(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun TripResultsPreview() {
    ObaTheme {
        val state = TripResultsUiState.Success(
            options = listOf(
                ItineraryOption("Route 8", "32 min", "3:45p - 4:17p"),
                ItineraryOption("Route 48", "41 min", "3:50p - 4:31p")
            ),
            selectedIndex = 0,
            directions = listOf(
                DirectionItem(NO_ICON_PREVIEW, "1. Walk to Pine St & 3rd Ave"),
                DirectionItem(
                    NO_ICON_PREVIEW,
                    "2. Route 8 3:52p",
                    placeAndHeadsign = "Toward Rainier Beach",
                    agency = "Metro Transit",
                    isTransit = true,
                    subItems = listOf(DirectionItem(NO_ICON_PREVIEW, "Capitol Hill Station"))
                )
            ),
            showMap = false
        )
        Column {
            TripResultsHeader(state, onSelectOption = {}, onTabSelected = {})
            TripResultsList(state)
        }
    }
}

private const val NO_ICON_PREVIEW = -1
