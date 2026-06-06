/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.map.googlemapsv2.tripmap

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import org.onebusaway.android.R
import org.onebusaway.android.extrapolation.data.ensureShape
import org.onebusaway.android.extrapolation.data.TripStore.lookupTripState
import org.onebusaway.android.io.elements.ObaReferences
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.map.googlemapsv2.MapHelpV2
import org.onebusaway.android.util.Polyline

/** The two overlay layers produced by [TripMapOverlayFactory]. */
data class TripMapOverlays(
        val route: TripRouteOverlay,
        val vehicle: TripVehicleOverlay,
        val shapeData: Polyline,
        val tripId: String
)

/** Reasons [TripMapOverlayFactory.create] may fail to produce overlays. */
internal enum class TripMapOverlayFailure {
    MISSING_SCHEDULE,
    MISSING_REFERENCES,
    TRIP_NOT_IN_REFERENCES,
    MISSING_SHAPE_ID,
    /** Async shape fetch failed (e.g. network or API error). */
    SHAPE_FETCH_FAILED
}

/**
 * Creates and activates trip map overlays from an API response.
 *
 * [create] suspends while the trip shape is fetched via [ensureShape] (the underlying fetcher
 * dedupes in-flight requests) and returns the overlays, or null — after logging a
 * [TripMapOverlayFailure] — when required data is missing or failed to load.
 */
internal object TripMapOverlayFactory {

    private const val TAG = "TripMapOverlayFactory"

    suspend fun create(
            map: GoogleMap,
            context: Context,
            tripId: String,
            selectedStopId: String?,
            response: ObaTripDetailsResponse
    ): TripMapOverlays? {
        val schedule = response.schedule ?: return fail(tripId, TripMapOverlayFailure.MISSING_SCHEDULE)
        val status = response.status
        val refs = response.refs ?: return fail(tripId, TripMapOverlayFailure.MISSING_REFERENCES)
        val trip =
                refs.getTrip(tripId)
                        ?: return fail(tripId, TripMapOverlayFailure.TRIP_NOT_IN_REFERENCES)
        val route = refs.getRoute(trip.routeId)
        val shapeId = trip.shapeId ?: return fail(tripId, TripMapOverlayFailure.MISSING_SHAPE_ID)

        val sd =
                ensureShape(tripId, shapeId)
                        ?: return fail(tripId, TripMapOverlayFailure.SHAPE_FETCH_FAILED)

        val routeColor =
                route?.color ?: ContextCompat.getColor(context, R.color.route_line_color_default)
        val vehiclePosition =
                status?.takeIf { it.activeTripId == tripId }?.position?.let {
                    MapHelpV2.makeLatLng(it)
                }
        val scheduleDeviation = status?.takeIf { it.activeTripId == tripId }?.scheduleDeviation
        val stopNames = buildStopNameMap(schedule, refs)

        val routeOverlay =
                TripRouteOverlay(
                        map,
                        context,
                        tripId,
                        sd,
                        schedule,
                        routeColor,
                        stopNames,
                        selectedStopId,
                        scheduleDeviation
                )
        val vehicleOverlay = TripVehicleOverlay(map, context, sd, routeColor, route?.type)

        routeOverlay.activate()
        lookupTripState(tripId)?.anchor?.let {
            vehicleOverlay.showOrUpdateDataReceivedMarker(it, System.currentTimeMillis())
        }
        vehicleOverlay.activate(vehiclePosition)

        return TripMapOverlays(routeOverlay, vehicleOverlay, sd, tripId)
    }

    private fun fail(tripId: String, reason: TripMapOverlayFailure): TripMapOverlays? {
        Log.w(TAG, "Overlay creation failed for $tripId: $reason")
        return null
    }

    private fun buildStopNameMap(
            schedule: ObaTripSchedule,
            refs: ObaReferences
    ): Map<String, String> =
            schedule.stopTimes
                    ?.mapNotNull { st -> refs.getStop(st.stopId)?.let { st.stopId to it.name } }
                    ?.toMap()
                    ?: emptyMap()
}
