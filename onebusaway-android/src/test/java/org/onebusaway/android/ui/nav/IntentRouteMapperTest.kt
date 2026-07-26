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
package org.onebusaway.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.ui.mylists.MyTabs
import org.onebusaway.android.ui.nav.IntentRouteMapper.RouteDecision
import org.onebusaway.android.ui.nav.IntentRouteMapper.RouteIntent
import org.onebusaway.android.ui.nav.IntentRouteMapper.decide
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher

/**
 * Unit tests for the pure route-precedence decision [IntentRouteMapper.decide] — the branch order +
 * dispatch ported out of the former `HomeActivity.routeForIntent` (the Android Intent/Uri/JSON reads
 * stay in `IntentRouteMapper.read`, so only the decision is exercised here).
 */
class IntentRouteMapperTest {

    @Test
    fun `no recognizable fields stays on the home or map path`() {
        assertEquals(RouteDecision.None, decide(RouteIntent()))
    }

    // --- precedence (the branch order is significant) ---

    @Test
    fun `an explicit in-app nav route wins over everything else`() {
        val decision = decide(
            RouteIntent(
                navRoute = "settings",
                isSearch = true,
                searchQuery = "loser",
                hasArrivalPayload = true,
                arrivalStopId = "1_99"
            )
        )
        assertEquals(RouteDecision.Verbatim("settings"), decision)
    }

    @Test
    fun `add-region routes nowhere even when a search action is also present`() {
        // The URLs apply as a side effect (HomeActivity); routing stays on the home/map path.
        assertEquals(
            RouteDecision.None,
            decide(
                RouteIntent(
                    deepLink = ExternalDeepLinks.Target.AddRegion("https://api.example.com", null),
                    isSearch = true,
                    searchQuery = "x"
                )
            )
        )
    }

    @Test
    fun `search beats the data-URI path branches`() {
        val decision = decide(
            RouteIntent(
                isSearch = true,
                searchQuery = "1st ave",
                pathSegments = listOf(DeepLinkUris.STOPS_PATH, "1_75403")
            )
        )
        assertEquals(RouteDecision.Search("1st ave"), decision)
    }

    @Test
    fun `an empty search query still resolves to the search screen`() {
        assertEquals(RouteDecision.Search(""), decide(RouteIntent(isSearch = true)))
    }

    // --- FCM arrival payload (present-ness short-circuits) ---

    @Test
    fun `an arrival payload with a parsed stop id opens that stop's arrivals`() {
        assertEquals(
            RouteDecision.Arrivals("1_75403"),
            decide(RouteIntent(hasArrivalPayload = true, arrivalStopId = "1_75403"))
        )
    }

    @Test
    fun `a present-but-unparseable arrival payload stays put and does not fall through`() {
        // The payload's presence short-circuits later branches, so the trip-details extra is ignored.
        assertEquals(
            RouteDecision.None,
            decide(
                RouteIntent(
                    hasArrivalPayload = true,
                    arrivalStopId = null,
                    tripDetailsId = "trip-should-be-ignored"
                )
            )
        )
    }

    // --- trip details (ids as extras) ---

    @Test
    fun `trip-details extras open the trip-details screen`() {
        assertEquals(
            RouteDecision.TripDetails("trip-1", "1_75403", "arrivals"),
            decide(
                RouteIntent(
                    tripDetailsId = "trip-1",
                    tripDetailsStopId = "1_75403",
                    tripDetailsScrollMode = "arrivals"
                )
            )
        )
    }

    // --- legacy pinned shortcuts ---

    @Test
    fun `a night-light component shortcut opens the night-light screen`() {
        assertEquals(RouteDecision.NightLight, decide(RouteIntent(isNightLight = true)))
    }

    @Test
    fun `a recent-routes tab link opens My Routes`() {
        assertEquals(
            RouteDecision.MyRoutes(MyTabs.RECENT_ROUTES),
            decide(RouteIntent(tabTag = MyTabs.RECENT_ROUTES))
        )
    }

    @Test
    fun `any other tab link opens My Stops on that tab`() {
        assertEquals(
            RouteDecision.MyStops(MyTabs.STARRED_STOPS),
            decide(RouteIntent(tabTag = MyTabs.STARRED_STOPS))
        )
    }

    // --- content-URI path dispatch ---

    @Test
    fun `a stops data-URI opens arrivals with the optional pre-load title`() {
        assertEquals(
            RouteDecision.Arrivals("1_75403", "Pike St & 3rd Ave"),
            decide(
                RouteIntent(
                    pathSegments = listOf(DeepLinkUris.STOPS_PATH, "1_75403"),
                    arrivalsStopName = "Pike St & 3rd Ave"
                )
            )
        )
    }

    @Test
    fun `a routes data-URI opens route info`() {
        assertEquals(
            RouteDecision.RouteInfo("1_100224"),
            decide(RouteIntent(pathSegments = listOf(DeepLinkUris.ROUTES_PATH, "1_100224")))
        )
    }

    @Test
    fun `an unrecognized data-URI path stays on the home or map path`() {
        assertEquals(
            RouteDecision.None,
            decide(RouteIntent(pathSegments = listOf("service_alerts", "abc")))
        )
    }

    // --- cross-platform (iOS-parity) deep links; the URI parsing itself is ExternalDeepLinksTest ---

    @Test
    fun `a view-stop deep link opens that stop's arrivals`() {
        assertEquals(
            RouteDecision.Arrivals("1_75403"),
            decide(RouteIntent(deepLink = ExternalDeepLinks.Target.Stop("1_75403")))
        )
    }

    @Test
    fun `a web trip deep link opens the trip scrolled to the shared stop`() {
        assertEquals(
            RouteDecision.TripDetails("1_18196913", "1_75403", TripDetailsLauncher.SCROLL_MODE_STOP),
            decide(
                RouteIntent(
                    deepLink = ExternalDeepLinks.Target.Trip(
                        tripId = "1_18196913",
                        stopId = "1_75403"
                    )
                )
            )
        )
    }

    @Test
    fun `a deep-link target beats the content data-URI path branches`() {
        assertEquals(
            RouteDecision.Arrivals("1_75403"),
            decide(
                RouteIntent(
                    deepLink = ExternalDeepLinks.Target.Stop("1_75403"),
                    pathSegments = listOf(DeepLinkUris.ROUTES_PATH, "1_100224")
                )
            )
        )
    }
}
