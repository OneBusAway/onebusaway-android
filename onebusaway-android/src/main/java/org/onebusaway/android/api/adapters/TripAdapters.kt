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

import android.location.Location
import org.onebusaway.android.api.contract.Position
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.contract.TripReference
import org.onebusaway.android.api.contract.TripSchedule
import org.onebusaway.android.api.contract.TripStatus
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripDetails
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.util.LocationUtils

/*
 * Adapters that present the io/client trip DTOs as the `models` domain interfaces
 * (ObaTripStatus/ObaTrip/ObaTripDetails), so the speed-estimation/vehicle-render code — which works
 * through those interfaces — consumes the modernized fetch unchanged. The same
 * one-DTO-implements-the-interface pattern the map boundary uses for stops/routes.
 */

private fun Position.toLocation(): Location = LocationUtils.makeLocation(lat, lon)

/** Presents a [TripStatus] DTO as an [ObaTripStatus]. */
internal class DtoTripStatus(private val dto: TripStatus) : ObaTripStatus {
    override val serviceDate: Long get() = dto.serviceDate
    override val isPredicted: Boolean get() = dto.predicted
    override val scheduleDeviation: Long get() = dto.scheduleDeviation
    override val vehicleId: String? get() = dto.vehicleId
    override val closestStop: String? get() = dto.closestStop
    override val closestStopTimeOffset: Long get() = dto.closestStopTimeOffset
    override val position: Location? get() = dto.position?.toLocation()
    // Absent active-trip id reads as null, like the legacy element (callers skip on it).
    override val activeTripId: String? get() = dto.activeTripId.ifBlank { null }
    override val distanceAlongTrip: Double? get() = dto.distanceAlongTrip
    override val scheduledDistanceAlongTrip: Double? get() = dto.scheduledDistanceAlongTrip
    override val totalDistanceAlongTrip: Double? get() = dto.totalDistanceAlongTrip
    override val orientation: Double? get() = dto.orientation
    override val nextStop: String? get() = dto.nextStop
    override val nextStopTimeOffset: Long? get() = dto.nextStopTimeOffset
    override val phase: String? get() = dto.phase
    override val status: Status? get() = Status.fromString(dto.status)
    override val lastUpdateTime: Long get() = dto.lastUpdateTime
    override val lastKnownLocation: Location? get() = dto.lastKnownLocation?.toLocation()
    override val lastLocationUpdateTime: Long get() = dto.lastLocationUpdateTime
    override val lastKnownDistanceAlongTrip: Double? get() = dto.lastKnownDistanceAlongTrip
    override val lastKnownOrientation: Double? get() = dto.lastKnownOrientation
    override val blockTripSequence: Int get() = dto.blockTripSequence
    override val occupancyStatus: Occupancy? get() = Occupancy.fromString(dto.occupancyStatus)
}

/** Presents a [TripReference] as an [ObaTrip]. */
internal class DtoTrip(private val ref: TripReference) : ObaTrip {
    override val id: String get() = ref.id
    override val shortName: String? get() = ref.tripShortName
    override val shapeId: String? get() = ref.shapeId
    override val directionId: Int get() = ref.directionId?.toIntOrNull() ?: 0
    override val serviceId: String? get() = ref.serviceId
    override val headsign: String? get() = ref.tripHeadsign
    override val timezone: String? get() = ref.timeZone
    override val routeId: String get() = ref.routeId
    override val blockId: String? get() = ref.blockId
}

/** Presents a [TripDetailsEntry] as an [ObaTripDetails]. */
internal class DtoTripDetails(private val entry: TripDetailsEntry) : ObaTripDetails {
    override val id: String get() = entry.tripId
    override val status: ObaTripStatus? get() = entry.status?.let { DtoTripStatus(it) }
    override val schedule: ObaTripSchedule? get() = entry.schedule?.toObaTripSchedule()
}

/** Maps the io/client [TripSchedule] DTO to the [ObaTripSchedule] model (consumed by schedule replay). */
fun TripSchedule.toObaTripSchedule(): ObaTripSchedule {
    val times: List<ObaTripSchedule.StopTime> = stopTimes.map {
        StopTimeData(
            it.stopId,
            it.stopHeadsign,
            it.arrivalTime,
            it.departureTime,
            Occupancy.fromString(it.historicalOccupancy),
            Occupancy.fromString(it.predictedOccupancy),
            it.distanceAlongTrip,
        )
    }
    return TripScheduleData(times.toTypedArray(), timeZone, previousTripId, nextTripId)
}

/**
 * Plain in-memory [ObaTripSchedule], built by [toObaTripSchedule] from the wire DTO. Not a `data`
 * class — equality is identity, matching the schedule's use as a cached value where structural
 * equality over the [stopTimes] array would be meaningless.
 */
class TripScheduleData(
    override val stopTimes: Array<ObaTripSchedule.StopTime> = emptyArray(),
    override val timeZone: String? = null,
    override val previousTripId: String? = null,
    override val nextTripId: String? = null,
) : ObaTripSchedule {

    companion object {
        @JvmField
        val EMPTY = TripScheduleData()
    }
}

/** Plain in-memory [ObaTripSchedule.StopTime]. */
class StopTimeData(
    override val stopId: String = "",
    override val headsign: String? = null,
    override val arrivalTime: Long = 0,
    override val departureTime: Long = 0,
    override val historicalOccupancy: Occupancy? = null,
    override val predictedOccupancy: Occupancy? = null,
    override val distanceAlongTrip: Double = 0.0,
) : ObaTripSchedule.StopTime
