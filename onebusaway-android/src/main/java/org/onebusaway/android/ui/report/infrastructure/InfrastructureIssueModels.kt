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
package org.onebusaway.android.ui.report.infrastructure

import org.onebusaway.android.report.TripReportContext
import org.onebusaway.android.io.elements.ObaStop

/**
 * The issue's current map position, replacing the mutable IssueLocationHelper. [latitude]/
 * [longitude] are the effective issue coordinate — the map supplies the focused stop's location
 * directly, so we never read the (Android) stop.location here. [stop] is retained for routing.
 */
data class IssueLocation(
    val latitude: Double,
    val longitude: Double,
    val stop: ObaStop? = null
)

/**
 * A snapshot of the issue's location/address/stop for the host to wrap in its Open311 context type
 * (so the ViewModel doesn't import the Open311 layer's Open311IssueContext).
 */
data class IssueContextSnapshot(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val stop: ObaStop?
)

/** Which report form the current selection routes to; the host performs the transaction. */
sealed interface ReportTarget {
    data object None : ReportTarget
    data class StopProblem(val stop: ObaStop) : ReportTarget

    /** A null [arrival] means the host shows the arrival picker first; non-null shows the form. */
    data class TripProblem(val stop: ObaStop, val arrival: TripReportContext?) : ReportTarget
    data class Open311(val category: ServiceListItem.Category, val arrival: TripReportContext?) : ReportTarget
}

/** The category to auto-select when the screen opens, from the report type list. */
enum class DefaultIssueType { NONE, STOP, TRIP }

/** Complete UI state for the infrastructure-issue container. */
data class InfrastructureIssueUiState(
    val location: IssueLocation,
    val address: String = "",
    val services: List<ServiceListItem> = emptyList(),
    val selectedIndex: Int = 0,
    val servicesVisible: Boolean = false,
    val busStopName: String? = null,
    /** Show the "tap a stop on the map" prompt (report_dialog_stop_header). */
    val showStopPrompt: Boolean = false,
    val loadingServices: Boolean = false,
    val target: ReportTarget = ReportTarget.None,
    /** When non-null, the host shows a single manual marker here; null clears it. */
    val markerLocation: IssueLocation? = null
)

/** One-shot effects the host carries out (map recenter, toast, success dialog). */
sealed interface InfrastructureIssueEvent {
    data class RecenterMap(val latitude: Double, val longitude: Double) : InfrastructureIssueEvent
    data object AddressNotFound : InfrastructureIssueEvent
    data object ReportSent : InfrastructureIssueEvent
}
