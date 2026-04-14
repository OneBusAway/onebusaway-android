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
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import org.onebusaway.android.R
import org.onebusaway.android.extrapolation.data.TripDataManager
import org.onebusaway.android.io.elements.ObaReferences
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
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
 * Calls [onReady] on the main thread once overlays are created, or [onError] with a
 * [TripMapOverlayFailure] describing which required data was missing or failed to load.
 */
internal object TripMapOverlayFactory {

    fun create(
            map: GoogleMap,
            context: Context,
            tripId: String,
            selectedStopId: String?,
            response: ObaTripDetailsResponse,
            onReady: (TripMapOverlays) -> Unit,
            onError: (TripMapOverlayFailure) -> Unit
    ) {
        val schedule =
                response.schedule
                        ?: run {
                            onError(TripMapOverlayFailure.MISSING_SCHEDULE)
                            return
                        }
        val status = response.status
        val refs =
                response.refs
                        ?: run {
                            onError(TripMapOverlayFailure.MISSING_REFERENCES)
                            return
                        }
        val trip =
                refs.getTrip(tripId)
                        ?: run {
                            onError(TripMapOverlayFailure.TRIP_NOT_IN_REFERENCES)
                            return
                        }
        val route = refs.getRoute(trip.routeId)

        cacheResponseData(tripId, schedule, status)

        val routeColor =
                route?.color ?: ContextCompat.getColor(context, R.color.route_line_color_default)
        val vehiclePosition =
                status?.takeIf { it.activeTripId == tripId }?.position?.let {
                    MapHelpV2.makeLatLng(it)
                }
        val scheduleDeviation = status?.takeIf { it.activeTripId == tripId }?.scheduleDeviation
        val stopNames = buildStopNameMap(schedule, refs)

        fun build(sd: Polyline) {
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
            TripDataManager.getAnchor(tripId)?.let {
                vehicleOverlay.showOrUpdateDataReceivedMarker(it, System.currentTimeMillis())
            }
            vehicleOverlay.activate(vehiclePosition)

            onReady(TripMapOverlays(routeOverlay, vehicleOverlay, sd, tripId))
        }

        val shapeId = trip.shapeId
        if (shapeId != null) {
            TripDataManager.ensureShape(tripId, shapeId, ::build) {
                onError(TripMapOverlayFailure.SHAPE_FETCH_FAILED)
            }
        } else {
            onError(TripMapOverlayFailure.MISSING_SHAPE_ID)
        }
    }

    private fun cacheResponseData(
            tripId: String,
            schedule: ObaTripSchedule,
            status: ObaTripStatus?
    ) {
        TripDataManager.putSchedule(tripId, schedule)
        if (status != null && status.serviceDate > 0) {
            TripDataManager.putServiceDate(tripId, status.serviceDate)
        }
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
