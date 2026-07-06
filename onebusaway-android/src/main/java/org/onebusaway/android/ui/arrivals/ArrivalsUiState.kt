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
    val isRouteFavorite: Boolean,
    /** The representative *active* service-alert id affecting this arrival, or null when none —
     *  drives the per-row alert indicator and the situation it opens on tap (issue #1687 Bug 2). */
    val alertSituationId: String? = null
)

/**
 * A service alert (situation) shown in the arrivals banner. Identity is [contentId] (the dedupe
 * key), not [situationId]: a republished duplicate keeps the same row identity even as its backing
 * situation id rotates. See #1593.
 */
data class AlertItem(
    /** Stable content identity (the dedupe contentKey) — the row's key for hide/show and Compose. */
    val contentId: String,
    /** The representative situation backing this row — resolves the full alert for the detail dialog. */
    val situationId: String,
    /** Every situation id folded into this row (the representative plus any republished duplicates).
     *  Hiding writes them all, and the row counts as hidden when *any* of them is hidden in the DB —
     *  so a hide follows the content across the feed rotating its id. See #1593. */
    val situationIds: Set<String>,
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
    ) : ArrivalsUiState {
        /** True when the stop has any service alert at all — shown *or* hidden — so the header keeps
         *  its alert icon even after the rider hides every alert. */
        val hasAlerts: Boolean get() = alerts.isNotEmpty() || hiddenAlertCount > 0
    }

    data class Error(val message: String) : ArrivalsUiState
}
