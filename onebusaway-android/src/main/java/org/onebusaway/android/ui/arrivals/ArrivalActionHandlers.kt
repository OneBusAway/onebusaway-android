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

import org.onebusaway.android.map.ShowRouteRequest
import org.onebusaway.android.ui.tripinfo.TripInfoLauncher
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher
import org.onebusaway.android.ui.arrivals.dialogs.RouteFavoriteHost
import org.onebusaway.android.ui.arrivals.dialogs.StopDetailsHost
import org.onebusaway.android.ui.arrivals.dialogs.showSituationDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.onebusaway.android.R
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.report.ui.InfrastructureIssueLauncher
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.ReminderUtils

/**
 * Builds the [ArrivalActionHandler] shared by the standalone arrivals activity and the map panel.
 * The only behavioral difference is [onShowRouteOnMap]: the standalone launches HomeActivity in
 * route mode, while the panel drives the existing map. Everything else (favorite/alert dialogs,
 * navigation, report flow) is identical, so it lives here once.
 */
fun createArrivalActionHandler(
    activity: AppCompatActivity,
    viewModel: ArrivalsViewModel,
    currentContent: () -> ArrivalsUiState.Content?,
    // Carries the arrival's stop in the request, so route mode can narrow to the stop-relevant direction.
    onShowRouteOnMap: (ShowRouteRequest) -> Unit,
    // How to show the alert hide/undo snackbar — supplied by the host so the dialog isn't tied to a
    // specific View (the standalone activity anchors to its root; Compose hosts use a SnackbarHost).
    showUndoSnackbar: (messageRes: Int, actionRes: Int?, onAction: (() -> Unit)?) -> Unit,
    // How to open trip details — defaults to launching TripDetailsActivity (standalone/sheet hosts);
    // the in-NavHost arrivals destination overrides it to navigate to the trip-details destination.
    onShowTrip: (tripId: String, stopId: String) -> Unit = { tripId, stopId ->
        TripDetailsLauncher.Builder(activity, tripId)
            .setStopId(stopId)
            .setScrollMode(TripDetailsLauncher.SCROLL_MODE_STOP)
            .start()
    },
    // How to open the reminder editor for an arrival's trip context — defaults to re-entering
    // HomeActivity via the launcher facade (standalone hosts); the in-NavHost arrivals sites
    // override it to navigate the TRIP_INFO destination directly.
    onEditReminder: (args: ReminderEditorArgs) -> Unit = { args ->
        TripInfoLauncher.start(activity, args)
    }
): ArrivalActionHandler = object : ArrivalActionHandler {

    override fun onRouteFavorite(actions: ArrivalActions) {
        // Pure ViewModel operation now: the dialog is Compose ([RouteFavoriteHost]) and the
        // favoriting write + route-details fetch live in the repository. We just raise the request.
        viewModel.requestRouteFavorite(actions)
    }

    override fun onShowVehiclesOnMap(arrival: ArrivalInfo) {
        recordRoute(arrival)
        // Pass the arrival's stop so route mode shows only the direction (stops + vehicles) serving it.
        onShowRouteOnMap(ShowRouteRequest(arrival.routeId, arrival.stopId))
    }

    override fun onShowTripStatus(arrival: ArrivalInfo) {
        recordRoute(arrival)
        onShowTrip(arrival.tripId, arrival.stopId)
    }

    /** Records the route in recents/search before navigating (the legacy DBUtil.addRouteToDB). */
    fun recordRoute(arrival: ArrivalInfo) {
        DatabaseEntryPoint.get(activity).routeRecorder()
            .recordFromArrival(arrival.routeId, arrival.shortName, arrival.routeLongName, arrival.headsign)
    }

    override fun onSetReminder(arrival: ArrivalInfo) {
        if (!ReminderUtils.shouldShowReminders()) {
            Toast.makeText(activity, R.string.reminder_not_enabled, Toast.LENGTH_SHORT).show()
            return
        }
        // A partial/omitted API response can leave tripId/stopId blank (they default to ""); the editor
        // can't open without both, so bail with a toast rather than tripping ReminderEditorArgs' require().
        if (arrival.tripId.isBlank() || arrival.stopId.isBlank()) {
            Toast.makeText(activity, R.string.failed_to_set_reminder, Toast.LENGTH_SHORT).show()
            return
        }
        onEditReminder(
            ReminderEditorArgs(
                tripId = arrival.tripId,
                stopId = arrival.stopId,
                routeId = arrival.routeId,
                routeName = arrival.shortName,
                stopName = currentContent()?.header?.name.orEmpty(),
                headsign = arrival.headsign,
                departTime = arrival.reminderDepartureTime,
                stopSequence = arrival.stopSequence,
                serviceDate = arrival.serviceDate,
                vehicleId = arrival.vehicleId,
            )
        )
    }

    override fun onShowRouteSchedule(scheduleUrl: String) {
        ExternalIntents.goToUrl(activity, scheduleUrl)
    }

    override fun onReportArrivalProblem(actions: ArrivalActions) {
        val content = currentContent() ?: return
        val arrival = content.arrivals.firstOrNull { it.tripId == actions.tripId } ?: return
        InfrastructureIssueLauncher.startWithService(
            activity,
            activity.getString(R.string.ri_selected_service_trip),
            content.header.stopId,
            content.header.name,
            content.stopCode,
            content.stopLat,
            content.stopLon,
            arrival.toTripReportContext(),
            actions.agencyName,
            actions.blockId
        )
    }

    override fun onShowAlert(alertId: String) {
        val alert = viewModel.alertDetails(alertId) ?: return
        showSituationDialog(
            activity = activity,
            alert = alert,
            onMarkRead = { viewModel.markAlertRead(alertId) },
            onHide = { viewModel.hideAlert(alertId) },
            onUnhide = { viewModel.unhideAlert(alertId) },
            onHideAll = {
                viewModel.hideAllRecordedAlerts()
                PreferenceUtils.saveBoolean(
                    activity.getString(R.string.preference_key_hide_alerts), true
                )
            },
            onDismiss = { isAlertHidden -> if (isAlertHidden) viewModel.manualRefresh() },
            showUndoSnackbar = showUndoSnackbar
        )
    }

    override fun onHideAlert(alert: AlertItem) {
        viewModel.hideAlert(alert)
    }

    override fun onShowStopDetails() {
        // Pure ViewModel operation now: the dialog is Compose ([StopDetailsHost]).
        viewModel.requestStopDetails()
    }

    override fun onReportStopProblem() {
        val content = currentContent() ?: return
        InfrastructureIssueLauncher.startWithService(
            activity,
            activity.getString(R.string.ri_selected_service_stop),
            content.header.stopId,
            content.header.name,
            content.stopCode,
            content.stopLat,
            content.stopLon
        )
    }
}
