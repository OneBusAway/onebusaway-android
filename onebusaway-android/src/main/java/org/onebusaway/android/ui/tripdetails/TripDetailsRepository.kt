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
package org.onebusaway.android.ui.tripdetails

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.elements.ObaTrip
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.elements.Status
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripDetailsResponse
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.ObaRequestErrors

/** A loaded snapshot of a trip's header + ordered stops, ready for the UI. */
data class TripDetailsData(
    val header: TripHeader,
    val stops: List<TripStopItem>,
    val scrollToIndex: Int,
    val routeId: String,
    val lineColorArgb: Int
)

/** Loads a trip's schedule + real-time status and projects it onto the UI model. */
interface TripDetailsRepository {

    /**
     * @param stopId the stop to focus/scroll to (from the launching intent), or null
     * @param scrollMode [TripDetailsLauncher.SCROLL_MODE_VEHICLE]/`_STOP`, or null for no auto-scroll
     * @param destinationId the destination-reminder stop to flag, or null
     */
    suspend fun getTripDetails(
        tripId: String,
        stopId: String?,
        scrollMode: String?,
        destinationId: String?
    ): Result<TripDetailsData>

    /** The last good response, for the host to resolve stops when setting a destination reminder. */
    fun lastResponse(): ObaTripDetailsResponse?
}

/**
 * Default implementation wrapping the blocking trip-details request. Ports
 * TripDetailsListFragment's binding (header status/deviation, per-stop time + color, passed/vehicle
 * markers, scroll target) onto the IO thread, falling back to the last good response on failure.
 * All Android statics (resources, time formatting, color resolution) are quarantined here so
 * [TripDetailsViewModel] stays JVM-testable. Occupancy is deferred (as in the Compose arrivals rows).
 */
class DefaultTripDetailsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : TripDetailsRepository {

    private var lastGood: ObaTripDetailsResponse? = null

    override suspend fun getTripDetails(
        tripId: String,
        stopId: String?,
        scrollMode: String?,
        destinationId: String?
    ): Result<TripDetailsData> = withContext(Dispatchers.IO) {
        val response = ObaTripDetailsRequest.newRequest(context, tripId).call()
        when {
            response.code == ObaApi.OBA_OK -> {
                lastGood = response
                Result.success(toData(response, stopId, scrollMode, destinationId))
            }
            lastGood != null ->
                Result.success(toData(lastGood!!, stopId, scrollMode, destinationId))

            else -> Result.failure(IOException(ObaRequestErrors.getRouteErrorString(context, response.code)))
        }
    }

    override fun lastResponse(): ObaTripDetailsResponse? = lastGood

    private fun toData(
        response: ObaTripDetailsResponse,
        stopId: String?,
        scrollMode: String?,
        destinationId: String?
    ): TripDetailsData {
        val refs = response.refs
        val trip = refs.getTrip(response.id)
        val routeId = trip.routeId
        val route = refs.getRoute(routeId)
        // The API reports deviation/position relative to the trip the vehicle is *currently*
        // serving (status.activeTripId). When this view is a different trip in the same block,
        // that real-time data isn't about this trip — present schedule-only (legacy
        // TripDetailsListFragment behavior).
        val status = response.status?.takeIf { it.activeTripId == response.id }
        val schedule = response.schedule
        val stopTimes = schedule?.stopTimes ?: emptyArray()

        val isRealtime = status != null && status.isPredicted
        val nextStopIndex = status?.let { findIndexForStop(stopTimes, it.nextStop) }
        val stopIndex = findIndexForStop(stopTimes, stopId)
        val destinationIndex = findIndexForStop(stopTimes, destinationId)

        // Time base: real-time service date + deviation, or midnight today for schedule-only.
        val deviation = status?.scheduleDeviation ?: 0L
        val serviceDate = status?.serviceDate ?: midnightToday()
        val canceled = status != null && Status.CANCELED == status.status

        val lastIndex = stopTimes.lastIndex
        val stops = stopTimes.mapIndexed { i, stopTime ->
            val stop = refs.getStop(stopTime.stopId)
            val millis = serviceDate + stopTime.arrivalTime * 1000 + deviation * 1000
            TripStopItem(
                stopId = stopTime.stopId,
                name = MyTextUtils.formatDisplayText(stop.name).orEmpty(),
                direction = stop.direction,
                timeText = DisplayFormat.formatTime(context, millis),
                canceled = canceled,
                isPassed = nextStopIndex != null && i < nextStopIndex,
                linePosition = when (i) {
                    0 -> LinePosition.FIRST
                    lastIndex -> LinePosition.LAST
                    else -> LinePosition.MIDDLE
                },
                isVehicleHere = nextStopIndex != null && i == nextStopIndex - 1,
                pin = when (i) {
                    destinationIndex -> StopPin.DESTINATION
                    stopIndex -> StopPin.FOCUSED
                    else -> StopPin.NONE
                }
            )
        }

        val lineColorArgb = route.color ?: context.getColor(R.color.theme_primary)

        return TripDetailsData(
            header = buildHeader(response, trip, route, status, isRealtime),
            stops = stops,
            scrollToIndex = resolveScrollIndex(scrollMode, stopIndex, destinationIndex, nextStopIndex),
            routeId = routeId,
            lineColorArgb = lineColorArgb
        )
    }

    private fun buildHeader(
        response: ObaTripDetailsResponse,
        trip: ObaTrip,
        route: ObaRoute,
        status: ObaTripStatus?,
        isRealtime: Boolean
    ): TripHeader {
        val deviation = status?.scheduleDeviation ?: 0L
        val statusColor = when {
            status == null || !status.isPredicted -> R.color.stop_info_scheduled_time
            else -> {
                val c = ArrivalInfoUtils.computeColorFromDeviation(TimeUnit.SECONDS.toMinutes(deviation))
                if (c == R.color.stop_info_ontime) R.color.theme_primary else c
            }
        }
        return TripHeader(
            routeShortName = route.shortName.orEmpty(),
            headsign = trip.headsign.orEmpty(),
            tripShortName = trip.shortName?.takeIf { it.isNotBlank() },
            agencyName = response.refs.getAgency(route.agencyId)?.name.orEmpty(),
            vehicleId = status?.vehicleId?.takeIf { it.isNotEmpty() },
            statusText = headerStatusText(status, deviation),
            statusColor = statusColor,
            isRealtime = isRealtime
        )
    }

    private fun headerStatusText(
        status: ObaTripStatus?,
        deviation: Long
    ): String = when {
        status == null -> context.getString(R.string.trip_details_scheduled_data)
        !status.isPredicted ->
            if (Status.CANCELED == status.status) context.getString(R.string.stop_info_canceled)
            else context.getString(R.string.trip_details_scheduled_data)

        else -> {
            val minutes = abs(deviation) / 60
            val seconds = abs(deviation) % 60
            val lastUpdate = DisplayFormat.formatTime(context, status.lastUpdateTime)
            when {
                deviation >= 0 && deviation < 60 ->
                    context.getString(R.string.trip_details_real_time_sec_late, seconds, lastUpdate)
                deviation >= 0 ->
                    context.getString(R.string.trip_details_real_time_min_sec_late, minutes, seconds, lastUpdate)
                deviation > -60 ->
                    context.getString(R.string.trip_details_real_time_sec_early, seconds, lastUpdate)
                else ->
                    context.getString(R.string.trip_details_real_time_min_sec_early, minutes, seconds, lastUpdate)
            }
        }
    }

    /** Ports `setScroller`: vehicle first (then stop), or stop first (then vehicle); destination wins. */
    private fun resolveScrollIndex(
        scrollMode: String?,
        stopIndex: Int?,
        destinationIndex: Int?,
        nextStopIndex: Int?
    ): Int {
        destinationIndex?.let { return it }
        val vehicleIndex = nextStopIndex?.let { it - 1 }
        return when (scrollMode) {
            TripDetailsLauncher.SCROLL_MODE_VEHICLE -> vehicleIndex ?: stopIndex ?: -1
            else -> stopIndex ?: vehicleIndex ?: -1
        }
    }

    private fun findIndexForStop(stopTimes: Array<ObaTripSchedule.StopTime>, stopId: String?): Int? {
        if (stopId == null) return null
        return stopTimes.indexOfFirst { it.stopId == stopId }.takeIf { it >= 0 }
    }

    private fun midnightToday(): Long = GregorianCalendar().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
