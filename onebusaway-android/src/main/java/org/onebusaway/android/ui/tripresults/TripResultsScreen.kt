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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.realtime.TripPlanMonitor
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.ui.compose.components.EtaDurationText
import org.onebusaway.android.ui.compose.components.EtaPartsText
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.RouteBadgeChip
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.tripplan.TripPlanParams

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
    // Side-scrollable so options never get squished: each card sizes to its own content (route/lines,
    // duration, walk distance, time) and the row scrolls horizontally when they overflow the width.
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
private fun OptionCard(
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
    val context = LocalContext.current
    Surface(
        color = background,
        contentColor = textColor,
        shape = MaterialTheme.shapes.small,
        // Wrap to the content width (a sensible floor so short options aren't tiny); the row scrolls.
        modifier = Modifier
            .widthIn(min = 104.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The modes: transit route badges (no comma between), a walk glyph for a walk-only trip, or
            // a mode label for other non-transit trips.
            when (val mode = option.mode) {
                is ModeSummary.Routes -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    mode.badges.forEach { RouteBadgeChip(it.shortName, it.routeColor) }
                }
                ModeSummary.Walk -> Icon(
                    painterResource(R.drawable.ic_directions_walk),
                    contentDescription = stringResource(R.string.step_by_step_non_transit_mode_walk_action),
                    // Sized to the route-badge row height so a walk-only card's first line matches.
                    modifier = Modifier.size(20.dp),
                )
                is ModeSummary.Label -> Text(
                    text = mode.text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            // Duration + walk distance read as one stat group, so they sit tighter together than the
            // card's other lines.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Duration — a leading hourglass + the ETA-pill-formatted trip length.
                MetricRow(R.drawable.hourglass_24, contentDescription = null) {
                    EtaDurationText(minutes = option.durationMinutes)
                }
                // Total walking for the trip — a leading walk glyph mirroring the duration row's hourglass,
                // with the distance styled like the duration (bold value + smaller unit). In the user's units
                // (miles/km, or feet/meters for short walks). Hidden when the trip has no walking.
                if (option.walkDistanceMeters > 0.0) {
                    MetricRow(
                        R.drawable.ic_directions_walk,
                        contentDescription = stringResource(R.string.step_by_step_non_transit_mode_walk_action),
                    ) {
                        EtaPartsText(ConversionUtils.getFormattedDistanceParts(option.walkDistanceMeters, context))
                    }
                }
            }
            // The device-localized departure–arrival range (unwrap the server clock only here).
            Text(
                text = "${DisplayFormat.formatTime(context, option.startTime.epochMs)} – " +
                    DisplayFormat.formatTime(context, option.endTime.epochMs),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/**
 * One option-card stat line: a 16dp leading glyph followed by its value [content]. Shared by the
 * duration and walk-distance rows so their icon size and spacing stay in lockstep.
 */
@Composable
private fun MetricRow(iconRes: Int, contentDescription: String?, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
        )
        content()
    }
}

/**
 * The directions list (or the loading/error state), filling the results sheet below the header. The map
 * is the scaffold body behind the sheet ([TripResultsMap]), not a sibling tab.
 */
@Composable
fun TripResultsList(
    state: TripResultsUiState,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    onFocusPoint: (GeoPoint) -> Unit = {},
) {
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

            // The surface reaches the bottom edge; a bottom content padding lets the final directions row
            // be scrolled clear of the nav chrome without an empty strip below the list.
            is TripResultsUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset),
            ) {
                itemsIndexed(state.directions) { _, item ->
                    DirectionRow(item, onFocusPoint)
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * The trip-results **sheet content**: the header (option cards) plus the directions list. Drives the
 * [TripResultsViewModel] (option cards + directions) — seeds the plan and follows option selection onto
 * the map via the [showItinerary] callback — and starts the background trip-update poller when the user
 * has trip-update notifications enabled. The caller's [showItinerary] both draws and frames the itinerary
 * (deferring the frame until the map is ready), so no separate "map ready" step is needed here.
 *
 * The map itself is deliberately **not** drawn here — the host (the home map's directions focus) owns the
 * map surface; this composable only supplies the results header + directions list, keeping an interactive
 * map out of a draggable bottom sheet where it would fight the sheet's drags (#1640).
 */
@Composable
fun TripResultsSheet(
    itineraries: List<TripItinerary>,
    params: TripPlanParams?,
    resultsViewModel: TripResultsViewModel,
    showItinerary: (TripItinerary) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    listBottomInset: Dp = 0.dp,
) {
    val state by resultsViewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Seed from the completed plan + point the map at the first itinerary (the old bindResults).
    LaunchedEffect(itineraries) {
        resultsViewModel.setItineraries(itineraries, initialIndex = 0)
        itineraries.firstOrNull()?.let { showItinerary(it) }
        maybeStartTripUpdates(activity, params, itineraries, index = 0)
    }

    // Follow the selected option onto the map (the old observeSelection). Read [itineraries] and
    // [params] through rememberUpdatedState so the long-lived collector always sees the latest plan —
    // keying the effect on resultsViewModel alone would pin the first snapshot, so a later selection
    // could arm trip updates with a stale itinerary list *or* a stale request after new results arrive
    // (selectedItinerary is a no-replay SharedFlow, so keeping one collector — rather than restarting it
    // — also can't drop a concurrent emission).
    val currentItineraries by rememberUpdatedState(itineraries)
    val currentParams by rememberUpdatedState(params)
    LaunchedEffect(resultsViewModel) {
        resultsViewModel.selectedItinerary.collect { (index, itinerary) ->
            showItinerary(itinerary)
            maybeStartTripUpdates(activity, currentParams, currentItineraries, index)
        }
    }

    Column(modifier) {
        TripResultsHeader(
            state = state,
            onSelectOption = resultsViewModel::selectOption,
        )
        TripResultsList(state, Modifier.weight(1f), bottomInset = listBottomInset, onFocusPoint = onFocusPoint)
    }
}

/**
 * Arms the trip-plan-change monitor ([TripPlanMonitor]) for the selected itinerary when trip-update
 * notifications are enabled. [params] is the request that produced [itineraries]; it's null when the
 * results were restored from a notification re-entry (the request isn't reconstructed there), in which
 * case there is nothing to re-plan, so monitoring isn't re-armed.
 */
private fun maybeStartTripUpdates(
    activity: Activity,
    params: TripPlanParams?,
    itineraries: List<TripItinerary>,
    index: Int,
) {
    val itinerary = itineraries.getOrNull(index) ?: return
    if (params == null) return

    val context = activity.applicationContext
    val notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(NotificationChannels.TRIP_PLAN_UPDATES_ID)
        channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    } else {
        PreferencesEntryPoint.get(context)
            .getBoolean(R.string.preference_key_trip_plan_notifications, true)
    }
    if (!notificationsEnabled) return

    // The notification re-opens the activity that launched monitoring (HomeActivity, which hosts the
    // trip-plan destination) tagged with the TRIP_PLAN route — see TripPlanMonitorService.notifyChange.
    TripPlanMonitor.start(activity, params, itinerary, activity.javaClass)
}

@Composable
private fun DirectionRow(item: DirectionItem, onFocusPoint: (GeoPoint) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val hasSubItems = item.subItems.isNotEmpty()
    val point = item.focusPoint
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // An expandable row (a chevron) only toggles its sub-steps; only leaf rows send the map
                // to their point. So a parent never moves the map — its expanded leaves do.
                .clickable(enabled = hasSubItems || point != null) {
                    if (hasSubItems) {
                        expanded = !expanded
                    } else {
                        point?.let(onFocusPoint)
                    }
                }
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
            item.subItems.forEach { sub -> SubDirectionRow(sub, onFocusPoint) }
        }
    }
}

@Composable
private fun SubDirectionRow(item: DirectionItem, onFocusPoint: (GeoPoint) -> Unit) {
    val point = item.focusPoint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = point != null) {
                point?.let(onFocusPoint)
            }
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
                ItineraryOption(
                    mode = ModeSummary.Routes(listOf(RouteBadge("8", 0xFF1B6EF3.toInt()))),
                    durationMinutes = 32, startTime = ServerTime(0L), endTime = ServerTime(32 * 60_000L),
                    walkDistanceMeters = 800.0,
                ),
                ItineraryOption(
                    mode = ModeSummary.Routes(listOf(RouteBadge("48", null), RouteBadge("11", null))),
                    durationMinutes = 41, startTime = ServerTime(0L), endTime = ServerTime(41 * 60_000L),
                    walkDistanceMeters = 400.0,
                )
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
