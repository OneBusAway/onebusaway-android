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
package org.onebusaway.android.models

/**
 * The arrival fields the display model (`ArrivalInfo`) computes from, abstracted from the wire type.
 * io.client adapts the arrivals fetch into these (`DtoArrivalData`); times are epoch millis.
 */
interface ArrivalData {
    val routeId: String
    val tripId: String
    val stopId: String
    val headsign: String?
    val shortName: String?
    val routeLongName: String?
    val stopSequence: Int
    val serviceDate: Long
    val vehicleId: String?
    val predicted: Boolean
    val scheduledArrivalTime: Long
    val predictedArrivalTime: Long
    val scheduledDepartureTime: Long
    val predictedDepartureTime: Long
    val status: Status?
    val frequency: FrequencyWindow?
    val historicalOccupancy: Occupancy?
    val predictedOccupancy: Occupancy?
    /** Real-time fields, for the report context; defaults when there is no trip status. */
    val hasTripStatus: Boolean
    val scheduleDeviation: Long
    val lastKnownLat: Double?
    val lastKnownLon: Double?
}

/** Headway-based (exact_times=0) service window; epoch millis / seconds, matching the wire. */
data class FrequencyWindow(val startTime: Long, val endTime: Long, val headway: Long)
