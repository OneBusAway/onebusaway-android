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

import org.onebusaway.android.directions.model.TripItinerary

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

    /** Nothing here — an empty editable field. A half-typed query is content, so it isn't empty. */
    val isEmpty: Boolean get() = this is FreeText && query.isBlank()

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
        override val isTransit: Boolean = false
    ) : TripEndpoint

    /** An address-book (contacts) pick; may still need server-side geocoding (null coordinates). */
    data class AddressBook(
        val displayName: String,
        override val lat: Double?,
        override val lon: Double?
    ) : TripEndpoint

    /** The device's current location. Its label is a fixed string resolved by the UI. */
    data class CurrentLocation(override val lat: Double?, override val lon: Double?) : TripEndpoint

    /** A point chosen on the map. Its label is a fixed string resolved by the UI. */
    data class MapPoint(override val lat: Double, override val lon: Double) : TripEndpoint
}

/** Which of the form's two endpoints an action targets. */
enum class TripEndpointSlot {
    FROM,
    TO;

    /** The endpoint at the trip's other end. */
    val other: TripEndpointSlot get() = if (this == FROM) TO else FROM
}

/**
 * The advanced trip options, persisted in preferences by the host.
 *
 * [maxWalkMeters] and [walkPreference] express the same wish through the two protocols' different
 * vocabularies — a hard cap OTP1 accepts, a reluctance OTP2 accepts — and neither server sees the
 * other's field. Both are carried (and persisted) at all times so switching regions doesn't discard
 * whichever one the previous region used; the advanced-settings dialog shows only the one the
 * current region's server can act on.
 */
data class AdvancedSettings(
    val modes: TripModeSelection,
    val maxWalkMeters: Double?,
    val optimizeTransfers: Boolean,
    val wheelchair: Boolean,
    val walkPreference: WalkPreference = WalkPreference.MEDIUM,
    val cyclingPreference: CyclingPreference = CyclingPreference.DEFAULT,
    val bikePreference: BikePreference = BikePreference.MEDIUM
)

/** A fully-specified plan request handed to [TripPlanRepository]. */
data class TripPlanParams(
    val from: TripEndpoint,
    val to: TripEndpoint,
    val dateTimeMillis: Long,
    val arriving: Boolean,
    val modes: TripModeSelection,
    val wheelchair: Boolean,
    val optimizeTransfers: Boolean,
    val maxWalkMeters: Double?,
    val walkPreference: WalkPreference = WalkPreference.MEDIUM,
    val cyclingPreference: CyclingPreference = CyclingPreference.DEFAULT,
    val bikePreference: BikePreference = BikePreference.MEDIUM
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
    val modes: TripModeSelection = TripModeSelection(),
    val wheelchair: Boolean = false,
    val optimizeTransfers: Boolean = false,
    val maxWalkMeters: Double? = null,
    val walkPreference: WalkPreference = WalkPreference.MEDIUM,
    val cyclingPreference: CyclingPreference = CyclingPreference.DEFAULT,
    val bikePreference: BikePreference = BikePreference.MEDIUM
) {
    /** Mirrors TripRequestBuilder.ready(): both endpoints must resolve to coordinates. */
    val canSubmit: Boolean
        get() = from.hasCoordinates && to.hasCoordinates

    /** The endpoint currently in [slot]. */
    fun endpointAt(slot: TripEndpointSlot): TripEndpoint = when (slot) {
        TripEndpointSlot.FROM -> from
        TripEndpointSlot.TO -> to
    }

    /** The autocomplete suggestions currently offered for [slot]. */
    fun suggestionsAt(slot: TripEndpointSlot): List<TripEndpoint.Geocoded> = when (slot) {
        TripEndpointSlot.FROM -> fromSuggestions
        TripEndpointSlot.TO -> toSuggestions
    }

    /** This form with [slot] set to [endpoint], dropping that field's now-stale suggestions. */
    fun withEndpoint(slot: TripEndpointSlot, endpoint: TripEndpoint): TripPlanFormState = when (slot) {
        TripEndpointSlot.FROM -> copy(from = endpoint, fromSuggestions = emptyList())
        TripEndpointSlot.TO -> copy(to = endpoint, toSuggestions = emptyList())
    }

    /**
     * This form with [slot] being typed into. Unlike [withEndpoint] the suggestions stay put — they're
     * what the rider is picking from, and the debounced lookup replaces them a moment later.
     */
    fun withTypedQuery(slot: TripEndpointSlot, query: String): TripPlanFormState = when (slot) {
        TripEndpointSlot.FROM -> copy(from = TripEndpoint.FreeText(query))
        TripEndpointSlot.TO -> copy(to = TripEndpoint.FreeText(query))
    }

    /** This form with [slot]'s autocomplete suggestions replaced by [suggestions]. */
    fun withSuggestions(slot: TripEndpointSlot, suggestions: List<TripEndpoint.Geocoded>): TripPlanFormState = when (slot) {
        TripEndpointSlot.FROM -> copy(fromSuggestions = suggestions)
        TripEndpointSlot.TO -> copy(toSuggestions = suggestions)
    }

    /**
     * This form with [slot] set to [endpoint], and the trip's other end filled with [here] (the
     * device's current location) if — and only if — that end is [TripEndpoint.isEmpty] right now (#2092).
     *
     * Naming one end of a trip on its own leaves a form nobody can submit, and the other end is
     * overwhelmingly the rider's own position, so it's filled in for them and the trip plans on the
     * spot. Strictly a convenience: an endpoint carrying anything at all — a resolved pill, a half-typed
     * query — is left untouched, and with no fix available ([here] null) that end simply stays empty.
     *
     * The test is what the field holds now, not whether the rider ever touched it: an end they cleared
     * with the pill's ✕ is empty again and will be paired. That's deliberate — the ✕ says "not this
     * place", and a form the rider then leaves half-filled is no more useful for having been edited.
     */
    fun withEndpointPaired(
        slot: TripEndpointSlot,
        endpoint: TripEndpoint,
        here: TripEndpoint.CurrentLocation?
    ): TripPlanFormState {
        val filled = withEndpoint(slot, endpoint)
        if (here == null || !endpointAt(slot.other).isEmpty) return filled
        return filled.withEndpoint(slot.other, here)
    }

    /** The current advanced options, for persistence by the host. */
    val advancedSettings: AdvancedSettings
        get() = AdvancedSettings(
            modes = modes,
            maxWalkMeters = maxWalkMeters,
            optimizeTransfers = optimizeTransfers,
            wheelchair = wheelchair,
            walkPreference = walkPreference,
            cyclingPreference = cyclingPreference,
            bikePreference = bikePreference
        )

    /** Builds the plan request; only call when [canSubmit] is true. */
    fun toParams(): TripPlanParams = TripPlanParams(
        from = from,
        to = to,
        dateTimeMillis = dateTimeMillis,
        arriving = arriving,
        modes = modes,
        wheelchair = wheelchair,
        optimizeTransfers = optimizeTransfers,
        maxWalkMeters = maxWalkMeters,
        walkPreference = walkPreference,
        cyclingPreference = cyclingPreference,
        bikePreference = bikePreference
    )
}

/** The state of a plan submission. The host shows the results screen on [Success]. */
sealed interface PlanResult {
    data object Idle : PlanResult
    data object Loading : PlanResult

    /**
     * [params] are the request that produced these [itineraries], carried so the trip-plan-change
     * monitor can re-plan the same request. Null when the results were restored from a notification
     * re-entry (the full request isn't reconstructed there), in which case monitoring isn't re-armed.
     */
    data class Success(
        val itineraries: List<TripItinerary>,
        val params: TripPlanParams? = null
    ) : PlanResult
    data class Error(val error: TripPlanError) : PlanResult
}
