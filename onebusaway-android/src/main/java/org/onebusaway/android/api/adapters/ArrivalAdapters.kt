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

private class DtoArrivalData(private val d: ArrivalDeparture) : ArrivalData {
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
    override val predictedArrivalTime get() = ServerTime(d.predictedArrivalTime)
    override val scheduledDepartureTime get() = ServerTime(d.scheduledDepartureTime)
    override val predictedDepartureTime get() = ServerTime(d.predictedDepartureTime)
    override val status get() = d.tripStatus?.status?.let { Status.fromString(it) }
    override val frequency
        get() = d.frequency?.let { FrequencyWindow(ServerTime(it.startTime), ServerTime(it.endTime), it.headway) }
    override val historicalOccupancy get() = Occupancy.fromString(d.historicalOccupancy)
    override val predictedOccupancy get() = Occupancy.fromString(d.occupancyStatus)
    override val hasTripStatus get() = d.tripStatus != null
    override val scheduleDeviation get() = d.tripStatus?.scheduleDeviation ?: 0L
    override val lastKnownLat get() = d.tripStatus?.lastKnownLocation?.lat
    override val lastKnownLon get() = d.tripStatus?.lastKnownLocation?.lon
}
