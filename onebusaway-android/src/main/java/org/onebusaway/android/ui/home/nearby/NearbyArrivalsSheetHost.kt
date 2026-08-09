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

import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.arrivals.components.RouteArrivalRow
import org.onebusaway.android.ui.arrivals.convertArrivals
import org.onebusaway.android.ui.compose.ReportListContentHeight
import org.onebusaway.android.ui.compose.navigationBarBottomPadding
import org.onebusaway.android.util.DisplayFormat
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
    // Bay labels resolved once per row set rather than per row recomposition — each is a string-resource
    // lookup, and the row's own content changes far more often than its bay does.
    val context = LocalContext.current
    val stopLabels = remember(rows, context) { rows.associate { it.key to it.bay.label(context) } }
    Surface(color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = navigationBarBottomPadding())
        ) {
            // Names what the drawer is, which the peek alone can't: it opens uninvited on zoom, so a
            // rider who didn't ask for it needs to see at a glance that these are the routes around
            // them and not the one stop they last tapped. Scrolls with the list rather than pinning —
            // once they're reading rows they know what they're reading, and the sheet is short enough
            // that a stuck header would be a real cost.
            item(key = TITLE_KEY) {
                Text(
                    text = stringResource(R.string.nearby_arrivals_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() }
                )
            }
            items(rows, key = { it.key }) { row ->
                RouteArrivalRow(
                    group = row.group,
                    actionsFor = actionsFor,
                    isFavorite = row.group.routeId in favoriteRouteIds,
                    callbacks = callbacks,
                    stopLabel = stopLabels[row.key]
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

    // The laid-out height, so the host can fit the peek to a short list — the same measurement the
    // per-stop panel makes, so both go through the one helper.
    ReportListContentHeight(
        listState,
        contentKey = rows.takeIf { it.isNotEmpty() },
        onContentHeight = onContentHeight
    )
}

/**
 * How a bay reads on a row: its name, then the compass direction it serves. At a real transit centre the
 * direction is what separates the two sides of an intersection — "Pine St & 4th Ave" exists twice.
 *
 * The direction goes through [DisplayFormat.stopDirectionText] like every other stop surface in the app
 * (the focus banner, search results, the arrivals header), so a rider reads "Southeast bound" rather
 * than the feed's raw `SE` — and reads it translated. An unrecognised code resolves to nothing and the
 * bay shows as its bare name.
 *
 * The stop code is deliberately left off. It is the most prominent thing the feed publishes about a bay
 * but the least useful thing to read here: this list is scanned down the *route* column, and a leading
 * number on every row is noise the eye has to skip past. A rider who wants the code gets it on the
 * stop's own panel, one tap away.
 */
internal fun NearbyBay.label(context: Context): String {
    val bound = DisplayFormat.stopDirectionText(context, direction)
    return if (bound != null) "$name ($bound)" else name
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
internal fun rememberNearbyRowActions(
    state: NearbyArrivalsUiState,
    rows: List<NearbyRouteRow>
): (ArrivalInfo) -> ArrivalActions? {
    val loaded = state as? NearbyArrivalsUiState.Loaded
    // Resolved once per response into a trip-keyed map, then looked up — the shape the other three
    // arrivals hosts use (`content.actions[it.tripId]`). Building on demand instead would re-resolve
    // references and re-parse the route's hex colour on every call, and the callers are hot: the ETA
    // strip recomposes once a second off the live clock, and the alert-glyph check walks every trip in
    // every row. Cheap map hits either way now.
    val actions = remember(loaded, rows) {
        val snapshot = loaded?.arrivals ?: return@remember emptyMap()
        rows.asSequence()
            .flatMap { it.group.trips.asSequence() }
            .associateBy(ArrivalInfo::tripId) { arrival ->
                val route = snapshot.route(arrival.routeId)
                ArrivalActions(
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
    return remember(actions) { { arrival: ArrivalInfo -> actions[arrival.tripId] } }
}

/** Whether the last response was truncated, so the drawer can say some bays are missing. */
internal val NearbyArrivalsUiState.limitExceeded: Boolean
    get() = (this as? NearbyArrivalsUiState.Loaded)?.arrivals?.limitExceeded ?: false

private const val TITLE_KEY = "nearby-title"

private const val LIMIT_NOTICE_KEY = "nearby-limit-notice"
