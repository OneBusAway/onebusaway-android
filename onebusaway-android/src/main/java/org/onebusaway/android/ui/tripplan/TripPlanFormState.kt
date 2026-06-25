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
package org.onebusaway.android.ui.tripplan

import org.opentripplanner.api.model.Itinerary

/**
 * A JVM-pure projection of a trip-plan endpoint (a [org.onebusaway.android.directions.util.CustomAddress]),
 * so the ViewModel and its tests don't depend on `android.location.Address`. [lat]/[lon] are null
 * for endpoints without coordinates (e.g. a contacts pick); the repository encodes those as a raw
 * string for the OTP server to geocode.
 */
data class PlaceItem(
    val displayName: String,
    val lat: Double? = null,
    val lon: Double? = null,
    /** The geocoder flagged this as a public-transit location (drives the suggestion icon). */
    val isTransit: Boolean = false,
    val isCurrentLocation: Boolean = false
) {
    /** Mirrors CustomAddress.isSet(): a usable endpoint must have coordinates. */
    val hasCoordinates: Boolean get() = lat != null && lon != null
}

/** The advanced trip options, persisted in preferences by the host. */
data class AdvancedSettings(
    val modeId: Int,
    val maxWalkMeters: Double?,
    val optimizeTransfers: Boolean,
    val wheelchair: Boolean
)

/** A fully-specified plan request handed to [TripPlanRepository]. */
data class TripPlanParams(
    val from: PlaceItem,
    val to: PlaceItem,
    val dateTimeMillis: Long,
    val arriving: Boolean,
    val modeId: Int,
    val wheelchair: Boolean,
    val optimizeTransfers: Boolean,
    val maxWalkMeters: Double?
)

/** The trip-plan form (origin/destination, when, and the advanced options). */
data class TripPlanFormState(
    val from: PlaceItem? = null,
    val to: PlaceItem? = null,
    val fromQuery: String = "",
    val toQuery: String = "",
    val fromSuggestions: List<PlaceItem> = emptyList(),
    val toSuggestions: List<PlaceItem> = emptyList(),
    val dateTimeMillis: Long = 0L,
    val arriving: Boolean = false,
    val dateLabel: String = "",
    val timeLabel: String = "",
    val modeId: Int = 0,
    val wheelchair: Boolean = false,
    val optimizeTransfers: Boolean = false,
    val maxWalkMeters: Double? = null
) {
    /** Mirrors TripRequestBuilder.ready(): both endpoints must resolve to coordinates. */
    val canSubmit: Boolean
        get() = from?.hasCoordinates == true && to?.hasCoordinates == true

    /** The current advanced options, for persistence by the host. */
    val advancedSettings: AdvancedSettings
        get() = AdvancedSettings(modeId, maxWalkMeters, optimizeTransfers, wheelchair)

    /** Builds the plan request; only call when [canSubmit] is true. */
    fun toParams(): TripPlanParams = TripPlanParams(
        from = from!!,
        to = to!!,
        dateTimeMillis = dateTimeMillis,
        arriving = arriving,
        modeId = modeId,
        wheelchair = wheelchair,
        optimizeTransfers = optimizeTransfers,
        maxWalkMeters = maxWalkMeters
    )
}

/** The state of a plan submission. The host shows the results screen on [Success]. */
sealed interface PlanResult {
    data object Idle : PlanResult
    data object Loading : PlanResult
    data class Success(val itineraries: List<Itinerary>) : PlanResult
    data class Error(val message: String) : PlanResult
}
