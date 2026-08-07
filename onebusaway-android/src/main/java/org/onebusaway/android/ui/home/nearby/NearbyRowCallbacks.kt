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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.map.render.MapViewport
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.components.ArrivalRowCallbacks
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.HomeViewModel
import org.onebusaway.android.util.ExternalIntents

/**
 * The nearby drawer's row actions.
 *
 * The two taps a rider actually makes here — the row body and an ETA pill — do what this drawer is
 * for: show that route on the map, scoped to the bay the row named, in one focus push so Back returns
 * straight to the list.
 *
 * **The long-press menu's per-stop actions (star, reminder, tracking, report a problem) and the alert
 * glyph are not wired to their own dialogs in this first version**: each needs the stop-scoped
 * `ArrivalsViewModel` and alert store that the per-stop drawer hoists per focused stop, which this
 * list — spanning many bays at once — deliberately does not create. Rather than no-op them, they focus
 * the row's bay, which lands the rider on that stop's own panel where every one of those actions is
 * fully wired. Worth a follow-up: see the PR description.
 */
@Composable
internal fun rememberNearbyRowCallbacks(
    homeViewModel: HomeViewModel,
    rows: List<NearbyRouteRow>,
    undoViewport: () -> MapViewport?,
    onShowTrip: (tripId: String, stopId: String) -> Unit
): ArrivalRowCallbacks {
    val context = LocalContext.current
    val currentRows = rememberUpdatedState(rows)
    val currentOnShowTrip = rememberUpdatedState(onShowTrip)
    return remember(homeViewModel, context) {
        fun bayOf(arrival: ArrivalInfo): NearbyBay? = currentRows.value
            .firstOrNull { it.bay.id == arrival.stopId }
            ?.bay

        fun focusedStop(bay: NearbyBay) = FocusedStop(bay.id, bay.name, bay.code, bay.point)

        // Show the arrival's route on the map, scoped to the bay its row named.
        fun revealAtBay(arrival: ArrivalInfo) {
            val bay = bayOf(arrival) ?: return
            homeViewModel.showNearbyRouteOnMap(
                bay = focusedStop(bay),
                routeId = arrival.routeId,
                shortName = arrival.shortName.orEmpty().ifBlank { arrival.routeId },
                directionId = arrival.directionId,
                headsign = arrival.headsign,
                undoViewport = undoViewport()
            )
        }

        // Focus the bay alone, for the actions that live on the per-stop panel (see the KDoc above).
        fun openBay(arrival: ArrivalInfo) {
            val bay = bayOf(arrival) ?: return
            homeViewModel.revealStop(focusedStop(bay), animate = true)
        }

        ArrivalRowCallbacks(
            onShowVehiclesOnMap = ::revealAtBay,
            onEtaClick = ::revealAtBay,
            // The badge long-press reveals the whole route, unscoped — the same meaning it has in the
            // per-stop drawer, where it deliberately drops the stop/direction scoping.
            onShowRouteOnMap = { arrival ->
                homeViewModel.focusStandaloneRoute(
                    ShowRouteRequest(routeId = arrival.routeId),
                    undoViewport = undoViewport()
                )
            },
            onShowTripStatus = { arrival ->
                currentOnShowTrip.value(arrival.tripId, arrival.stopId)
            },
            onShowRouteSchedule = { scheduleUrl -> ExternalIntents.goToUrl(context, scheduleUrl) },
            onRouteFavorite = { actions -> openBayForRoute(currentRows.value, actions, homeViewModel) },
            onSetReminder = ::openBay,
            onToggleTracking = ::openBay,
            onReportArrivalProblem = { actions ->
                openBayForRoute(currentRows.value, actions, homeViewModel)
            },
            onShowAlert = { /* Alerts are shown by the focused stop's banner; see the KDoc above. */ }
        )
    }
}

/** [ArrivalActions] carries no stop, so the bay is found through the row that produced it. */
private fun openBayForRoute(
    rows: List<NearbyRouteRow>,
    actions: ArrivalActions,
    homeViewModel: HomeViewModel
) {
    val bay = rows.firstOrNull { row -> row.group.trips.any { it.tripId == actions.tripId } }?.bay
        ?: return
    homeViewModel.revealStop(FocusedStop(bay.id, bay.name, bay.code, bay.point), animate = true)
}
