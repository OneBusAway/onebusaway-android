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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
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
 * The results header: the (1–3) itinerary option cards. Shown above the directions list, pinned at the
 * top of the results sheet. Empty until the first [TripResultsUiState.Success]. The map is revealed by
 * dragging this sheet down — it renders as the scaffold body behind it ([TripResultsMap]) — so there is
 * no list/map tab here (#1640).
 */
@Composable
fun TripResultsHeader(
    state: TripResultsUiState,
    onSelectOption: (Int) -> Unit
) {
    val success = state as? TripResultsUiState.Success ?: return
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
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
 * The directions list (or the loading/error state), filling the results sheet below the header. The map
 * is the scaffold body behind the sheet ([TripResultsMap]), not a sibling tab.
 */
@Composable
fun TripResultsList(state: TripResultsUiState, modifier: Modifier = Modifier) {
    Box(
        modifier
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
 * The trip-results **sheet content**: the header (option cards) plus the directions list. Drives the
 * [TripResultsViewModel] (option cards + directions) and the directions-mode [DirectionsMapViewModel] —
 * seeds the plan and follows option selection onto the map — and starts the background trip-update
 * poller when the user has trip-update notifications enabled. [DirectionsMapViewModel.showItinerary]
 * both draws and frames the itinerary (deferring the frame until the map is ready), so no separate
 * "map ready" step is needed here.
 *
 * The map itself is deliberately **not** drawn here: it renders as the trip-plan scaffold *body* (behind
 * this sheet) via [TripResultsMap], revealed by dragging this sheet down. That keeps an interactive
 * [ObaMap] out of the draggable bottom sheet, where the sheet's
 * [androidx.compose.material3.BottomSheetScaffold] would otherwise steal its vertical drags (#1640).
 * Both VMs are hoisted to the caller so the body and this sheet share them.
 */
@Composable
fun TripResultsSheet(
    itineraries: List<Itinerary>,
    resultsViewModel: TripResultsViewModel,
    mapViewModel: DirectionsMapViewModel,
    modifier: Modifier = Modifier,
) {
    val state by resultsViewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Seed from the completed plan + point the map at the first itinerary (the old bindResults).
    LaunchedEffect(itineraries) {
        resultsViewModel.setItineraries(itineraries, initialIndex = 0)
        itineraries.firstOrNull()?.let { mapViewModel.showItinerary(it) }
        maybeStartTripUpdates(activity, itineraries, index = 0)
    }

    // Follow the selected option onto the map (the old observeSelection). Read [itineraries] through
    // rememberUpdatedState so the long-lived collector always sees the latest list — keying the effect on
    // resultsViewModel alone would pin the first snapshot, so a later selection could start trip updates
    // for a stale plan after new results arrive (selectedItinerary is a no-replay SharedFlow, so keeping
    // one collector — rather than restarting it — also can't drop a concurrent emission).
    val currentItineraries by rememberUpdatedState(itineraries)
    LaunchedEffect(resultsViewModel) {
        resultsViewModel.selectedItinerary.collect { (index, itinerary) ->
            mapViewModel.showItinerary(itinerary)
            maybeStartTripUpdates(activity, currentItineraries, index)
        }
    }

    Column(modifier) {
        TripResultsHeader(
            state = state,
            onSelectOption = resultsViewModel::selectOption,
        )
        TripResultsList(state, Modifier.weight(1f))
    }
}

/**
 * The trip-results **map**, rendered as the trip-plan scaffold *body* (behind the results sheet) while
 * the map tab is active. Kept out of the draggable sheet so vertical drags pan the map rather than
 * moving the sheet (#1640); [mapViewModel] is the shared directions-mode VM that [TripResultsSheet]
 * drives (draws the selected itinerary, frames it on the first idle).
 */
@Composable
fun TripResultsMap(
    mapViewModel: DirectionsMapViewModel,
    modifier: Modifier = Modifier,
) {
    ObaMap(
        host = mapViewModel.host,
        callbacks = NoOpObaMapCallbacks,
        modifier = modifier,
    )
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
        val channel = manager.getNotificationChannel(NotificationChannels.TRIP_PLAN_UPDATES_ID)
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
            )
        )
        Column {
            TripResultsHeader(state, onSelectOption = {})
            TripResultsList(state)
        }
    }
}

private const val NO_ICON_PREVIEW = -1
