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
import org.onebusaway.android.ui.tripinfo.TripInfoLauncher
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.arrivals.ArrivalsListLauncher
import org.onebusaway.android.ui.HomeActivity
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import org.onebusaway.android.R
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.ui.common.Shortcuts
import org.onebusaway.android.ui.search.RouteSearchResult
import org.onebusaway.android.ui.search.StopSearchResult
import org.onebusaway.android.util.ExternalIntents
import org.onebusaway.android.util.ReminderUtils

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

/** Opens a stop's arrivals. */
internal fun AppCompatActivity.openStop(stop: StopListItem) {
    (this as HomeActivity).navigateTo(NavRoutes.arrivals(stop.id, stop.name))
}

/** A stop row's long-press actions; [removeLabel] is the only per-list delta. */
internal fun AppCompatActivity.stopActions(
    stop: StopListItem,
    @StringRes removeLabel: Int,
    onRemove: () -> Unit
): List<RowAction> = listOf(
    RowAction(getString(R.string.my_context_showonmap)) {
        (this as HomeActivity).focusStopOnMap(stop.id, stop.lat, stop.lon)
    },
    RowAction(getString(R.string.my_context_create_shortcut)) {
        Shortcuts.createStopShortcut(this, stop.name, stopArrivalsBuilder(stop))
    },
    RowAction(getString(removeLabel), onRemove)
)

/** Opens a route on the map. */
internal fun AppCompatActivity.openRoute(route: RouteListItem) {
    (this as HomeActivity).showRouteOnMap(route.id)
}

/** A route row's long-press actions; [removeLabel] is the only per-list delta. */
internal fun AppCompatActivity.routeActions(
    route: RouteListItem,
    @StringRes removeLabel: Int,
    onRemove: () -> Unit
): List<RowAction> = buildList {
    add(RowAction(getString(R.string.my_context_showonmap)) {
        (this@routeActions as HomeActivity).showRouteOnMap(route.id)
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

/** Opens the reminder editor for [reminder]. */
internal fun AppCompatActivity.editReminder(reminder: ReminderItem) {
    TripInfoLauncher.start(this, reminder.tripId, reminder.stopId)
}

/**
 * A reminder row's long-press actions: edit / delete (cancels the alarm) / show stop / show route.
 * The host supplies [onShowRoute]/[onShowStop] to navigate to the in-app RouteInfo / Arrivals
 * destinations.
 */
internal fun AppCompatActivity.reminderActions(
    reminder: ReminderItem,
    onShowRoute: (routeId: String) -> Unit,
    onShowStop: (stopId: String) -> Unit,
): List<RowAction> = listOf(
    RowAction(getString(R.string.trip_list_context_edit)) { editReminder(reminder) },
    RowAction(getString(R.string.trip_list_context_delete)) {
        confirmDeleteReminder(this) {
            ReminderUtils.requestDeleteAlarm(
                this, ObaContract.Trips.buildUri(reminder.tripId, reminder.stopId)
            )
        }
    },
    RowAction(getString(R.string.trip_list_context_showstop)) {
        onShowStop(reminder.stopId)
    },
    RowAction(getString(R.string.trip_list_context_showroute)) {
        onShowRoute(reminder.routeId)
    }
)

/** Opens a search-result stop's arrivals. */
internal fun AppCompatActivity.openStopSearchResult(stop: StopSearchResult) {
    (this as HomeActivity).navigateTo(NavRoutes.arrivals(stop.id, stop.serverName))
}

/** Opens a search-result route. */
internal fun AppCompatActivity.openRouteSearchResult(route: RouteSearchResult) {
    (this as HomeActivity).navigateTo(NavRoutes.routeInfo(route.id))
}
