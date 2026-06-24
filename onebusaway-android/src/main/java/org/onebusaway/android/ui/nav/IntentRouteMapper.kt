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

import android.app.SearchManager
import android.content.Intent
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.ui.arrivals.ArrivalsIntents
import org.onebusaway.android.ui.mylists.MyTabs
import org.onebusaway.android.util.ReminderUtils

/**
 * Translates an incoming external [Intent] into the in-app NavHost route it should open, or null to
 * leave the home/map path untouched. Lifted out of `HomeActivity` so the route
 * precedence/dispatch is a pure, JVM-testable decision ([decide]) sitting behind the thin Android
 * `Intent`/`Uri`/JSON reads ([routeForIntent] -> [read]).
 *
 * Routing only — side-effect free: the domain mutations some intents imply (the `add-region` URL apply,
 * the FCM arrival-reminder clear) run in `HomeActivity.applyIntentSideEffects`, which shares the
 * [ADD_REGION_SCHEME]/[ADD_REGION_HOST] constants from here and [ReminderUtils.ARRIVAL_PAYLOAD_KEY].
 */
object IntentRouteMapper {

    /** The exported `onebusaway://add-region` deep link (an exported manifest intent-filter). */
    const val ADD_REGION_SCHEME = "onebusaway"
    const val ADD_REGION_HOST = "add-region"

    private const val NIGHT_LIGHT_ACTIVITY = "NightLightActivity"

    /** The Android-free projection of an incoming intent that [decide] consumes; produced by [read]. */
    data class RouteIntent(
        /** An explicit in-app route ([NavRoutes.EXTRA_NAV_ROUTE]); cross-screen launches carry it verbatim. */
        val navRoute: String? = null,
        /** The exported add-region deep link — routes nowhere (stays on home/map; URLs apply as a side effect). */
        val isAddRegion: Boolean = false,
        /** System search ([Intent.ACTION_SEARCH]); [searchQuery] is the (possibly empty) query. */
        val isSearch: Boolean = false,
        val searchQuery: String = "",
        /** The FCM arrival payload was present; [arrivalStopId] is its parsed stop id (null if unparseable). */
        val hasArrivalPayload: Boolean = false,
        val arrivalStopId: String? = null,
        /** Trip-details launch carrying its ids as extras (arrivals "show trip" / vehicle tap / reminder). */
        val tripDetailsId: String? = null,
        val tripDetailsStopId: String? = null,
        val tripDetailsScrollMode: String? = null,
        /** An old pinned night-light shortcut (the frozen NightLightActivity component alias). */
        val isNightLight: Boolean = false,
        /** The tab tag carried by an old pinned `tab://<tag>` My* shortcut (null if not a tab link). */
        val tabTag: String? = null,
        /** Data-URI path segments (`content://<authority>/<path>/{id}`), read by path since the authority is flavor-specific. */
        val pathSegments: List<String> = emptyList(),
        /** Optional pre-load stop title for the stops data-URI ([ArrivalsIntents.STOP_NAME]). */
        val arrivalsStopName: String? = null,
    )

    /**
     * The screen an incoming intent resolves to (or [None]). Kept separate from the encoded [NavRoutes]
     * string so [decide] stays `Uri`-free and unit-testable; [toRoute] does the encoding.
     */
    sealed interface RouteDecision {
        /** Stay on the home/map path. */
        object None : RouteDecision
        data class Verbatim(val route: String) : RouteDecision
        data class Search(val query: String) : RouteDecision
        data class Arrivals(val stopId: String, val stopName: String? = null) : RouteDecision
        data class RouteInfo(val routeId: String) : RouteDecision
        data class TripDetails(val tripId: String, val stopId: String?, val scrollMode: String?) : RouteDecision
        object NightLight : RouteDecision
        data class MyRoutes(val tab: String) : RouteDecision
        data class MyStops(val tab: String) : RouteDecision
    }

    /** Translates an incoming external [intent] into the NavHost route to open, or null to stay on home/map. */
    fun routeForIntent(intent: Intent?): String? =
        intent?.let { decide(read(it)).toRoute() }

    /**
     * The pure route-precedence decision over [input] — the exact branch order of the former
     * `HomeActivity.routeForIntent`.
     */
    fun decide(input: RouteIntent): RouteDecision {
        // In-app / cross-screen launches carry their destination route verbatim (see [navIntent]).
        input.navRoute?.let { return RouteDecision.Verbatim(it) }
        // The exported add-region deep link applies custom API URLs as a side effect (HomeActivity); for
        // routing it stays on the home/map path (the legacy handler went Home).
        if (input.isAddRegion) return RouteDecision.None
        // System search (HomeActivity is the default_searchable target): open the search destination.
        if (input.isSearch) return RouteDecision.Search(input.searchQuery)
        // The FCM arrival payload clears its fired reminder as a side effect; here it just opens arrivals
        // (or stays put if the stop id couldn't be parsed). Its presence short-circuits the later branches.
        if (input.hasArrivalPayload) {
            return input.arrivalStopId?.let { RouteDecision.Arrivals(it) } ?: RouteDecision.None
        }
        // Trip details carries its args as extras (no data URI).
        input.tripDetailsId?.let {
            return RouteDecision.TripDetails(it, input.tripDetailsStopId, input.tripDetailsScrollMode)
        }
        // Old pinned night-light launcher shortcuts target the frozen NightLightActivity alias.
        if (input.isNightLight) return RouteDecision.NightLight
        // Old pinned launcher shortcuts carry a `tab://<tag>` data URI → the matching My* list route.
        input.tabTag?.let {
            return if (it == MyTabs.RECENT_ROUTES) RouteDecision.MyRoutes(it) else RouteDecision.MyStops(it)
        }
        // Explicit-component screen intents carrying a content://<authority>/<path>/{id} data URI (read by
        // path segment, since the authority is flavor-specific). MapParams.* focus / route-mode launches
        // have no data URI and resolve to None — they stay map behavior.
        return when (input.pathSegments.firstOrNull()) {
            ObaContract.Stops.PATH ->
                input.pathSegments.lastOrNull()
                    ?.let { RouteDecision.Arrivals(it, input.arrivalsStopName) } ?: RouteDecision.None
            ObaContract.Routes.PATH ->
                input.pathSegments.lastOrNull()?.let { RouteDecision.RouteInfo(it) } ?: RouteDecision.None
            else -> RouteDecision.None
        }
    }

    /** Encodes a [RouteDecision] into its navigable [NavRoutes] string (or null for [RouteDecision.None]). */
    private fun RouteDecision.toRoute(): String? = when (this) {
        RouteDecision.None -> null
        is RouteDecision.Verbatim -> route
        is RouteDecision.Search -> NavRoutes.search(query)
        is RouteDecision.Arrivals -> NavRoutes.arrivals(stopId, stopName)
        is RouteDecision.RouteInfo -> NavRoutes.routeInfo(routeId)
        is RouteDecision.TripDetails -> NavRoutes.tripDetails(tripId, stopId, scrollMode)
        RouteDecision.NightLight -> NavRoutes.NIGHT_LIGHT
        is RouteDecision.MyRoutes -> NavRoutes.myRoutes(tab)
        is RouteDecision.MyStops -> NavRoutes.myStops(tab)
    }

    /** Projects the Android [intent] into the plain [RouteIntent] that [decide] consumes. */
    private fun read(intent: Intent): RouteIntent {
        val data = intent.data
        // Match the legacy behavior: presence is "extra value is non-null", so a present-but-null payload
        // still short-circuits to None rather than falling through to the path branches.
        val arrivalJson = intent.getStringExtra(ReminderUtils.ARRIVAL_PAYLOAD_KEY)
        // null unless the URI is a `tab://` link (MyTabs returns the tag iff the scheme matches).
        val tabTag = data?.let { MyTabs.defaultTabFromUri(it) }
        return RouteIntent(
            navRoute = intent.getStringExtra(NavRoutes.EXTRA_NAV_ROUTE),
            isAddRegion = data?.scheme == ADD_REGION_SCHEME && data.host == ADD_REGION_HOST,
            isSearch = intent.action == Intent.ACTION_SEARCH,
            searchQuery = intent.getStringExtra(SearchManager.QUERY).orEmpty(),
            hasArrivalPayload = arrivalJson != null,
            arrivalStopId = arrivalJson?.let { ReminderUtils.getStopIdFromPayload(it) },
            // Read off every intent, but the NavRoutes.ARG_* keys are internal extras set only by in-app
            // navIntent launches, and decide() checks navRoute/add-region/search/arrival-payload first — so
            // an external intent only reaches the trip-details branch if it both carries no higher-priority
            // signal and happens to reuse these keys. Keep them internal; don't repurpose ARG_TRIP_ID for an
            // exported intent-filter or an external app could silently deep-link into trip details.
            tripDetailsId = intent.getStringExtra(NavRoutes.ARG_TRIP_ID),
            tripDetailsStopId = intent.getStringExtra(NavRoutes.ARG_STOP_ID),
            tripDetailsScrollMode = intent.getStringExtra(NavRoutes.ARG_SCROLL_MODE),
            isNightLight = intent.component?.className?.endsWith(NIGHT_LIGHT_ACTIVITY) == true,
            tabTag = tabTag,
            pathSegments = data?.pathSegments ?: emptyList(),
            arrivalsStopName = intent.getStringExtra(ArrivalsIntents.STOP_NAME),
        )
    }
}
