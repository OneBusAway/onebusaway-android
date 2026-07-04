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
package org.onebusaway.android.ui.report.problem

import org.onebusaway.android.api.data.ProblemReportDataSource

import android.location.Location

/** Submits stop/trip problem reports to the OBA REST API. */
interface ProblemReportRepository {

    suspend fun submitStop(
        stopId: String,
        code: String,
        comment: String,
        location: Location?
    ): Result<Unit>

    suspend fun submitTrip(
        params: ProblemParams.Trip,
        code: String,
        comment: String,
        onVehicle: Boolean,
        vehicleNumber: String,
        location: Location?
    ): Result<Unit>
}

/**
 * Default implementation that unpacks the UI's report params + [Location] into the plain values the
 * api [ProblemReportDataSource] takes, keeping the wire call (and the legacy `data` param) inside
 * io. A non-OK app-level code or a transport failure maps to [Result.failure] in the service.
 */
class DefaultProblemReportRepository(
    private val reportService: ProblemReportDataSource
) : ProblemReportRepository {

    override suspend fun submitStop(
        stopId: String,
        code: String,
        comment: String,
        location: Location?
    ): Result<Unit> = reportService.reportStop(
        stopId = stopId,
        code = code,
        comment = comment.ifEmpty { null },
        lat = location?.latitude,
        lon = location?.longitude,
        accuracyMeters = location?.accuracyMeters(),
    )

    override suspend fun submitTrip(
        params: ProblemParams.Trip,
        code: String,
        comment: String,
        onVehicle: Boolean,
        vehicleNumber: String,
        location: Location?
    ): Result<Unit> = reportService.reportTrip(
        tripId = params.tripId,
        stopId = params.stopId,
        serviceDate = params.serviceDate,
        vehicleId = params.vehicleId,
        code = code,
        comment = comment.ifEmpty { null },
        onVehicle = onVehicle,
        vehicleNumber = vehicleNumber.ifEmpty { null },
        lat = location?.latitude,
        lon = location?.longitude,
        accuracyMeters = location?.accuracyMeters(),
    )

    /** The location's accuracy in whole meters, or null when the fix carries none. */
    private fun Location.accuracyMeters(): Int? = if (hasAccuracy()) accuracy.toInt() else null
}
