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

/** Adapts a modernized [ArrivalDeparture] DTO (arrivals fetch) to the [ArrivalData] model. */
internal fun ArrivalDeparture.asArrivalData(): ArrivalData = DtoArrivalData(this)

/**
 * A closed (or otherwise suppressed) stop keeps `predicted:true` but replaces the near-term
 * prediction with a non-positive sentinel — observed `-1` and `0` for stop `1_82673` in issue #1687.
 * Collapse any non-positive predicted instant to the single canonical "no prediction" value `0`
 * here at the wire→domain boundary, so downstream `!= 0` / `> 0` guards all see one sentinel and a
 * `-1` can never be treated as a real epoch-millis timestamp.
 */
private fun predictedOrAbsent(epochMs: Long): Long = if (epochMs > 0L) epochMs else 0L

private class DtoArrivalData(private val d: ArrivalDeparture) : ArrivalData {

    /**
     * The authoritative predicted instant for a stop, in epoch millis, computed at the wire→domain
     * boundary from OBA's canonical relationship `predicted = scheduled + scheduleDeviation` rather
     * than the absolute `predicted{Arrival,Departure}Time` field.
     *
     * The server derives BOTH the absolute predicted times and `tripStatus.scheduleDeviation` from
     * the same deviation, so for healthy data this yields exactly the server's own predicted time (a
     * no-op). But the two can disagree when the absolute field is corrupt: at the WSF "Seattle"
     * terminal (stop `95_7`, issue #1688) the `predictedDepartureTime` for origin-terminal sailings
     * is pinned ~15h stale near the service-day start, while `scheduleDeviation` stays sane (60s /
     * 780s) and the same trip's `predictedArrivalTime` matches `scheduled + scheduleDeviation`
     * exactly. Deriving from the deviation repairs that garbage (~ -900 min ETA).
     *
     * Only applied when the trip is actually tracked (`predicted` AND a `tripStatus` to read the
     * deviation from) AND there is a real scheduled anchor to add the deviation to; otherwise there
     * is no usable real-time source, so fall back to the sentinel-normalized absolute value (issue
     * #1687). The `scheduledEpochMs > 0` guard matters because the wire defaults an absent scheduled
     * time to 0: without it, `0 + deviation` would be a ~epoch-1970 instant that passes the downstream
     * `> 0` prediction gate and reproduces the very garbage ETA this fix removes. [scheduleDeviation]
     * is seconds (+late/−early); scheduled times are epoch millis.
     */
    private fun predictedInstant(scheduledEpochMs: Long, absolutePredictedEpochMs: Long): Long =
        if (d.predicted && d.tripStatus != null && scheduledEpochMs > 0L) {
            scheduledEpochMs + d.tripStatus.scheduleDeviation * MS_IN_SECOND
        } else {
            predictedOrAbsent(absolutePredictedEpochMs)
        }

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
    // Wire→server mint: these are the server clock, already epoch millis.
    override val scheduledArrivalTime get() = ServerTime(d.scheduledArrivalTime)
    override val predictedArrivalTime
        get() = ServerTime(predictedInstant(d.scheduledArrivalTime, d.predictedArrivalTime))
    override val scheduledDepartureTime get() = ServerTime(d.scheduledDepartureTime)
    override val predictedDepartureTime
        get() = ServerTime(predictedInstant(d.scheduledDepartureTime, d.predictedDepartureTime))
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

    private companion object {
        private const val MS_IN_SECOND = 1000L
    }
}
