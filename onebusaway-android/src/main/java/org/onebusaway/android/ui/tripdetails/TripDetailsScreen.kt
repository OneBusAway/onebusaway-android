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
package org.onebusaway.android.ui.tripdetails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.RealtimeIndicator
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Refresh interval matching the legacy TripDetailsListFragment (fixed 60s). */
private const val REFRESH_PERIOD_MS = 60_000L

/** Lifecycle-scoped 60s polling loop (RESUMED-only), mirroring the arrivals screen. */
@Composable
private fun TripDetailsPolling(viewModel: TripDetailsViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val sinceLast = System.currentTimeMillis() - viewModel.lastResponseTimeMs
            delay((REFRESH_PERIOD_MS - sinceLast).coerceIn(0L, REFRESH_PERIOD_MS))
            while (isActive) {
                viewModel.refresh()
                delay(REFRESH_PERIOD_MS)
            }
        }
    }
}

/** Stateful entry point: collects state, runs polling, and forwards events to the host. */
@Composable
fun TripDetailsRoute(
    viewModel: TripDetailsViewModel,
    onBack: () -> Unit,
    onShowOnMap: (routeId: String) -> Unit,
    onStopClick: (stopId: String, name: String, direction: String?) -> Unit,
    onSetDestinationReminder: (stopIndex: Int) -> Unit,
    onShowTrajectory: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    TripDetailsPolling(viewModel)
    TripDetailsScreen(
        state = state,
        refreshing = refreshing,
        onBack = onBack,
        onRefresh = viewModel::manualRefresh,
        onShowOnMap = { viewModel.routeId()?.let(onShowOnMap) },
        onStopClick = onStopClick,
        onSetDestinationReminder = onSetDestinationReminder,
        onShowTrajectory = onShowTrajectory,
    )
}

/** Stateless screen content, fully driven by [TripDetailsUiState] — previewable and testable. */
@Composable
fun TripDetailsScreen(
    state: TripDetailsUiState,
    refreshing: Boolean = false,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShowOnMap: () -> Unit,
    onStopClick: (String, String, String?) -> Unit,
    onSetDestinationReminder: (Int) -> Unit,
    onShowTrajectory: () -> Unit = {},
) {
    val content = state as? TripDetailsUiState.Content
    Scaffold(
        topBar = {
            ObaTopAppBar(title = stringResource(R.string.trip_status), onBack = onBack) {
                if (content != null) {
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_navigation_refresh),
                                contentDescription = stringResource(R.string.stop_info_option_refresh),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // "Show on map" from a trip opens the single-trip live view (not the route map).
                    IconButton(onClick = onShowOnMap) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_location_map),
                            contentDescription = stringResource(R.string.stop_info_option_showonmap),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Speed-estimation trajectory graph (debug).
                    IconButton(onClick = onShowTrajectory) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Trajectory",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                TripDetailsUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))

                is TripDetailsUiState.Content ->
                    TripStopList(state, onStopClick, onSetDestinationReminder)

                is TripDetailsUiState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            }
        }
    }
}

@Composable
private fun TripStopList(
    content: TripDetailsUiState.Content,
    onStopClick: (String, String, String?) -> Unit,
    onSetDestinationReminder: (Int) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val lineColor = Color(content.lineColorArgb)
    val vehicleColor = colorResource(content.header.statusColor)
    val passedColor = colorResource(R.color.trip_details_passed)
    val notPassedColor = colorResource(R.color.trip_details_not_passed)
    // Scroll once to the vehicle/focused/destination stop (item 0 is the header, so +1).
    LaunchedEffect(Unit) {
        if (content.scrollToIndex >= 0) listState.scrollToItem(content.scrollToIndex + 1)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "header") { TripHeaderSection(content.header) }
        itemsIndexed(content.stops, key = { i, stop -> "$i:${stop.stopId}" }) { i, stop ->
            TripStopRow(
                stop = stop,
                lineColor = lineColor,
                vehicleColor = vehicleColor,
                textColor = if (stop.isPassed) passedColor else notPassedColor,
                isRealtime = content.header.isRealtime,
                onClick = { onStopClick(stop.stopId, stop.name, stop.direction) },
                // Legacy: only stops past the first two can anchor a reminder (need a "before" stop)
                onLongClick = if (i > 1) ({ onSetDestinationReminder(i) }) else null
            )
        }
    }
}

@Composable
private fun TripHeaderSection(header: TripHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineBadge(header.routeShortName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = header.headsign,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            header.tripShortName?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = header.agencyName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            header.vehicleId?.let {
                Text(
                    text = stringResource(R.string.trip_details_vehicle, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(6.dp))
            StatusPill(header.statusText, colorResource(header.statusColor), header.isRealtime)
        }
    }
}

/**
 * The lateness-colored status pill (white text on the deviation color). When the trip is real-time,
 * the radiating indicator sits inside the pill, right of the text, in the same white as the lettering.
 */
@Composable
private fun StatusPill(text: String, color: Color, isRealtime: Boolean) {
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            if (isRealtime) {
                Spacer(Modifier.width(6.dp))
                RealtimeIndicator(color = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripStopRow(
    stop: TripStopItem,
    lineColor: Color,
    vehicleColor: Color,
    textColor: Color,
    isRealtime: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val decoration = if (stop.canceled) TextDecoration.LineThrough else TextDecoration.None
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransitRail(
            stop = stop,
            lineColor = lineColor,
            vehicleColor = vehicleColor,
            isRealtime = isRealtime,
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight()
        )
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = decoration
            )
            Text(
                text = stop.timeText,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                textDecoration = decoration
            )
        }
    }
}

/**
 * The trip's vertical transit line for one stop: a colored line (top half omitted on the first stop,
 * bottom half on the last) with a dot, plus the vehicle / focused-stop / destination markers in the
 * left gutter — a Compose port of `trip_details_listitem`'s transit_line + marker icons.
 */
@Composable
private fun TransitRail(
    stop: TripStopItem,
    lineColor: Color,
    vehicleColor: Color,
    isRealtime: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = 36.dp.toPx()
            val cy = size.height / 2f
            val top = if (stop.linePosition == LinePosition.FIRST) cy else 0f
            val bottom = if (stop.linePosition == LinePosition.LAST) cy else size.height
            drawLine(lineColor, Offset(cx, top), Offset(cx, bottom), strokeWidth = 3.dp.toPx())
            drawCircle(lineColor, radius = 6.5.dp.toPx(), center = Offset(cx, cy))
        }
        when {
            stop.isVehicleHere -> {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(vehicleColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bus),
                        contentDescription = stringResource(R.string.trip_details_current_bus_position),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (isRealtime) {
                    // Above and to the right of the 18dp bus (which starts at 5dp), badge-style.
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .offset(y = (-12).dp)
                            .size(8.dp)
                    ) {
                        RealtimeIndicator(color = vehicleColor, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            stop.pin == StopPin.FOCUSED -> Icon(
                painter = painterResource(R.drawable.ic_drawer_maps_place),
                contentDescription = stringResource(R.string.trip_details_current_stop),
                tint = lineColor,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 5.dp)
                    .size(18.dp)
            )

            stop.pin == StopPin.DESTINATION -> Icon(
                painter = painterResource(R.drawable.ic_content_flag),
                contentDescription = stringResource(R.string.trip_details_dest_stop),
                tint = lineColor,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 5.dp)
                    .size(18.dp)
            )

            else -> {}
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — the header + a few transit-line stop states (TripHeader/TripStopItem are previewable).

@Preview(showBackground = true)
@Composable
private fun TripDetailsPreview() {
    ObaTheme {
        val lineColor = colorResource(R.color.theme_primary)
        val vehicleColor = colorResource(R.color.stop_info_delayed)
        Column {
            TripHeaderSection(
                TripHeader(
                    routeShortName = "8",
                    headsign = "Capitol Hill - Rainier Beach",
                    tripShortName = null,
                    agencyName = "Metro Transit",
                    vehicleId = "1234",
                    statusText = "2 min, 30 sec late (updated 3:01 PM)",
                    statusColor = R.color.stop_info_delayed,
                    isRealtime = true
                )
            )
            val passedColor = colorResource(R.color.trip_details_passed)
            val notPassedColor = colorResource(R.color.trip_details_not_passed)
            fun stop(name: String, time: String, passed: Boolean, line: LinePosition, vehicle: Boolean = false, pin: StopPin = StopPin.NONE) =
                TripStopItem("s_$name", name, "S", time, false, passed, line, vehicle, pin)
            TripStopRow(stop("Denny Way", "3:00 PM", true, LinePosition.FIRST), lineColor, vehicleColor, passedColor, true, {}, null)
            TripStopRow(stop("Pine St & 3rd Ave", "3:03 PM", true, LinePosition.MIDDLE, vehicle = true), lineColor, vehicleColor, passedColor, true, {}, null)
            TripStopRow(stop("Broadway & E Pine", "3:08 PM", false, LinePosition.MIDDLE, pin = StopPin.FOCUSED), lineColor, vehicleColor, notPassedColor, true, {}, null)
            TripStopRow(stop("Mount Baker TC", "3:20 PM", false, LinePosition.LAST, pin = StopPin.DESTINATION), lineColor, vehicleColor, notPassedColor, true, {}, null)
        }
    }
}
