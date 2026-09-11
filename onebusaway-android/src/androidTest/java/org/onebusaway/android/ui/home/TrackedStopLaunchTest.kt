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

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.RouteRevealExtras
import org.onebusaway.android.ui.nav.putStopRouteReveal
import org.onebusaway.android.ui.searchresults.SearchResultMode
import org.onebusaway.android.util.GeoPoint

/** A tracking-card launch (#2166) opens the map, unless Lists mode says stops open on the board (#2319). */
class TrackedStopLaunchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferencesEntryPoint.get(context)
    private var savedMode: String? = null

    @Before
    fun saveMode() {
        savedMode = prefs.getString(SearchResultMode.PREFERENCE_KEY, null)
    }

    @After
    fun restoreMode() {
        prefs.setString(SearchResultMode.PREFERENCE_KEY, savedMode)
    }

    @Test
    fun listsModeSendsATrackingCardLaunchToTheBoardWithTheWatchedRouteSelected() {
        val card = Intent(context, HomeActivity::class.java).putStopRouteReveal(
            stop = FocusedStop(id = "1_100", name = "Pine St", code = null, point = GeoPoint(47.6, -122.3)),
            route = RouteRevealExtras(routeId = "1_40", routeShortName = "40", headsign = "Downtown")
        )

        prefs.setString(SearchResultMode.PREFERENCE_KEY, SearchResultMode.MAP.value)
        assertNull(card.trackedStopBoardRoute(prefs))
        assertEquals(NavRoutes.HOME, launchDestination(card, prefs))

        prefs.setString(SearchResultMode.PREFERENCE_KEY, SearchResultMode.LISTS.value)
        // The route half survives the switch to the board, which preselects that row as the map drawer would.
        val board = NavRoutes.arrivals("1_100", "Pine St", routeId = "1_40", routeHeadsign = "Downtown")
        assertEquals(board, card.trackedStopBoardRoute(prefs))
        assertEquals(board, launchDestination(card, prefs))
    }

    @Test
    fun aPlainStopLaunchIsAnExplicitMapVisitInEitherMode() {
        val showOnMap = Intent(context, HomeActivity::class.java)
            .putExtra(MapParams.STOP_ID, "1_100")
            .putExtra(MapParams.CENTER_LAT, 47.6)
            .putExtra(MapParams.CENTER_LON, -122.3)
        prefs.setString(SearchResultMode.PREFERENCE_KEY, SearchResultMode.LISTS.value)
        assertNull(showOnMap.trackedStopBoardRoute(prefs))
        assertEquals(NavRoutes.HOME, launchDestination(showOnMap, prefs))
    }
}
