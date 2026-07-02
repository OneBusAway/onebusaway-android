/*
 * Copyright (C) 2010-2016 Paul Watts (paulcwatts@gmail.com)
 * University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.ui.arrivals

import android.content.Context
import org.onebusaway.android.R
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.report.TripReportContext
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.getRouteDisplayName
import java.text.DateFormat
import java.util.Date

/**
 * @param includeArrivalDepartureInStatusLabel true if the arrival/departure label should be
 *                                             included in the status label false if it should not
 */
class ArrivalInfo(
    context: Context?,
    private val data: ArrivalData,
    now: Long,
    includeArrivalDepartureInStatusLabel: Boolean,
    favorite: Boolean,
) {

    val eta: Long

    val displayTime: Long

    val statusText: String

    val timeText: String

    val notifyText: String

    /**
     * The resource code for the color that should be used for the arrival time.
     */
    val color: Int

    /**
     * True if there is real-time arrival info available for this trip, false if there is not.
     */
    val predicted: Boolean

    /**
     * True if this arrival info is for an arrival time, false if it is for a departure time.
     */
    val isArrival: Boolean

    /**
     * True if this route is a user-designated favorite, false if it is not.
     */
    val isRouteAndHeadsignFavorite: Boolean

    /**
     * The average historical occupancy of the vehicle when it arrives at this stop, or null if the
     * occupancy is unknown.
     */
    val historicalOccupancy: Occupancy?

    /**
     * The predicted occupancy of the vehicle when it arrives at this stop, or null if the occupancy
     * is unknown.
     */
    val predictedOccupancy: Occupancy?

    /**
     * The status of the trip, or null if the trip status doesn't exist.
     */
    val status: Status?

    // Identity/display fields the UI and repository read directly, delegating to the [ArrivalData]
    // abstraction — so the same display model serves both the legacy fetch (My Lists) and the
    // modernized arrivals fetch, depending on which adapter produced the [data].
    val routeId: String get() = data.routeId
    val tripId: String get() = data.tripId
    val stopId: String get() = data.stopId
    val headsign: String? get() = data.headsign
    val shortName: String? get() = data.shortName
    val routeLongName: String? get() = data.routeLongName
    val stopSequence: Int get() = data.stopSequence
    val serviceDate: Long get() = data.serviceDate
    val vehicleId: String? get() = data.vehicleId

    /** The departure time to set a reminder for: the predicted time if known, else the scheduled. */
    val reminderDepartureTime: Long
        get() = if (data.predictedDepartureTime != 0L) data.predictedDepartureTime
        else data.scheduledDepartureTime

    /** Flattens this arrival into the report flow's scalar context (was ObaArrivalInfo-based). */
    fun toTripReportContext(): TripReportContext = TripReportContext(
        tripId = data.tripId,
        routeId = data.routeId,
        shortName = data.shortName,
        routeLongName = data.routeLongName,
        headsign = data.headsign,
        vehicleId = data.vehicleId,
        stopId = data.stopId,
        serviceDate = data.serviceDate,
        predicted = data.predicted,
        predictedArrivalTime = data.predictedArrivalTime,
        predictedDepartureTime = data.predictedDepartureTime,
        scheduledArrivalTime = data.scheduledArrivalTime,
        scheduledDepartureTime = data.scheduledDepartureTime,
        hasTripStatus = data.hasTripStatus,
        scheduleDeviation = data.scheduleDeviation,
        lastKnownLat = data.lastKnownLat,
        lastKnownLon = data.lastKnownLon,
    )

    init {
        // First, all times have to be converted to 'minutes'
        val nowMins = now / MS_IN_MINS
        val scheduled: Long
        val predictedTime: Long
        // If this is the first stop in the sequence, show the departure time.
        if (data.stopSequence != 0) {
            scheduled = data.scheduledArrivalTime
            predictedTime = data.predictedArrivalTime
            isArrival = true
        } else {
            // Show departure time
            scheduled = data.scheduledDepartureTime
            predictedTime = data.predictedDepartureTime
            isArrival = false
        }

        val scheduledMins = scheduled / MS_IN_MINS
        val predictedMins = predictedTime / MS_IN_MINS

        if (data.predicted) {
            predicted = true
            eta = predictedMins - nowMins
            displayTime = predictedTime
        } else {
            predicted = false
            eta = scheduledMins - nowMins
            displayTime = scheduled
        }

        color = ArrivalInfoUtils.computeColor(scheduledMins, predictedMins)

        statusText = computeStatusLabel(
            context, now, predictedTime, scheduledMins, predictedMins,
            includeArrivalDepartureInStatusLabel
        )
        timeText = computeTimeLabel(context)

        // Whether the user marked this routeId/headsign/stopId a favorite (precomputed by the caller
        // from one favorites query, replacing the legacy per-row ContentProvider lookup).
        isRouteAndHeadsignFavorite = favorite

        notifyText = computeNotifyText(context)

        historicalOccupancy = data.historicalOccupancy
        predictedOccupancy = data.predictedOccupancy
        status = data.status
    }

    /**
     * @param includeArrivalDeparture true if the arrival/departure label should be included, false
     *                                if it should not
     */
    private fun computeStatusLabel(
        context: Context?,
        now: Long,
        predictedTime: Long,
        scheduledMins: Long,
        predictedMins: Long,
        includeArrivalDeparture: Boolean
    ): String {
        if (context == null) {
            // The Activity has been destroyed, so just return an empty string to avoid an NPE
            return ""
        }

        val res = context.resources

        // CANCELED trips
        if (Status.CANCELED == data.status) {
            if (!includeArrivalDeparture) {
                return context.getString(R.string.stop_info_canceled)
            }
            return if (isArrival) {
                context.getString(R.string.stop_info_canceled_arrival)
            } else {
                context.getString(R.string.stop_info_canceled_departure)
            }
        }

        // Frequency (exact_times=0) trips
        val frequency = data.frequency
        if (frequency != null) {
            val headwayAsMinutes = (frequency.headway / 60).toInt()
            val formatter = DateFormat.getTimeInstance(DateFormat.SHORT)

            val statusLabelId: Int
            val time: Long

            if (now < frequency.startTime) {
                statusLabelId = R.string.stop_info_frequency_from
                time = frequency.startTime
            } else {
                statusLabelId = R.string.stop_info_frequency_until
                time = frequency.endTime
            }

            val label = formatter.format(Date(time))
            return context.getString(statusLabelId, headwayAsMinutes, label)
        }

        if (predictedTime != 0L) {
            // Real-time info
            var delay = predictedMins - scheduledMins

            if (eta >= 0) {
                // Bus hasn't yet arrived/departed
                return ArrivalInfoUtils.computeArrivalLabelFromDelay(res, delay)
            }

            // Arrival/departure time has passed
            if (!includeArrivalDeparture) {
                // Don't include "depart" or "arrive" in label
                return if (delay > 0) {
                    // Delayed
                    res.getQuantityString(
                        R.plurals.stop_info_status_late_without_arrive_depart, delay.toInt(), delay
                    )
                } else if (delay < 0) {
                    // Early
                    delay = -delay
                    res.getQuantityString(
                        R.plurals.stop_info_status_early_without_arrive_depart, delay.toInt(), delay
                    )
                } else {
                    // On time
                    context.getString(R.string.stop_info_ontime)
                }
            }

            return if (isArrival) {
                // Is an arrival time
                if (delay > 0) {
                    // Arrived late
                    res.getQuantityString(R.plurals.stop_info_arrived_delayed, delay.toInt(), delay)
                } else if (delay < 0) {
                    // Arrived early
                    delay = -delay
                    res.getQuantityString(R.plurals.stop_info_arrived_early, delay.toInt(), delay)
                } else {
                    // Arrived on time
                    context.getString(R.string.stop_info_arrived_ontime)
                }
            } else {
                // Is a departure time
                if (delay > 0) {
                    // Departed late
                    res.getQuantityString(R.plurals.stop_info_depart_delayed, delay.toInt(), delay)
                } else if (delay < 0) {
                    // Departed early
                    delay = -delay
                    res.getQuantityString(R.plurals.stop_info_depart_early, delay.toInt(), delay)
                } else {
                    // Departed on time
                    context.getString(R.string.stop_info_departed_ontime)
                }
            }
        } else {
            // Scheduled times
            if (!includeArrivalDeparture) {
                return context.getString(R.string.stop_info_scheduled)
            }
            return if (isArrival) {
                context.getString(R.string.stop_info_scheduled_arrival)
            } else {
                context.getString(R.string.stop_info_scheduled_departure)
            }
        }
    }

    private fun computeTimeLabel(context: Context?): String {
        if (context == null) {
            // The Activity has been destroyed, so just return an empty string to avoid an NPE
            return ""
        }

        val displayTimeText = DisplayFormat.formatTime(context, displayTime)

        return if (eta >= 0) {
            // Bus hasn't yet arrived
            if (isArrival) {
                context.getString(R.string.stop_info_time_arriving_at, displayTimeText)
            } else {
                context.getString(R.string.stop_info_time_departing_at, displayTimeText)
            }
        } else {
            // Arrival/departure time has passed
            if (isArrival) {
                context.getString(R.string.stop_info_time_arrived_at, displayTimeText)
            } else {
                context.getString(R.string.stop_info_time_departed_at, displayTimeText)
            }
        }
    }

    private fun computeNotifyText(context: Context?): String {
        if (context == null) {
            // The Activity has been destroyed, so just return an empty string to avoid an NPE
            return ""
        }

        val routeDisplayName = getRouteDisplayName(data.shortName, data.routeLongName)

        return if (eta > 0) {
            // Bus hasn't yet arrived/departed
            if (isArrival) {
                context.getString(R.string.trip_stat_arriving, routeDisplayName, eta.toInt())
            } else {
                context.getString(R.string.trip_stat_departing, routeDisplayName, eta.toInt())
            }
        } else if (eta < 0) {
            // Bus arrived or departed
            if (isArrival) {
                context.getString(R.string.trip_stat_gone_arrived, routeDisplayName)
            } else {
                context.getString(R.string.trip_stat_gone_departed, routeDisplayName)
            }
        } else {
            // Bus is arriving/departing now
            if (isArrival) {
                context.getString(R.string.trip_stat_lessthanone_arriving, routeDisplayName)
            } else {
                context.getString(R.string.trip_stat_lessthanone_departing, routeDisplayName)
            }
        }
    }

    companion object {
        private const val MS_IN_MINS = 60 * 1000
    }
}
