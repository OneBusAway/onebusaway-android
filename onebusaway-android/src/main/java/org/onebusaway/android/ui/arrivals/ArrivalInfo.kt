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
import androidx.annotation.ColorRes
import java.text.DateFormat
import java.util.Date
import kotlin.time.Duration
import org.onebusaway.android.R
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.report.TripReportContext
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.ScheduleDeviation
import org.onebusaway.android.util.getRouteDisplayName

/**
 * @param includeArrivalDepartureInStatusLabel true if the arrival/departure label should be
 *                                             included in the status label false if it should not
 */
class ArrivalInfo(
    context: Context?,
    private val data: ArrivalData,
    now: ServerTime,
    includeArrivalDepartureInStatusLabel: Boolean
) : RouteDirectionItem {

    override val eta: Long

    val displayTime: ServerTime

    val statusText: String

    val timeText: String

    val notifyText: String

    /**
     * The schedule-deviation color for the arrival time, as a color resource id. This is the
     * **foreground** tier — use it where the color is the text itself.
     */
    @get:ColorRes
    val color: Int

    /**
     * The same schedule-deviation state as [color], in the **on-fill** tier: darkened so white text
     * drawn on top of it clears WCAG AA. Use it wherever the color becomes a filled surface — the
     * ETA pills and the starred-stop badges — rather than the text.
     */
    @get:ColorRes
    val fillColor: Int

    /**
     * True if there is real-time arrival info available for this trip, false if there is not.
     */
    val predicted: Boolean

    /**
     * True if this arrival info is for an arrival time, false if it is for a departure time.
     */
    val isArrival: Boolean

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
    override val routeId: String get() = data.routeId
    val tripId: String get() = data.tripId
    override val directionId: Int? get() = data.directionId
    val stopId: String get() = data.stopId
    override val headsign: String? get() = data.headsign
    val shortName: String? get() = data.shortName
    val routeLongName: String? get() = data.routeLongName

    /**
     * True when a live vehicle is actively serving this trip and has a plottable position right now
     * (#1992): the ETA pill shows the "on the map" pin instead of the rss glyph, since tapping it would
     * reframe the map to that vehicle. Implies [predicted] (a pin is always real-time), so the pin is a
     * strict refinement of the rss "AVL-tracked" cue rather than a separate state. Derived from the
     * arrival's own trip status, so it holds before the route is ever shown on the map — the row doesn't
     * need to be selected first.
     */
    val vehicleOnMap: Boolean get() = predicted && data.hasPlottableVehicle

    /** The route-row sort/display name (#1822): short name falling back to the long name (the same
     *  [getRouteDisplayName] fallback used elsewhere for this route), then the route id — never
     *  blank, so it's always a valid [RouteRowGroup] sort key. */
    override val lineName: String get() = getRouteDisplayName(shortName, routeLongName).ifEmpty { routeId }
    val stopSequence: Int get() = data.stopSequence
    val serviceDate: Long get() = data.serviceDate
    val vehicleId: String? get() = data.vehicleId

    /** The service-alert (situation) ids that reference this specific arrival; empty when none. */
    val situationIds: List<String> get() = data.situationIds

    /** The departure time to set a reminder for: the predicted time if known, else the scheduled. */
    val reminderDepartureTime: ServerTime
        get() = data.predictedDepartureTime ?: data.scheduledDepartureTime

    /**
     * The server clock this arrival's ETA was computed against (the response's `currentTime`). Carried
     * to the reminder editor so its lead-time filter measures [reminderDepartureTime] against the same
     * clock that stamped it, keeping device clock skew out of the offsets (#1612). Kept typed; unwrapped
     * to a bare `Long` only at the nav-route serialization boundary.
     */
    val serverNow: ServerTime = now

    /**
     * [eta] against an arbitrary [now] instead of the poll-time snapshot — [eta] itself is just
     * `liveEta(now)` at construction time. A live countdown (issue #1781) calls this with a ticking
     * [now], counting down between polls until a fresh poll (a brand-new [ArrivalInfo], with a fresh
     * [serverNow]) supersedes it.
     */
    fun liveEta(now: ServerTime): Long = displayTime.epochMs / MS_IN_MINS - now.epochMs / MS_IN_MINS

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
        // The normalized flag (data.predicted AND a positive predicted instant), not the raw
        // server flag: at a closed stop the server keeps predicted:true but suppresses the
        // instants to 0 (issue #1687), and the report builder branches on this to pick the
        // predicted vs scheduled times — the raw flag would format Date(0) as a 1969 garbage time.
        predicted = predicted,
        predictedArrivalTime = data.predictedArrivalTime?.epochMs,
        predictedDepartureTime = data.predictedDepartureTime?.epochMs,
        scheduledArrivalTime = data.scheduledArrivalTime.epochMs,
        scheduledDepartureTime = data.scheduledDepartureTime.epochMs,
        hasTripStatus = data.hasTripStatus,
        scheduleDeviation = data.scheduleDeviation,
        lastKnownLat = data.lastKnownLat,
        lastKnownLon = data.lastKnownLon
    )

    init {
        // First, all times have to be converted to 'minutes'. now/scheduled/predicted are all
        // server-clock ServerTime — the ETA baseline can't be a device clock (#1620). The minute values
        // are floored per-instant then subtracted (unchanged behavior); the typed instants guarantee
        // the subtraction stays same-domain.
        val scheduled: ServerTime
        val predictedTime: ServerTime?
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

        // A prediction is only usable when the server actually gave us one. A closed stop keeps
        // `predicted:true` but suppresses the near-term prediction (decoded to a null instant at the
        // wire→domain boundary, issue #1687); keying the ETA off the boolean alone would subtract a 0
        // timestamp from "now" and render garbage (~ -29,718,596 min).
        val hasPrediction = data.predicted && predictedTime != null

        if (hasPrediction) {
            predicted = true
            displayTime = predictedTime
        } else {
            predicted = false
            displayTime = scheduled
        }
        // eta is liveEta at the constructor's own `now` — reusing it here (instead of re-deriving the
        // displayTime-minus-now subtraction inline) is what keeps a later ticking countdown (issue
        // #1781) exactly consistent with this poll-time value at t=0.
        eta = liveEta(now)

        // The deviation stays at full precision: a same-domain ServerTime subtraction, so no device
        // clock can leak in (#1620). Flooring each instant to whole minutes first — what this did
        // before #2043 — made "on time" mean the two minute-past-epoch values were exactly equal, so a
        // bus 20 s late that happened to straddle a minute boundary rendered late and the green
        // on-time state was nearly never shown. The color and the status label both bucket on the one
        // [ScheduleDeviation.status] call below, so they can never contradict each other.
        val deviation = predictedTime?.let { it - scheduled } ?: Duration.ZERO
        val deviationStatus = ScheduleDeviation.status(hasPrediction, deviation)
        color = deviationStatus.colorRes
        fillColor = deviationStatus.fillColorRes

        statusText = computeStatusLabel(
            context,
            now,
            hasPrediction,
            deviationStatus,
            ScheduleDeviation.roundedMinutes(deviation.absoluteValue),
            includeArrivalDepartureInStatusLabel
        )
        timeText = computeTimeLabel(context)

        notifyText = computeNotifyText(context)

        historicalOccupancy = data.historicalOccupancy
        predictedOccupancy = data.predictedOccupancy
        status = data.status
    }

    /**
     * @param includeArrivalDeparture true if the arrival/departure label should be included, false
     *                                if it should not
     */

    /**
     * [deviationStatus] and [deviationMinutes] come from the same [ScheduleDeviation.status] call that
     * picked [color], so the words and the hue always agree: [deviationMinutes] is the unsigned
     * magnitude, read only when the status is early or delayed.
     */
    private fun computeStatusLabel(
        context: Context?,
        now: ServerTime,
        hasPrediction: Boolean,
        deviationStatus: ScheduleDeviation.Status,
        deviationMinutes: Long,
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
            val time: ServerTime

            if (now < frequency.startTime) {
                statusLabelId = R.plurals.stop_info_frequency_from
                time = frequency.startTime
            } else {
                statusLabelId = R.plurals.stop_info_frequency_until
                time = frequency.endTime
            }

            val label = formatter.format(Date(time.epochMs))
            return res.getQuantityString(statusLabelId, headwayAsMinutes, headwayAsMinutes, label)
        }

        if (hasPrediction) {
            val minutes = deviationMinutes.toInt()

            if (eta >= 0) {
                // Bus hasn't yet arrived/departed
                return ArrivalInfoUtils.computeArrivalLabel(res, deviationStatus, deviationMinutes)
            }

            // Arrival/departure time has passed
            if (!includeArrivalDeparture) {
                // Don't include "depart" or "arrive" in label
                return when (deviationStatus) {
                    ScheduleDeviation.Status.DELAYED -> res.getQuantityString(
                        R.plurals.stop_info_status_late_without_arrive_depart,
                        minutes,
                        deviationMinutes
                    )
                    ScheduleDeviation.Status.EARLY -> res.getQuantityString(
                        R.plurals.stop_info_status_early_without_arrive_depart,
                        minutes,
                        deviationMinutes
                    )
                    else -> context.getString(R.string.stop_info_ontime)
                }
            }

            return if (isArrival) {
                // Is an arrival time
                when (deviationStatus) {
                    ScheduleDeviation.Status.DELAYED ->
                        res.getQuantityString(R.plurals.stop_info_arrived_delayed, minutes, deviationMinutes)
                    ScheduleDeviation.Status.EARLY ->
                        res.getQuantityString(R.plurals.stop_info_arrived_early, minutes, deviationMinutes)
                    else -> context.getString(R.string.stop_info_arrived_ontime)
                }
            } else {
                // Is a departure time
                when (deviationStatus) {
                    ScheduleDeviation.Status.DELAYED ->
                        res.getQuantityString(R.plurals.stop_info_depart_delayed, minutes, deviationMinutes)
                    ScheduleDeviation.Status.EARLY ->
                        res.getQuantityString(R.plurals.stop_info_depart_early, minutes, deviationMinutes)
                    else -> context.getString(R.string.stop_info_departed_ontime)
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

        val displayTimeText = DisplayFormat.formatTime(context, displayTime.epochMs)

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
