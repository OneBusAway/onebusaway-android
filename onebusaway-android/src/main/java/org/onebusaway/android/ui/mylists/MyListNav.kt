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
package org.onebusaway.android.ui.mylists

import org.onebusaway.android.ui.tripinfo.confirmDeleteReminder
import org.onebusaway.android.ui.arrivals.ArrivalsListLauncher
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import org.onebusaway.android.R
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.ui.common.Shortcuts
import org.onebusaway.android.ui.search.RouteSearchResult
import org.onebusaway.android.ui.search.StopSearchResult
import org.onebusaway.android.util.ExternalIntents

/**
 * Shared navigation and row-action wiring for the My-tab list destinations (recent/starred ×
 * stops/routes, plus reminders and the search results). They're hosted as composables by both the
 * `My*` tab activities (`MyTabsScreen`) and the Compose home screen, with identical tap/long-press
 * behavior except for the remove-action label — so it lives here as [AppCompatActivity] extensions
 * rather than a base class.
 */

private fun AppCompatActivity.stopArrivalsBuilder(stop: StopListItem) =
    ArrivalsListLauncher.Builder(this, stop.id)
        .setStopName(stop.name)

/** Opens a stop's arrivals via [onOpen] (the host supplies a NavController-backed navigation). */
internal fun openStop(stop: StopListItem, onOpen: (stopId: String, stopName: String?) -> Unit) =
    onOpen(stop.id, stop.name)

/** A stop row's long-press actions; [removeLabel] is the only per-list delta. */
internal fun AppCompatActivity.stopActions(
    stop: StopListItem,
    @StringRes removeLabel: Int,
    onShowOnMap: (stopId: String, lat: Double, lon: Double) -> Unit,
    onRemove: () -> Unit
): List<RowAction> = listOf(
    RowAction(getString(R.string.my_context_showonmap)) {
        onShowOnMap(stop.id, stop.lat, stop.lon)
    },
    RowAction(getString(R.string.my_context_create_shortcut)) {
        Shortcuts.createStopShortcut(this, stop.name, stopArrivalsBuilder(stop))
    },
    RowAction(getString(removeLabel), onRemove)
)

/** Opens a route on the map. */
internal fun openRoute(route: RouteListItem, onShowOnMap: (routeId: String) -> Unit) {
    onShowOnMap(route.id)
}

/** A route row's long-press actions; [removeLabel] is the only per-list delta. */
internal fun AppCompatActivity.routeActions(
    route: RouteListItem,
    @StringRes removeLabel: Int,
    onShowOnMap: (routeId: String) -> Unit,
    onRemove: () -> Unit
): List<RowAction> = buildList {
    add(RowAction(getString(R.string.my_context_showonmap)) {
        onShowOnMap(route.id)
    })
    route.url?.let { url ->
        add(RowAction(getString(R.string.my_context_show_schedule)) {
            ExternalIntents.goToUrl(this@routeActions, url)
        })
    }
    add(RowAction(getString(R.string.my_context_create_shortcut)) {
        Shortcuts.createRouteShortcut(this@routeActions, route.id, route.shortName)
    })
    add(RowAction(getString(removeLabel), onRemove))
}

/** Opens the reminder editor for [reminder] via [onEdit] (a NavController-backed navigation). */
internal fun editReminder(reminder: ReminderItem, onEdit: (tripId: String, stopId: String) -> Unit) =
    onEdit(reminder.tripId, reminder.stopId)

/**
 * A reminder row's long-press actions: edit / delete (cancels the alarm) / show stop / show route.
 * The host supplies [onEdit]/[onShowRoute]/[onShowStop] to navigate to the in-app TripInfo / RouteInfo /
 * Arrivals destinations.
 */
internal fun AppCompatActivity.reminderActions(
    reminder: ReminderItem,
    onEdit: (tripId: String, stopId: String) -> Unit,
    onShowRoute: (routeId: String) -> Unit,
    onShowStop: (stopId: String) -> Unit,
): List<RowAction> = listOf(
    RowAction(getString(R.string.trip_list_context_edit)) { editReminder(reminder, onEdit) },
    RowAction(getString(R.string.trip_list_context_delete)) {
        confirmDeleteReminder(this) {
            DatabaseEntryPoint.get(this).reminderRepository()
                .deleteReminderInBackground(reminder.tripId, reminder.stopId)
        }
    },
    RowAction(getString(R.string.trip_list_context_showstop)) {
        onShowStop(reminder.stopId)
    },
    RowAction(getString(R.string.trip_list_context_showroute)) {
        onShowRoute(reminder.routeId)
    }
)

/** Opens a search-result stop's arrivals via [onOpen] (a NavController-backed navigation). */
internal fun openStopSearchResult(stop: StopSearchResult, onOpen: (stopId: String, stopName: String?) -> Unit) =
    onOpen(stop.id, stop.serverName)

/** Opens a search-result route via [onOpen] (a NavController-backed navigation). */
internal fun openRouteSearchResult(route: RouteSearchResult, onOpen: (routeId: String) -> Unit) =
    onOpen(route.id)
