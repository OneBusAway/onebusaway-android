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

import androidx.lifecycle.SavedStateHandle
import org.onebusaway.android.map.MapParams

/** Mechanical SavedStateHandle encoding for [CurrentFocus], kept out of focus transition logic. */
internal object CurrentFocusPersistence {
    private const val KEY_FOCUS_KIND = "home.currentFocus.kind"
    private const val KEY_STOP_ID = "home.focusedStop.id"
    private const val KEY_STOP_NAME = "home.focusedStop.name"
    private const val KEY_STOP_CODE = "home.focusedStop.code"
    private const val KEY_STOP_LAT = "home.focusedStop.lat"
    private const val KEY_STOP_LON = "home.focusedStop.lon"
    private const val KEY_BIKE_STATION = "home.focusedBikeStation.id"
    private const val KEY_ROUTE_ID = "home.currentFocus.route.id"
    private const val KEY_ROUTE_STOP_ID = "home.currentFocus.route.stopId"
    private const val KEY_ROUTE_DIRECTION_ID = "home.currentFocus.route.directionId"
    private const val KEY_ROUTE_ORIGIN_HEADSIGN = "home.currentFocus.route.anchorHeadsign"
    private const val KEY_ROUTE_LEG_IDS = "home.currentFocus.route.legIds"
    private const val KEY_ROUTE_LEG_NAMES = "home.currentFocus.route.legNames"
    private const val KEY_ROUTE_LEG_DIRECTIONS = "home.currentFocus.route.legDirections"

    private const val FOCUS_NONE = "none"
    private const val FOCUS_STOP = "stop"
    private const val FOCUS_ROUTE = "route"
    private const val FOCUS_BIKE = "bike"
    private const val NO_DIRECTION = Int.MIN_VALUE

    fun read(state: SavedStateHandle): CurrentFocus {
        val stop = readStop(state)
        return when (state.get<String>(KEY_FOCUS_KIND)) {
            FOCUS_STOP -> stop?.let { CurrentFocus.Stop(it, readStopRoute(state)) }
                ?: CurrentFocus.None
            FOCUS_ROUTE -> readRouteTarget(state)?.let { CurrentFocus.Route(it) } ?: CurrentFocus.None
            FOCUS_BIKE -> state.get<String>(KEY_BIKE_STATION)?.let { CurrentFocus.BikeStation(it) }
                ?: CurrentFocus.None
            FOCUS_NONE -> CurrentFocus.None
            else -> readLegacyFocus(state, stop)
        }
    }

    fun write(state: SavedStateHandle, focus: CurrentFocus) {
        state[KEY_FOCUS_KIND] = when (focus) {
            CurrentFocus.None -> FOCUS_NONE
            is CurrentFocus.Stop -> FOCUS_STOP
            is CurrentFocus.Route -> FOCUS_ROUTE
            is CurrentFocus.BikeStation -> FOCUS_BIKE
        }
        val stop = (focus as? CurrentFocus.Stop)?.stop
        state[KEY_STOP_ID] = stop?.id
        state[KEY_STOP_NAME] = stop?.name
        state[KEY_STOP_CODE] = stop?.code
        state[KEY_STOP_LAT] = stop?.lat
        state[KEY_STOP_LON] = stop?.lon
        state[KEY_BIKE_STATION] = (focus as? CurrentFocus.BikeStation)?.id

        val target = (focus as? CurrentFocus.Route)?.target
        state[KEY_ROUTE_ID] = target?.routeId
        state[KEY_ROUTE_STOP_ID] = target?.directionStopId
        state[KEY_ROUTE_DIRECTION_ID] = target?.directionId

        val selected = (focus as? CurrentFocus.Stop)?.selectedRoute
        state[KEY_ROUTE_ORIGIN_HEADSIGN] = selected?.originHeadsign
        state[KEY_ROUTE_LEG_IDS] = selected?.legs?.map(RouteLeg::routeId)?.let { ArrayList(it) }
        state[KEY_ROUTE_LEG_NAMES] = selected?.legs?.map(RouteLeg::shortName)?.let { ArrayList(it) }
        state[KEY_ROUTE_LEG_DIRECTIONS] = selected?.legs
            ?.map { it.directionId ?: NO_DIRECTION }
            ?.toIntArray()
    }

    private fun readLegacyFocus(state: SavedStateHandle, stop: FocusedStop?): CurrentFocus {
        val route = state.get<String>(MapParams.ROUTE_ID)?.let {
            RouteTarget(
                routeId = it,
                directionStopId = state[MapParams.ROUTE_DIRECTION_STOP_ID],
                directionId = state[MapParams.ROUTE_DIRECTION_ID],
            )
        }
        return when {
            stop != null && route != null -> CurrentFocus.Stop(
                stop,
                StopRouteSelection(
                    originHeadsign = null,
                    legs = listOf(RouteLeg(route.routeId, route.routeId, route.directionId)),
                ),
            )
            route != null -> CurrentFocus.Route(route)
            stop != null -> CurrentFocus.Stop(stop)
            state.get<String>(KEY_BIKE_STATION) != null ->
                CurrentFocus.BikeStation(state.get<String>(KEY_BIKE_STATION)!!)
            else -> CurrentFocus.None
        }
    }

    private fun readRouteTarget(state: SavedStateHandle): RouteTarget? {
        val routeId = state.get<String>(KEY_ROUTE_ID) ?: return null
        return RouteTarget(routeId, state[KEY_ROUTE_STOP_ID], state[KEY_ROUTE_DIRECTION_ID])
    }

    private fun readStopRoute(state: SavedStateHandle): StopRouteSelection? {
        val ids = state.get<ArrayList<String>>(KEY_ROUTE_LEG_IDS) ?: return null
        val names = state.get<ArrayList<String>>(KEY_ROUTE_LEG_NAMES).orEmpty()
        val directions = state.get<IntArray>(KEY_ROUTE_LEG_DIRECTIONS) ?: intArrayOf()
        val legs = ids.mapIndexed { index, id ->
            RouteLeg(
                id,
                names.getOrNull(index).orEmpty().ifBlank { id },
                directions.getOrNull(index)?.takeUnless { it == NO_DIRECTION },
            )
        }
        if (legs.isEmpty()) return null
        return StopRouteSelection(
            originHeadsign = state[KEY_ROUTE_ORIGIN_HEADSIGN],
            legs = legs,
        )
    }

    private fun readStop(state: SavedStateHandle): FocusedStop? {
        val id = state.get<String>(KEY_STOP_ID) ?: return null
        return FocusedStop(
            id = id,
            name = state[KEY_STOP_NAME],
            code = state[KEY_STOP_CODE],
            lat = state.get<Double>(KEY_STOP_LAT) ?: 0.0,
            lon = state.get<Double>(KEY_STOP_LON) ?: 0.0,
        )
    }
}
