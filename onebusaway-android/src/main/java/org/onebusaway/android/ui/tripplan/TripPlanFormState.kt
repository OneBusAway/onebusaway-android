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
 * so the ViewModel and its tests don't depend on `android.location.Address`. Modeled as a sealed type
 * so the form can render each kind appropriately: only [FreeText] is an editable field; every resolved
 * kind ([Geocoded]/[AddressBook]/[CurrentLocation]/[MapPoint]) is shown as a cancellable pill.
 *
 * [lat]/[lon] are null for endpoints without coordinates (e.g. a contacts pick or a still-typed query);
 * the repository encodes those as a raw string for the OTP server to geocode.
 */
sealed interface TripEndpoint {
    val lat: Double?
    val lon: Double?

    /** Mirrors CustomAddress.isSet(): a usable endpoint must have coordinates. */
    val hasCoordinates: Boolean get() = lat != null && lon != null

    /** The geocoder flagged this as a public-transit location (drives the pill/suggestion icon). */
    val isTransit: Boolean get() = false

    /**
     * The text this endpoint carries itself (a typed query or a resolved place name), or null for the
     * fixed-label kinds ([CurrentLocation]/[MapPoint]) whose label is a string resource resolved by the
     * Android layer. Keeps the shared part of the endpoint→label mapping in one place instead of
     * duplicated across each call site's `when`.
     */
    val displayText: String? get() = when (this) {
        is FreeText -> query
        is Geocoded -> displayName
        is AddressBook -> displayName
        is CurrentLocation, is MapPoint -> null
    }

    /** Empty or still-being-typed text — the only editable, non-pill state. Never has coordinates. */
    data class FreeText(val query: String = "") : TripEndpoint {
        override val lat: Double? get() = null
        override val lon: Double? get() = null
    }

    /** A geocoder (Pelias) autocomplete pick. */
    data class Geocoded(
        val displayName: String,
        override val lat: Double?,
        override val lon: Double?,
        /** The geocoder flagged this as a public-transit location (drives the pill/suggestion icon). */
        override val isTransit: Boolean = false,
    ) : TripEndpoint

    /** An address-book (contacts) pick; may still need server-side geocoding (null coordinates). */
    data class AddressBook(
        val displayName: String,
        override val lat: Double?,
        override val lon: Double?,
    ) : TripEndpoint

    /** The device's current location. Its label is a fixed string resolved by the UI. */
    data class CurrentLocation(override val lat: Double?, override val lon: Double?) : TripEndpoint

    /** A point chosen on the map. Its label is a fixed string resolved by the UI. */
    data class MapPoint(override val lat: Double, override val lon: Double) : TripEndpoint
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
    val from: TripEndpoint,
    val to: TripEndpoint,
    val dateTimeMillis: Long,
    val arriving: Boolean,
    val modeId: Int,
    val wheelchair: Boolean,
    val optimizeTransfers: Boolean,
    val maxWalkMeters: Double?
)

/** The trip-plan form (origin/destination, when, and the advanced options). */
data class TripPlanFormState(
    val from: TripEndpoint = TripEndpoint.FreeText(),
    val to: TripEndpoint = TripEndpoint.FreeText(),
    val fromSuggestions: List<TripEndpoint.Geocoded> = emptyList(),
    val toSuggestions: List<TripEndpoint.Geocoded> = emptyList(),
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
        get() = from.hasCoordinates && to.hasCoordinates

    /** The current advanced options, for persistence by the host. */
    val advancedSettings: AdvancedSettings
        get() = AdvancedSettings(modeId, maxWalkMeters, optimizeTransfers, wheelchair)

    /** Builds the plan request; only call when [canSubmit] is true. */
    fun toParams(): TripPlanParams = TripPlanParams(
        from = from,
        to = to,
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
