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
package org.onebusaway.android.ui.home.nearby

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.convertArrivals
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.util.GeoPoint

/**
 * The transit-centre drawer's body (#2107): every route leaving every bay in the current viewport,
 * one row per (route, direction, bay), in the shared (agency, line, headsign) order.
 *
 * Route-first rather than grouped by stop, because the rider this is for is standing in the centre
 * looking for *their route* — grouping by stop would just reproduce the bay-by-bay scan they are
 * already doing by hand.
 */
@Composable
internal fun NearbyArrivalsSheetHost(
    rows: List<NearbyRouteRow>,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    favoriteRouteIds: Set<String>,
    callbacks: ArrivalRowCallbacks,
    limitExceeded: Boolean,
    onContentHeight: (heightPx: Int) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = navigationBarBottomPadding())
        ) {
            items(rows, key = { it.key }) { row ->
                RouteArrivalRow(
                    group = row.group,
                    actionsFor = actionsFor,
                    isFavorite = row.group.routeId in favoriteRouteIds,
                    callbacks = callbacks,
                    stopLabel = row.bay.label()
                )
            }
            // The server truncates its arrivals list by distance from the viewport centre, so a
            // truncated response drops the farthest bays outright. Say so rather than letting a rider
            // conclude their route doesn't stop here.
            if (limitExceeded) {
                item(key = LIMIT_NOTICE_KEY) {
                    Text(
                        text = stringResource(R.string.nearby_arrivals_limit_exceeded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    // The laid-out height, so the host can fit the peek to a short list. Real layout, not an estimate:
    // Material3 measures sheet content at full container height regardless of the peek, so if the last
    // item is present its bottom is the true content height; if it isn't, the list is taller than the
    // screen and an exact number isn't needed. Same measurement ArrivalsPanel makes.
    val contentHeightPx by remember(listState, rows) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (rows.isNotEmpty() && last != null && last.index == info.totalItemsCount - 1) {
                last.offset + last.size
            } else {
                null
            }
        }
    }
    contentHeightPx?.let { px -> LaunchedEffect(px) { onContentHeight(px) } }
}

/**
 * How a bay reads on a row: its code when the feed publishes one (that is what's painted on the pole),
 * then its name, then the compass direction it serves. At a real transit centre the direction is what
 * separates the two sides of an intersection — "Pine St & 4th Ave" exists twice.
 */
internal fun NearbyBay.label(): String {
    val head = code?.takeIf(String::isNotBlank)?.let { "$it · $name" } ?: name
    return direction?.takeIf(String::isNotBlank)?.let { "$head ($it)" } ?: head
}

/**
 * Builds the drawer's rows from the query's state. Lives in composition because turning wire arrivals
 * into [ArrivalInfo]s needs a `Context` — which is exactly why [NearbyArrivalsViewModel] stops at the
 * resolved response and stays a plain JVM unit test.
 */
@Composable
internal fun rememberNearbyRouteRows(state: NearbyArrivalsUiState): List<NearbyRouteRow> {
    val context = LocalContext.current
    val loaded = state as? NearbyArrivalsUiState.Loaded ?: return emptyList()
    return remember(loaded, context) {
        val snapshot = loaded.arrivals
        val arrivals = convertArrivals(
            context,
            snapshot.arrivals,
            snapshot.serverNow,
            includeArrivalDepartureInStatusLabel = false
        )
        nearbyRouteRows(
            arrivals = arrivals,
            bayOf = { stopId ->
                snapshot.stop(stopId)?.let { stop ->
                    NearbyBay(
                        id = stop.id,
                        name = stop.name.orEmpty(),
                        code = stop.stopCode,
                        direction = stop.direction,
                        point = GeoPoint(stop.location.latitude, stop.location.longitude)
                    )
                }
            },
            agencyNameOf = { snapshot.route(it.routeId)?.agencyId?.let(snapshot::agencyName) }
        )
    }
}

/**
 * Resolves each row's route-level actions from the response's own references — the colour and names the
 * badge draws, the agency the ordering sorts on, and the schedule URL whose absence hides that menu
 * item. `alertSituationId` is left null: the alert glyph opens a dialog owned by the focused stop's
 * banner, which this many-bay list has no equivalent of (see [rememberNearbyRowCallbacks]).
 */
@Composable
internal fun rememberNearbyActionsFor(
    state: NearbyArrivalsUiState
): (ArrivalInfo) -> ArrivalActions? {
    val loaded = state as? NearbyArrivalsUiState.Loaded
    return remember(loaded) {
        fun(arrival: ArrivalInfo): ArrivalActions? {
            val snapshot = loaded?.arrivals ?: return null
            val route = snapshot.route(arrival.routeId)
            return ArrivalActions(
                tripId = arrival.tripId,
                routeId = arrival.routeId,
                routeShortName = route?.shortName,
                routeLongName = route?.longName,
                routeColor = route?.color,
                scheduleUrl = route?.url,
                agencyName = route?.agencyId?.let(snapshot::agencyName),
                blockId = null
            )
        }
    }
}

/** Whether the last response was truncated, so the drawer can say some bays are missing. */
internal val NearbyArrivalsUiState.limitExceeded: Boolean
    get() = (this as? NearbyArrivalsUiState.Loaded)?.arrivals?.limitExceeded ?: false

private const val LIMIT_NOTICE_KEY = "nearby-limit-notice"
