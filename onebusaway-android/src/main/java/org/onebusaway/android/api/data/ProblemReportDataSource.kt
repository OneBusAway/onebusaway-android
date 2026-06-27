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

import org.onebusaway.android.api.requireOk

import org.onebusaway.android.api.net.ObaApiProvider

import javax.inject.Inject

/**
 * Submits stop/trip problem reports to the OBA REST API. Takes plain values (the feature unpacks its
 * UI params/Location into these) and builds the legacy `data` param internally, so callers never
 * touch [ObaWebService]. A non-OK app-level code or a transport failure maps to [Result.failure].
 */
interface ProblemReportDataSource {

    suspend fun reportStop(
        stopId: String,
        code: String,
        comment: String?,
        lat: Double?,
        lon: Double?,
        accuracyMeters: Int?,
    ): Result<Unit>

    suspend fun reportTrip(
        tripId: String,
        stopId: String?,
        serviceDate: Long,
        vehicleId: String?,
        code: String,
        comment: String?,
        onVehicle: Boolean,
        vehicleNumber: String?,
        lat: Double?,
        lon: Double?,
        accuracyMeters: Int?,
    ): Result<Unit>
}

class DefaultProblemReportDataSource @Inject constructor(
    private val api: ObaApiProvider
) : ProblemReportDataSource {

    override suspend fun reportStop(
        stopId: String,
        code: String,
        comment: String?,
        lat: Double?,
        lon: Double?,
        accuracyMeters: Int?,
    ): Result<Unit> = api.call {
        it.reportProblemWithStop(
            stopId = stopId,
            code = code,
            data = dataJson(code),
            userComment = comment,
            userLat = lat,
            userLon = lon,
            userLocationAccuracy = accuracyMeters,
        ).requireOk()
    }

    override suspend fun reportTrip(
        tripId: String,
        stopId: String?,
        serviceDate: Long,
        vehicleId: String?,
        code: String,
        comment: String?,
        onVehicle: Boolean,
        vehicleNumber: String?,
        lat: Double?,
        lon: Double?,
        accuracyMeters: Int?,
    ): Result<Unit> = api.call {
        it.reportProblemWithTrip(
            tripId = tripId,
            code = code,
            data = dataJson(code),
            stopId = stopId,
            serviceDate = serviceDate,
            vehicleId = vehicleId,
            userComment = comment,
            userLat = lat,
            userLon = lon,
            userLocationAccuracy = accuracyMeters,
            userOnVehicle = onVehicle,
            userVehicleNumber = vehicleNumber,
        ).requireOk()
    }

    /** The legacy JSON-encoded `data` param the API still expects alongside `code`. */
    private fun dataJson(code: String) = """{"code":"$code"}"""
}
