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
package org.onebusaway.android.ui.arrivals


/** The stop being viewed, for the arrivals screen header. */
data class StopHeader(
    val stopId: String,
    val name: String,
    val direction: String?,
    val isFavorite: Boolean,
    val routeCount: Int
)

/**
 * Per-arrival data the Activity needs to build navigation intents and dialogs, precomputed in the
 * repository (off the [ArrivalInfo] display model and the loaded response) so the ViewModel and
 * composables stay Android-free. Keyed by trip id in [ArrivalsUiState.Content.actions].
 */
data class ArrivalActions(
    val tripId: String,
    val routeId: String,
    val headsign: String,
    val stopId: String,
    val routeShortName: String?,
    val routeLongName: String?,
    /** Route schedule URL; null/blank hides the "show route schedule" menu item. */
    val scheduleUrl: String?,
    val agencyName: String?,
    val blockId: String?,
    val isRouteFavorite: Boolean
)

/** A service alert (situation) shown in the arrivals banner. */
data class AlertItem(
    val id: String,
    val summary: String,
    val severity: AlertSeverity
)

/** Maps ObaSituation severity onto the three banner styles, matching the legacy SituationAlert. */
enum class AlertSeverity { INFO, WARNING, ERROR }

/** One route serving the stop, for the route-filter dialog. */
data class RouteFilterOption(
    val routeId: String,
    val displayName: String,
    val checked: Boolean
)

/** UI state for the arrivals screen. */
sealed interface ArrivalsUiState {

    data object Loading : ArrivalsUiState

    /**
     * @param arrivals the existing [ArrivalInfo] display model, already filtered and sorted
     * @param style one of BuildFlavorUtils.ARRIVAL_INFO_STYLE_*
     * @param isStale true when showing the last good data after a refresh failed
     * @param actions per-arrival navigation/dialog data, keyed by trip id
     * @param alerts active, non-hidden service alerts for the stop
     * @param hiddenAlertCount how many alerts the user has hidden (for the "show hidden" affordance)
     * @param routeFilterOptions every route serving the stop, with current checked state
     * @param filteredRouteCount how many routes the active filter keeps (0 == showing all)
     */
    data class Content(
        val header: StopHeader,
        val arrivals: List<ArrivalInfo>,
        val minutesAfter: Int,
        val style: Int,
        val isStale: Boolean,
        val actions: Map<String, ArrivalActions> = emptyMap(),
        val alerts: List<AlertItem> = emptyList(),
        val hiddenAlertCount: Int = 0,
        val routeFilterOptions: List<RouteFilterOption> = emptyList(),
        val filteredRouteCount: Int = 0,
        val stopCode: String? = null,
        val stopLat: Double = 0.0,
        val stopLon: Double = 0.0,
        val stopUserName: String? = null
    ) : ArrivalsUiState

    data class Error(val message: String) : ArrivalsUiState
}
