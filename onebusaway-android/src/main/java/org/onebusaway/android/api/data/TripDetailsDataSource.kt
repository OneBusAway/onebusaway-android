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
package org.onebusaway.android.api.data

import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.adapters.toObaTripSchedule
import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.adapters.DtoTrip
import org.onebusaway.android.api.adapters.DtoTripStatus
import org.onebusaway.android.api.requireData

import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.contract.TripDetailsEntry

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.ObaTripStatus

/**
 * A resolved snapshot of one trip's details: the [trip]/[route]/[status]/[schedule] plus a stop +
 * agency resolver, all exposed as the `models` interfaces so the trip-details feature never touches
 * a wire DTO. The wire envelope stays private to this class.
 */
class TripDetails internal constructor(
    private val data: EntryWithReferences<TripDetailsEntry>,
    /** The server `currentTime` of this response (epoch millis). */
    val currentTime: Long,
) {
    private val refs get() = data.references
    private val entry get() = data.entry

    val tripId: String get() = entry.tripId

    /** This trip, resolved from the references pool, or null when absent. */
    val trip: ObaTrip? get() = refs.trip(entry.tripId)?.let(::DtoTrip)

    /** The trip's route, resolved from the references pool, or null when absent. */
    val route: ObaRoute? get() = trip?.routeId?.let { refs.route(it) }?.let(::DtoRoute)

    /**
     * The vehicle status, but only when it's actually serving THIS trip (`activeTripId == tripId`).
     * The API reports deviation/position for the trip the vehicle is currently on; when this view is
     * a different trip in the same block, that real-time data isn't about this trip — so null here
     * yields a schedule-only presentation (legacy behavior).
     */
    val status: ObaTripStatus?
        get() = entry.status?.takeIf { it.activeTripId == entry.tripId }?.let(::DtoTripStatus)

    /** The trip's schedule (ordered stop times), or null when absent. */
    val schedule: ObaTripSchedule? get() = entry.schedule?.toObaTripSchedule()

    /** Resolves a stop from the references pool by id, or null when absent. */
    fun stop(id: String): ObaStop? = refs.stop(id)?.let(::DtoStop)

    /** Resolves an agency name from the references pool by id, or null. */
    fun agencyName(id: String): String? = refs.agency(id)?.name
}

/** Fetches a single trip's details and resolves it to the [TripDetails] model. */
interface TripDetailsDataSource {

    /** trip-details for [tripId]. [Result.failure] (IO / HTTP / non-OK code) rather than throwing. */
    suspend fun tripDetails(tripId: String): Result<TripDetails>
}

class DefaultTripDetailsDataSource @Inject constructor(
    private val api: ObaApiProvider,
) : TripDetailsDataSource {

    override suspend fun tripDetails(tripId: String): Result<TripDetails> = api.call {
        val envelope = it.tripDetails(tripId)
        TripDetails(envelope.requireData(), envelope.currentTime)
    }.onFailure { Log.e(TAG, "tripDetails($tripId) failed", it) }

    private companion object {
        const val TAG = "TripDetailsDataSource"
    }
}
