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
package org.onebusaway.android.ui.home

import android.content.Intent
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.ui.fromIntent
import org.onebusaway.android.ui.nav.IntentRouteMapper
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.StopReveal
import org.onebusaway.android.ui.nav.readRouteReveal
import org.onebusaway.android.ui.searchresults.SearchResultMode
import org.onebusaway.android.ui.searchresults.searchResultMode

/** Reuse March's persisted drawer selection, including values imported from SharedPreferences. */
internal const val HOME_SECTION_KEY = "selected_navigation_drawer_position"

internal fun homeSectionRoute(section: Int): String = when (section) {
    1 -> NavRoutes.HOME_STARRED_STOPS
    2 -> NavRoutes.HOME_STARRED_ROUTES
    3 -> NavRoutes.MY_REMINDERS
    else -> NavRoutes.HOME
}

internal fun PreferencesRepository.homeStartDestination(): String = homeSectionRoute(getInt(HOME_SECTION_KEY, 0))

/** Only explicit section choices change the next launch; visiting a map or a detail screen does not. */
internal fun PreferencesRepository.rememberHomeSection(route: String) {
    val section = when (route) {
        NavRoutes.HOME -> 0
        NavRoutes.HOME_STARRED_STOPS -> 1
        NavRoutes.HOME_STARRED_ROUTES -> 2
        NavRoutes.MY_REMINDERS -> 3
        else -> return
    }
    setInt(HOME_SECTION_KEY, section)
}

/**
 * The stop a tracking-card launch (#2166) should open on the mapless board, or null when it should
 * take its usual map reveal. The card's PendingIntent asks for the map — the vehicles are what it
 * cannot show — but a rider in Lists mode has said stops open on the board, and that choice has the
 * last word here as it does in `NavController.openStop` (#2319). The map half of the reveal
 * (`HomeActivity.maybeRevealTrackedRouteFromIntent`) asks this same question, so the two agree.
 */
internal fun Intent.trackedStopOnBoard(prefs: PreferencesRepository): StopReveal? {
    if (prefs.searchResultMode() != SearchResultMode.LISTS || readRouteReveal() == null) return null
    return FocusedStop.fromIntent(this)?.let { StopReveal(it.id, it.name, it.point) }
}

/** Explicit destinations win over the remembered section; only an ordinary launcher opening uses it. */
internal fun launchDestination(intent: Intent, prefs: PreferencesRepository): String = IntentRouteMapper.routeForIntent(intent) ?: intent.trackedStopOnBoard(prefs)?.let { NavRoutes.arrivals(it.stopId, it.name) } ?: if (
    intent.action == Intent.ACTION_MAIN &&
    intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
    intent.data == null &&
    !intent.hasExtra(MapParams.STOP_ID) &&
    !intent.hasExtra(MapParams.ROUTE_ID)
) {
    prefs.homeStartDestination()
} else {
    NavRoutes.HOME
}
