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

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController

/**
 * Navigate to an in-app [route], popping up to HOME and de-duping the top — the single navigation
 * idiom every in-app caller uses (drawer/top-bar actions, the arrivals sheet, the My* lists, the home
 * overlays), replacing the former `HomeViewModel.stageDeepLinkRoute` route latch and its `DeepLinkEffect`
 * consumer. Matches the options that consumer applied, so the back-stack behavior is unchanged: a pushed
 * destination collapses back to HOME, and an already-open destination isn't re-created.
 */
fun NavController.navigateFromHome(route: String) =
    navigate(route) {
        popUpTo(NavRoutes.HOME) { inclusive = false }
        launchSingleTop = true
    }

/**
 * "Show route / stop on the map" expressed as navigation rather than a reach-through to the host's
 * ViewModels. A pushed destination (route info, search, the My* lists) hands the reveal to the HOME
 * back-stack entry's [androidx.lifecycle.SavedStateHandle] and pops back to it; the HOME destination
 * observes these keys, applies them to the shared map/home ViewModels (which it already holds in scope),
 * and consumes them. This is the idiomatic Navigation-Compose "return a result to a previous destination"
 * pattern — observable, process-death-safe, and the consume (set-null) persists.
 *
 * HOME is the NavHost start destination, so it is always on the back stack and [getBackStackEntry] is safe.
 */
const val RESULT_MAP_ROUTE_ID = "mapReveal.routeId"
const val RESULT_MAP_STOP_ID = "mapReveal.stopId"
const val RESULT_MAP_STOP_LAT = "mapReveal.stopLat"
const val RESULT_MAP_STOP_LON = "mapReveal.stopLon"

/** Reveal the map in route mode for [routeId], popping back to HOME. */
fun NavController.revealRouteOnMap(routeId: String) {
    getBackStackEntry(NavRoutes.HOME).savedStateHandle[RESULT_MAP_ROUTE_ID] = routeId
    popBackStack(NavRoutes.HOME, /* inclusive = */ false)
}

/** Reveal the map focused on [stopId] at [lat]/[lon], popping back to HOME. */
fun NavController.revealStopOnMap(stopId: String, lat: Double, lon: Double) {
    getBackStackEntry(NavRoutes.HOME).savedStateHandle.apply {
        set(RESULT_MAP_STOP_ID, stopId)
        set(RESULT_MAP_STOP_LAT, lat)
        set(RESULT_MAP_STOP_LON, lon)
    }
    popBackStack(NavRoutes.HOME, false)
}

/** A complete stop reveal read back off the HOME [SavedStateHandle] — the typed counterpart of the
 *  three [RESULT_MAP_STOP_ID]/[RESULT_MAP_STOP_LAT]/[RESULT_MAP_STOP_LON] keys [revealStopOnMap] writes. */
data class StopReveal(val stopId: String, val lat: Double, val lon: Double)

/**
 * Reads and consumes a pending stop reveal from the HOME [SavedStateHandle] — the symmetric typed *read*
 * for [revealStopOnMap], keeping the `RESULT_MAP_STOP_*` keys and their `Double` types in this one file
 * rather than re-naming them in the consumer. All three keys are cleared together regardless of
 * completeness (so a stale lat/lon pair can't linger past the reveal); returns null when no stop id is
 * present, or — the corrupted/half-restored case the producer never writes — an id without both
 * coordinates.
 */
fun SavedStateHandle.consumeStopReveal(): StopReveal? {
    val stopId = get<String>(RESULT_MAP_STOP_ID)
    val lat = get<Double>(RESULT_MAP_STOP_LAT)
    val lon = get<Double>(RESULT_MAP_STOP_LON)
    set(RESULT_MAP_STOP_ID, null)
    set(RESULT_MAP_STOP_LAT, null)
    set(RESULT_MAP_STOP_LON, null)
    if (stopId == null || lat == null || lon == null) return null
    return StopReveal(stopId, lat, lon)
}
