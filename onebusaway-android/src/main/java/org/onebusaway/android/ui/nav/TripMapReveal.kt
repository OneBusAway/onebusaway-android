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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.onebusaway.android.map.ShowRouteRequest

/** A trip's map action retains its originating stop and arrivals-row identity. */
@Serializable
data class TripMapReveal(
    val tripId: String,
    val routeId: String,
    val shortName: String,
    val headsign: String?,
    val directionId: Int,
    val stopId: String?,
    val hasVehicle: Boolean
) {
    fun routeRequest() = ShowRouteRequest(
        routeId = routeId,
        directionStopId = stopId,
        focusTripId = tripId.takeIf { hasVehicle },
        initialDirectionId = directionId.takeIf { hasVehicle || stopId != null }
    )
}

const val RESULT_MAP_TRIP = "mapReveal.trip"

/** Keep the trip page beneath the map so Back returns to it. */
fun NavController.showTripOnMap(reveal: TripMapReveal) {
    navigate(NavRoutes.HOME)
    getBackStackEntry(NavRoutes.HOME).savedStateHandle.putTripMapReveal(reveal)
}

internal fun SavedStateHandle.putTripMapReveal(reveal: TripMapReveal) {
    set(RESULT_MAP_TRIP, Json.encodeToString(reveal))
}

fun SavedStateHandle.consumeTripMapReveal(): TripMapReveal? {
    val encoded = get<String>(RESULT_MAP_TRIP) ?: return null
    set(RESULT_MAP_TRIP, null)
    return Json.decodeFromString<TripMapReveal>(encoded)
}
