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

import org.onebusaway.android.io.request.ObaReportProblemWithStopRequest
import org.onebusaway.android.io.request.ObaReportProblemWithTripRequest

/** Whether the form reports a problem about a stop or about a trip/vehicle. */
enum class ProblemKind { STOP, TRIP }

/** One selectable problem category. A null [code] is the unselected "Choose a problem" hint row. */
data class ProblemCode(val code: String?, val label: String)

/** Identity of the thing being reported, kept separate from the editable form state. */
sealed interface ProblemParams {

    data class Stop(val stopId: String) : ProblemParams

    data class Trip(
        val tripId: String,
        val stopId: String?,
        val vehicleId: String?,
        val serviceDate: Long
    ) : ProblemParams
}

/** Immutable UI state for the problem form, fully driving [ProblemReportForm]. */
data class ProblemFormState(
    val kind: ProblemKind,
    val codes: List<ProblemCode>,
    val selectedCodeIndex: Int = 0,
    val comment: String = "",
    val headsign: String? = null,
    val onVehicle: Boolean = false,
    val vehicleNumber: String = ""
) {
    val selectedCode: ProblemCode get() = codes[selectedCodeIndex]

    /** A real category (not the hint) must be chosen before the report can be sent. */
    val canSubmit: Boolean get() = selectedCode.code != null
}

/** Lifecycle of a single submit attempt. */
sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data object Sent : SubmitState
    data object Error : SubmitState
}

/**
 * Zips the localized problem-category labels (from `R.array.report_*_problem_code*`) with the
 * request codes, in the order the legacy spinners used. The first row is the hint (`code = null`).
 */
object ProblemCodes {

    fun stop(labels: List<String>): List<ProblemCode> = zip(labels, STOP_CODES)

    fun trip(labels: List<String>): List<ProblemCode> = zip(labels, TRIP_CODES)

    private val STOP_CODES = listOf(
        null,
        ObaReportProblemWithStopRequest.NAME_WRONG,
        ObaReportProblemWithStopRequest.NUMBER_WRONG,
        ObaReportProblemWithStopRequest.LOCATION_WRONG,
        ObaReportProblemWithStopRequest.ROUTE_OR_TRIP_MISSING,
        ObaReportProblemWithStopRequest.OTHER
    )

    private val TRIP_CODES = listOf(
        null,
        ObaReportProblemWithTripRequest.VEHICLE_NEVER_CAME,
        ObaReportProblemWithTripRequest.VEHICLE_CAME_EARLY,
        ObaReportProblemWithTripRequest.VEHICLE_CAME_LATE,
        ObaReportProblemWithTripRequest.WRONG_HEADSIGN,
        ObaReportProblemWithTripRequest.VEHICLE_DOES_NOT_STOP_HERE,
        ObaReportProblemWithTripRequest.OTHER
    )

    private fun zip(labels: List<String>, codes: List<String?>): List<ProblemCode> =
        labels.mapIndexed { index, label -> ProblemCode(codes.getOrNull(index), label) }
}
