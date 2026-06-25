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
package org.onebusaway.android.ui.tripdetails

import androidx.annotation.ColorRes

/** Where a stop's dot sits on the trip's vertical transit line. */
enum class LinePosition { FIRST, MIDDLE, LAST }

/** The overlay pin drawn at a stop: the scrolled-to stop, or a destination-reminder flag. */
enum class StopPin { NONE, FOCUSED, DESTINATION }

/** The trip header: route/headsign/agency plus the (real-time) vehicle + schedule-deviation status. */
data class TripHeader(
    val routeShortName: String,
    val headsign: String,
    /** Trip short name (e.g. a run number); null/blank hides it. */
    val tripShortName: String?,
    val agencyName: String,
    /** Vehicle id label; null when there's no real-time vehicle. */
    val vehicleId: String?,
    val statusText: String,
    @ColorRes val statusColor: Int,
    /** True when the trip has real-time data (drives the pulsing indicator). */
    val isRealtime: Boolean
)

/**
 * One stop along the trip. [linePosition] + [isVehicleHere]/[pin] drive the transit-line
 * visualization; [isPassed] fades stops the vehicle has already left.
 */
data class TripStopItem(
    val stopId: String,
    val name: String,
    val direction: String?,
    val timeText: String,
    val canceled: Boolean,
    /** True once the vehicle has left this stop — the row's name/time fade to the "passed" color. */
    val isPassed: Boolean,
    val linePosition: LinePosition,
    /** The vehicle's current position (the stop it most recently left). */
    val isVehicleHere: Boolean,
    val pin: StopPin
)

/** UI state for the trip details screen. */
sealed interface TripDetailsUiState {

    data object Loading : TripDetailsUiState

    /**
     * @param stops the trip's stops in order
     * @param scrollToIndex the stop to scroll to on first load (vehicle/focused/destination), or -1
     * @param routeId the trip's route, for the "show on map" action
     * @param lineColorArgb the resolved transit-line color (route color, or the theme default)
     */
    data class Content(
        val header: TripHeader,
        val stops: List<TripStopItem>,
        val scrollToIndex: Int,
        val routeId: String,
        val lineColorArgb: Int
    ) : TripDetailsUiState

    data class Error(val message: String) : TripDetailsUiState
}
