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
package org.onebusaway.android.map.render

import android.content.Context
import androidx.annotation.StringRes
import org.onebusaway.android.R
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.getRouteDisplayName

/**
 * A vehicle marker's title: its route, headsign, and how full it is — "44 - Ballard - Standing room".
 *
 * Since #2194 a vehicle shows no info window, so for vehicles this text is never drawn: it is purely the
 * marker's **accessible name** (the Maps SDK reads a marker's title out as its content description),
 * which is how the crowding the marker draws as silhouettes reaches a rider who can't see them.
 *
 * Shared by both map flavors rather than mirrored in each renderer — it touches no map-SDK type, and the
 * accessible name is exactly the thing that must not quietly drift between flavors (only the `oba`
 * *Google* variant runs in the routine test grid, so a maplibre-side divergence would be invisible).
 */
internal fun vehicleTitle(context: Context, vehicle: VehicleMarker, response: RouteTrips): String {
    val trip = response.trip(vehicle.status.activeTripId) ?: return ""
    val route = response.route(trip.routeId) ?: return ""
    val name = getRouteDisplayName(route) + " - " + MyTextUtils.formatDisplayText(trip.headsign)
    val occupancy = occupancyLabelRes(vehicle) ?: return name
    return name + " - " + context.getString(occupancy)
}

/**
 * What the pips on [vehicle]'s marker say, in words, or null when it draws none.
 *
 * Keyed off the same count [VehicleBitmaps.occupancyPips] draws rather than re-bucketing the raw
 * occupancy, so the words and the silhouettes cannot disagree — including the rule that a vehicle
 * without real-time reports no crowding at all (#959).
 */
@StringRes
private fun occupancyLabelRes(vehicle: VehicleMarker): Int? = when (VehicleBitmaps.occupancyPips(vehicle)) {
    1 -> R.string.realtime_many_seats_available
    2 -> R.string.realtime_standing_room
    VehicleBitmaps.MAX_PIPS -> R.string.realtime_full
    else -> null
}
