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
package org.onebusaway.android.map.compose

import android.content.res.Configuration
import android.content.res.Resources
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.RouteTrips
import kotlinx.coroutines.delay
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.getRouteDisplayName
import java.util.concurrent.TimeUnit

/**
 * The vehicle marker info-window content (shared across map flavors): route + headsign, a
 * schedule-deviation status chip, occupancy silhouettes, the last-updated line, and the "more info"
 * chevron. Rendered as the content of the Google `MarkerInfoWindow` and of the MapLibre info-window
 * `ComposeView`.
 */
@Composable
fun VehicleInfoWindow(status: ObaTripStatus, isRealtime: Boolean, response: RouteTrips) {
    val res = LocalContext.current.resources
    // "Now" in the server clock domain: the age is measured against the vehicle's last real-time fix
    // (status.lastLocationUpdateTime, falling back to lastUpdateTime) — both server AVL timestamps —
    // so a skewed device clock must not leak into it (#1612).
    val nowMs = rememberServerNowMs(response.currentTimeMs)
    // The window only opens for an already-rendered vehicle, so its trip/route are in the refs;
    // guard the unreachable null instead of dereferencing (the legacy getTrip/getRoute would NPE).
    val trip = response.trip(status.activeTripId) ?: return
    val route = response.route(trip.routeId) ?: return
    val deviationMin = TimeUnit.SECONDS.toMinutes(status.scheduleDeviation)

    // [isRealtime] is the drawn marker's live-vs-scheduled flag (from the renderer), so the window can't
    // disagree with the icon; the arrival listings share the scheduled-vs-deviation coloring via ArrivalInfoUtils.
    VehicleInfoWindowContent(
        title = getRouteDisplayName(route) + " " +
            stringResource(R.string.trip_info_separator) + " " +
            MyTextUtils.formatDisplayText(trip.headsign),
        statusLabel = if (isRealtime) {
            ArrivalInfoUtils.computeArrivalLabelFromDelay(res, deviationMin)
        } else {
            stringResource(R.string.stop_info_scheduled)
        },
        statusColor = colorResource(ArrivalInfoUtils.statusColor(isRealtime, deviationMin)),
        occupancyDots = if (isRealtime) occupancyDots(status.occupancyStatus) else 0,
        lastUpdated = lastUpdatedText(res, isRealtime, status, nowMs),
    )
}

/**
 * The stateless bubble layout: route + headsign title, a colored schedule-status chip with up to
 * three occupancy silhouettes beside it, and the last-updated line with the "more info" chevron. Split
 * from [VehicleInfoWindow] (which derives these values from the API models) so the layout is
 * previewable in isolation.
 */
@Composable
private fun VehicleInfoWindowContent(
    title: String,
    statusLabel: String,
    statusColor: Color,
    occupancyDots: Int,
    lastUpdated: String,
) {
    // This window draws its own bubble (each flavor returns the whole view from getInfoWindow, so the
    // SDK adds no chrome) and is pre-rendered inside ObaTheme, so it follows the app's light/dark
    // theme via the Material color scheme rather than a fixed white bubble with dark text.
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .widthIn(max = 280.dp)
    ) {
        Text(
            text = title,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusLabel,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(statusColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )

            if (occupancyDots > 0) {
                Spacer(Modifier.width(5.dp))
                OccupancyMeter(occupancyDots)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lastUpdated,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_navigation_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * A three-position occupancy meter indicating how full the vehicle is; the first [dots] (0–3, from
 * [occupancyDots]) positions show a filled silhouette and the rest stay blank. A subtle frame wraps
 * all three positions whether or not they're filled, so the meter keeps a constant size.
 */
@Composable
private fun OccupancyMeter(dots: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        // Always reserve all three positions so the frame keeps a constant size; empty ones are blank
        // spacers rather than transparent icons, so we don't pay to draw an invisible vector.
        repeat(3) { i ->
            if (i < dots) {
                Icon(
                    painter = painterResource(R.drawable.ic_occupancy),
                    contentDescription = null,
                    tint = colorResource(R.color.stop_info_occupancy),
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Spacer(Modifier.size(14.dp))
            }
        }
    }
}

@Preview(name = "Occupancy meter — light", showBackground = true)
@Preview(
    name = "Occupancy meter — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OccupancyMeterPreview() {
    ObaTheme {
        Surface {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (dots in 0..3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$dots:",
                            fontSize = 12.sp,
                            modifier = Modifier.width(24.dp),
                        )
                        OccupancyMeter(dots)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VehicleInfoWindowPreview() {
    ObaTheme {
        VehicleInfoWindowContent(
            title = "44 — Ballard via Wallingford",
            statusLabel = "3 min late",
            statusColor = colorResource(R.color.stop_info_delayed),
            // Two silhouettes: FEW_SEATS_AVAILABLE / STANDING_ROOM_ONLY.
            occupancyDots = 2,
            lastUpdated = "Estimate from data updated 12 sec ago",
        )
    }
}

/** Number of occupancy silhouettes to show (0–3) for the vehicle's occupancy level. */
private fun occupancyDots(occupancy: Occupancy?): Int = when (occupancy) {
    null, Occupancy.EMPTY -> 0
    Occupancy.MANY_SEATS_AVAILABLE -> 1
    Occupancy.FEW_SEATS_AVAILABLE, Occupancy.STANDING_ROOM_ONLY -> 2
    Occupancy.CRUSHED_STANDING_ROOM_ONLY, Occupancy.FULL, Occupancy.NOT_ACCEPTING_PASSENGERS -> 3
}

private fun lastUpdatedText(res: Resources, realtime: Boolean, status: ObaTripStatus, nowMs: Long): String {
    if (!realtime) {
        return res.getString(R.string.vehicle_last_updated_scheduled)
    }
    val last = if (status.lastLocationUpdateTime != 0L) {
        status.lastLocationUpdateTime
    } else {
        status.lastUpdateTime
    }
    // The route-map marker is continuously extrapolated from the last fix, so it's a model
    // estimate, not the raw position — frame the age as "Estimate from data updated …" (legacy
    // VehicleOverlay behavior for an extrapolating marker).
    return formatDataAge(res, TimeUnit.MILLISECONDS.toSeconds(nowMs - last), estimating = true)
}

/**
 * A monotonic device clock (`SystemClock.elapsedRealtime`) that updates once per second, for live
 * "… ago" age text. Lets a marker info window tick even though its data (status/response) is unchanged
 * between polls (strong skipping would otherwise freeze it). Monotonic, not wall-clock, so measuring an
 * interval against it is immune to NTP corrections / the user changing the device clock.
 */
@Composable
internal fun rememberElapsedRealtimeMs(): Long {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    return now
}

/**
 * A live "now" in the **server** clock domain, anchored on [serverTimeMs] (the poll's server
 * `currentTime`) and advanced by the device's elapsed time since this response was observed. Ticking
 * against the server clock keeps age text (`… ago`) free of device clock skew — the age is measured
 * against the same clock that stamped the vehicle timestamp (#1612). The offset is captured once per
 * [serverTimeMs] (each poll) from the **monotonic** clock, so composition lag never accumulates and a
 * device clock change can't corrupt it: at capture the result equals [serverTimeMs], then it counts up
 * in real elapsed device time.
 */
@Composable
internal fun rememberServerNowMs(serverTimeMs: Long): Long {
    val deviceStartMs = remember(serverTimeMs) { SystemClock.elapsedRealtime() }
    val deviceNowMs = rememberElapsedRealtimeMs()
    return serverNowMs(serverTimeMs, deviceStartMs, deviceNowMs)
}

/** [serverTimeMs] advanced by elapsed device time. The delta is clamped at 0 so a fresh anchor (the
 *  synchronous [remember] capture racing the 1s-lagged ticker) never yields a "now" before the anchor.
 *  Extracted from [rememberServerNowMs] as a pure function so it's JVM-unit-testable. */
internal fun serverNowMs(serverTimeMs: Long, deviceStartMs: Long, deviceNowMs: Long): Long =
    serverTimeMs + (deviceNowMs - deviceStartMs).coerceAtLeast(0L)

/**
 * Formats [elapsedSeconds] as "Data updated N min M sec ago" (or "… M sec ago" under a minute).
 * When [estimating] is true the marker position is an extrapolated estimate, so the age is framed
 * as "Estimate from data updated …" instead.
 */
internal fun formatDataAge(res: Resources, elapsedSeconds: Long, estimating: Boolean = false): String {
    val s = elapsedSeconds.coerceAtLeast(0)
    val secRes = if (estimating) R.string.vehicle_estimate_from_update_sec else R.string.vehicle_last_updated_sec
    val minSecRes =
        if (estimating) R.string.vehicle_estimate_from_update_min_and_sec else R.string.vehicle_last_updated_min_and_sec
    return if (s < 60) {
        res.getString(secRes, s)
    } else {
        res.getString(minSecRes, TimeUnit.SECONDS.toMinutes(s), s % 60)
    }
}
