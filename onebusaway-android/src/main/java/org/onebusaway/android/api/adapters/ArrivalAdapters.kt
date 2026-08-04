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
package org.onebusaway.android.api.adapters

import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime

/**
 * Adapts a modernized [ArrivalDeparture] DTO (arrivals fetch) to the [ArrivalData] model, or drops an
 * unrecoverable frequency row whose scheduled and predicted times were all replaced by the server's
 * no-prediction sentinel.
 */
internal fun ArrivalDeparture.asArrivalData(directionId: Int?): ArrivalData? = takeUnless { hasCollapsedFrequencySentinel }?.let { DtoArrivalData(it, directionId) }

/**
 * For a frequency instance, the server's predicted-time setters also overwrite the corresponding
 * scheduled times. When both timepoint predictions are absent, that destroys the original scheduled
 * dwell and leaves all four timestamps equal to either the bare sentinel (`-1` / `0`) or its
 * `slack * 1000 - 1` spelling. The positive spelling always ends in `999`, because schedule slack is
 * whole seconds. With neither a prediction nor a schedule left, there is no honest time to display;
 * discard that row instead of inventing one from the frequency window.
 *
 * Requiring a frequency row and all four timestamps to agree keeps this tied to the server path that
 * collapses the schedule. For the positive spelling, the arithmetic signature (`999` milliseconds)
 * is not sufficient by itself because a real epoch can have that suffix; the candidate must also
 * predate the frequency service window. We deliberately do not impose an upper bound because visits
 * at downstream stops can occur after the trip-origin frequency window ends.
 */
private val ArrivalDeparture.hasCollapsedFrequencySentinel: Boolean
    get() {
        val frequencyStartTime = frequency?.startTime ?: return false
        val candidate = predictedArrivalTime
        return predictedDepartureTime == candidate &&
            scheduledArrivalTime == candidate &&
            scheduledDepartureTime == candidate &&
            (candidate <= 0L || (candidate < frequencyStartTime && candidate % 1_000L == 999L))
    }

/**
 * Decodes a wire `predicted{Arrival,Departure}Time` into "the instant" or **`null`** ("no prediction"),
 * so the domain model carries absence as a nullable [ServerTime] rather than an epoch a consumer could
 * mistake for a real timestamp (parse, don't validate). Two spellings of absence reach us, and both are
 * the *same* server sentinel — OBA's `TimepointPredictionRecord` defaults both predicted fields to
 * **`-1`** for "the feed gave no prediction for this stop":
 *
 *  - **`-1` / `0` verbatim.** A closed or otherwise suppressed stop keeps `predicted:true` and sends
 *    these — observed for stop `1_82673` in issue #1687.
 *
 *  - **`-1` with the stop's slack time added to it**, which is a positive number and so sailed straight
 *    past the non-positive check. In `ArrivalAndDepartureServiceImpl` the server does
 *    `if (departureTime <= 0) departureTime = arrivalTime + slack * 1000` — arithmetic *on the
 *    sentinel* — then, because `arrivalTime == -1`, copies that result onto the arrival too. So both
 *    fields come across as exactly `slack * 1000 - 1`. Live example: a 1 Line platform with a 30 s
 *    scheduled dwell sent `29999` for both, which as an epoch is half a minute past 1970 and rendered
 *    as an ETA of **-496029 hours** (issue #2144; filed upstream as
 *    onebusaway-application-modules#474).
 *
 * [scheduledDwellMs] is what makes the second case decodable *exactly*, with no threshold and no
 * guess about how small a number is "too small": the server's slack is
 * `StopTimeEntryImpl.getSlackTime()`, defined as that stop time's own `departureTime - arrivalTime`,
 * which is precisely this arrival's scheduled dwell — a value already on the wire. So we reconstruct
 * the artifact the server would have produced and compare for equality. It cannot collide with a real
 * prediction: a dwell in milliseconds is ~1e4, while a genuine predicted instant is ~1.8e12.
 *
 * A stop with no scheduled dwell reduces to the first case (`0 - 1 == -1`), which is why this bug hid
 * for so long — it only escapes the non-positive check at stops that dwell, and most don't.
 */
// `internal`, not `private`: a same-file class getter reads it, and a private top-level function would
// force a synthetic accessor (SyntheticAccessor lint) — internal is accessed directly.
internal fun predictedServerTimeOrNull(epochMs: Long, scheduledDwellMs: Long): ServerTime? = epochMs
    .takeIf { it > 0L && it != scheduledDwellMs - 1L }
    ?.let { ServerTime(it) }

private class DtoArrivalData(
    private val d: ArrivalDeparture,
    override val directionId: Int?
) : ArrivalData {
    override val routeId get() = d.routeId
    override val tripId get() = d.tripId
    override val stopId get() = d.stopId
    override val headsign get() = d.tripHeadsign
    override val shortName get() = d.routeShortName
    override val routeLongName get() = d.routeLongName
    override val stopSequence get() = d.stopSequence
    override val serviceDate get() = d.serviceDate
    override val vehicleId get() = d.vehicleId
    override val predicted get() = d.predicted

    /** This stop time's own scheduled dwell — the server's `slackTime`, which is what it adds to the
     *  no-prediction sentinel; see [predictedServerTimeOrNull]. */
    private val scheduledDwellMs get() = d.scheduledDepartureTime - d.scheduledArrivalTime

    // Wire→server mint: these are the server clock, already epoch millis.
    override val scheduledArrivalTime get() = ServerTime(d.scheduledArrivalTime)
    override val predictedArrivalTime get() = predictedServerTimeOrNull(d.predictedArrivalTime, scheduledDwellMs)
    override val scheduledDepartureTime get() = ServerTime(d.scheduledDepartureTime)
    override val predictedDepartureTime get() = predictedServerTimeOrNull(d.predictedDepartureTime, scheduledDwellMs)
    override val status get() = d.tripStatus?.status?.let { Status.fromString(it) }
    override val situationIds get() = d.situationIds
    override val frequency
        get() = d.frequency?.let { FrequencyWindow(ServerTime(it.startTime), ServerTime(it.endTime), it.headway) }
    override val historicalOccupancy get() = Occupancy.fromString(d.historicalOccupancy)
    override val predictedOccupancy get() = Occupancy.fromString(d.occupancyStatus)
    override val hasTripStatus get() = d.tripStatus != null
    override val scheduleDeviation get() = d.tripStatus?.scheduleDeviation ?: 0L
    override val lastKnownLat get() = d.tripStatus?.lastKnownLocation?.lat
    override val lastKnownLon get() = d.tripStatus?.lastKnownLocation?.lon

    // Drawable only when the vehicle is actively on THIS trip (activeTripId == this arrival's tripId) and
    // has a location — the same source (lastKnownLocation ?: position) the map turns into a marker keyed
    // by activeTripId (see TripExtrapolationBuilder.extrapolatedVehicles). If the block's vehicle is still
    // upstream on an earlier trip, its activeTripId differs, the map draws no marker for this trip, and a
    // pill tap wouldn't reframe — so this is correctly false there (#1992).
    override val hasPlottableVehicle
        get() = d.tripStatus?.let { ts ->
            ts.activeTripId == d.tripId && (ts.lastKnownLocation != null || ts.position != null)
        } ?: false
}
