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

import android.content.Context
import android.location.Location
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaReportProblemWithStopRequest
import org.onebusaway.android.io.request.ObaReportProblemWithTripRequest
import org.onebusaway.android.io.request.ObaResponse

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
 * Default implementation wrapping the blocking OBA report requests (replacing the legacy
 * ReportLoader AsyncTaskLoader). RequestBase.call() never throws — a non-OBA_OK code maps to
 * [Result.failure].
 */
class DefaultProblemReportRepository(private val context: Context) : ProblemReportRepository {

    override suspend fun submitStop(
        stopId: String,
        code: String,
        comment: String,
        location: Location?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val builder = ObaReportProblemWithStopRequest.Builder(context, stopId)
        builder.setCode(code)
        if (comment.isNotEmpty()) builder.setUserComment(comment)
        applyLocation(location, builder::setUserLocation, builder::setUserLocationAccuracy)
        builder.build().call().toResult()
    }

    override suspend fun submitTrip(
        params: ProblemParams.Trip,
        code: String,
        comment: String,
        onVehicle: Boolean,
        vehicleNumber: String,
        location: Location?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val builder = ObaReportProblemWithTripRequest.Builder(context, params.tripId)
        builder.setStopId(params.stopId)
        builder.setVehicleId(params.vehicleId)
        builder.setServiceDate(params.serviceDate)
        builder.setCode(code)
        if (comment.isNotEmpty()) builder.setUserComment(comment)
        applyLocation(location, builder::setUserLocation, builder::setUserLocationAccuracy)
        builder.setUserOnVehicle(onVehicle)
        if (vehicleNumber.isNotEmpty()) builder.setUserVehicleNumber(vehicleNumber)
        builder.build().call().toResult()
    }

    private fun ObaResponse?.toResult(): Result<Unit> =
        if (this != null && code == ObaApi.OBA_OK) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("Problem report failed with code " + this?.code))
        }

    /** Copies the user's [location] onto either request builder, only if one is available. */
    private fun applyLocation(
        location: Location?,
        setLocation: (Double, Double) -> Unit,
        setAccuracy: (Int) -> Unit
    ) {
        location?.let {
            setLocation(it.latitude, it.longitude)
            if (it.hasAccuracy()) setAccuracy(it.accuracy.toInt())
        }
    }
}
