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
package org.onebusaway.android.nav

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.api.data.TripDetailsDataSource
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.util.encodePolyline
import org.onebusaway.android.util.runCatchingCancellable

/**
 * Resolves the route shape for a ride that did not arrive with one.
 *
 * This exists for the legacy trip-details launch, which carries a trip id and two stop ids and no
 * geometry at all. A plan built from a directions itinerary already has each leg's own path and
 * never comes here — which is just as well, because an OTP2 itinerary's trip id is a GTFS id
 * (`1_12345`, `kcm:12345`) with no resolver onto OBA ids, so it would not reliably look up.
 */
internal interface ReminderShapeSource {
    suspend fun shapeFor(tripId: String, penultimateStopId: String, alightStopId: String): ReminderShape?
}

internal class ObaReminderShapeSource @Inject constructor(
    private val tripDetails: TripDetailsDataSource,
    private val observations: TripObservationRepository
) : ReminderShapeSource {

    override suspend fun shapeFor(
        tripId: String,
        penultimateStopId: String,
        alightStopId: String
    ): ReminderShape? = runCatchingCancellable {
        val details = tripDetails.tripDetails(tripId).getOrNull() ?: return@runCatchingCancellable null
        val shapeId = details.trip?.shapeId ?: return@runCatchingCancellable null
        val schedule = details.schedule ?: return@runCatchingCancellable null
        val polyline = observations.ensureShape(tripId, shapeId) ?: return@runCatchingCancellable null

        // The server's own offsets along this very shape — no projection, and no disagreement with
        // whatever the operator considers the stop's position on the route. `distanceAlongTrip` and
        // the polyline's cumulative distances share a metric space by construction (see
        // SphericalGeometry.EARTH_RADIUS_METERS), so the two are directly comparable.
        val offsets = schedule.stopTimes.associate { it.stopId to it.distanceAlongTrip }
        val penultimateOffset = offsets[penultimateStopId] ?: return@runCatchingCancellable null
        val alightOffset = offsets[alightStopId] ?: return@runCatchingCancellable null
        if (alightOffset <= penultimateOffset) {
            Log.w(TAG, "Trip $tripId orders $alightStopId at or before $penultimateStopId; ignoring its shape")
            return@runCatchingCancellable null
        }

        ReminderShape(
            encodedPoints = encodePolyline(polyline.points),
            pointCount = polyline.points.size,
            // The legacy launch contract carries no boarding stop, so there is no offset for one.
            boardOffsetMeters = null,
            penultimateOffsetMeters = penultimateOffset,
            alightOffsetMeters = alightOffset
        )
    }.onFailure { Log.w(TAG, "Could not resolve a shape for trip $tripId", it) }.getOrNull()

    private companion object {
        const val TAG = "ReminderShapeSource"
    }
}
