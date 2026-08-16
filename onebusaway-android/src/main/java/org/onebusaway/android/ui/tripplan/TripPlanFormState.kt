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
import org.onebusaway.android.time.ServerTime

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
     * This endpoint *is* the device's own position, which the map already marks with the location
     * layer's blue dot. Such an endpoint is given no pin of its own — neither the standalone From/To
     * pin shown while the form is being filled in nor the drawn itinerary's terminus pin — since a pin
     * dropped on top of the blue dot says nothing the dot doesn't (#2111).
     */
    val isDeviceLocation: Boolean get() = this is CurrentLocation

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

/**
 * Which of the form's two endpoints an action targets. Declaration order is the form's field order
 * (origin above destination) — [org.onebusaway.android.ui.tripplan.TripPlanForm] renders `entries`.
 */
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

/**
 * Which day a pinned trip instant falls on, as a rider would name it. Settled where the form's
 * date/time labels are — against a clock reading, in the ViewModel — so the callout can state the
 * day in words instead of a date whenever there is a word for it (#2185).
 */
enum class TripDay {
    TODAY,
    TOMORROW,
    OTHER
}

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
) {
    /**
     * When the plan puts the rider at its starting point: the requested departure of a depart-at plan,
     * and null for an arrive-by one, which fixes when the trip *ends* and says nothing about when the
     * rider sets out. The trip log uses it as when the rider is at the first stop of an itinerary that
     * opens on transit (#2228) — the plan's own "you get here" for a ride nothing precedes.
     *
     * Minted as [ServerTime] because it is the instant the plan was made against ([dateTimeMillis] is
     * what the planner is asked to depart at), and the plan's leg times come back on that same timeline
     * as server-domain instants; it is compared only with those and with the stop's arrival predictions.
     */
    val plannedStart: ServerTime?
        get() = if (arriving) null else ServerTime(dateTimeMillis)
}

/** The trip-plan form (origin/destination, when, and the advanced options). */
data class TripPlanFormState(
    val from: TripEndpoint = TripEndpoint.FreeText(),
    val to: TripEndpoint = TripEndpoint.FreeText(),
    val fromSuggestions: List<TripEndpoint.Geocoded> = emptyList(),
    val toSuggestions: List<TripEndpoint.Geocoded> = emptyList(),
    val dateTimeMillis: Long = 0L,
    val arriving: Boolean = false,
    /**
     * The trip is anchored to "now" rather than to [dateTimeMillis] — the default, and what the form's
     * time control shows until a date or time is picked. The distinction has to be explicit because
     * "now" is a *moving* anchor: [dateTimeMillis] is stamped once when the ViewModel is built, so a
     * form left open for twenty minutes would otherwise plan a trip twenty minutes in the past. The
     * live instant is resolved at submit — see [toParams].
     */
    val departNow: Boolean = true,
    val dateLabel: String = "",
    val timeLabel: String = "",
    /**
     * Which day [dateTimeMillis] falls on, relative to the clock as it read when the labels above
     * were written. The callout leads with the time and names the day only when it isn't today, so
     * this travels with [dateLabel]/[timeLabel] rather than being re-derived per recomposition —
     * one instant, one set of labels, no way for them to disagree.
     */
    val dayRelation: TripDay = TripDay.TODAY,
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

    /**
     * Either end of the trip is the device's own position, so a re-plan has to re-read the fix first —
     * see [withDeviceLocationAt].
     */
    val hasDeviceLocationEndpoint: Boolean
        get() = from.isDeviceLocation || to.isDeviceLocation

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
     * spot. Strictly a convenience — it only ever fills a field that was empty anyway.
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

    /**
     * This form with either end that is the device's own position moved to [here], the latest fix (#2134).
     *
     * "My location" names *where the rider is*, not where they were standing when they tapped it — but
     * a [TripEndpoint.CurrentLocation] carries the coordinate it was built from, so without this every
     * re-plan after the first (a mode change, a date nudge, editing the other end) re-sent the fix the
     * endpoint was created with and the trip kept starting from where the form was first filled in. The
     * fix is re-read at submit for the same reason the clock is — see [toParams].
     *
     * A null [here] — no fix yet, or the location permission is gone — leaves both ends alone: the
     * coordinate an endpoint already holds is the best answer available, and blanking it would only turn
     * a submittable form unsubmittable.
     */
    fun withDeviceLocationAt(here: TripEndpoint.CurrentLocation?): TripPlanFormState {
        if (here == null) return this
        return copy(
            from = if (from.isDeviceLocation) here else from,
            to = if (to.isDeviceLocation) here else to
        )
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

    /**
     * Builds the plan request; only call when [canSubmit] is true.
     *
     * [nowMillis] is the caller's reading of the wall clock, passed in rather than read here so this
     * stays a pure projection of the form (the same rule the ETA helpers follow). It is used only when
     * [departNow] is set, which is what makes "now" mean the moment of submission rather than the
     * moment the form was opened.
     */
    fun toParams(nowMillis: Long): TripPlanParams = TripPlanParams(
        from = from,
        to = to,
        dateTimeMillis = if (departNow) nowMillis else dateTimeMillis,
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
     *
     * [fromSnapshot] marks results that came off disk rather than off the wire — a pinned trip the
     * rider just resumed (#2053). It gates the change monitor: a stored plan carries [params], so the
     * monitor *could* be armed for it, but its departure may be long past and the monitor's start
     * window (`departure - now <= window`) admits a past departure, which would raise a foreground
     * service for a bus that has already gone. This states the fact — where these itineraries came
     * from — rather than inferring staleness from their timestamps. A Refresh re-plans and re-arms.
     */
    data class Success(
        val itineraries: List<TripItinerary>,
        val params: TripPlanParams? = null,
        val fromSnapshot: Boolean = false
    ) : PlanResult
    data class Error(val error: TripPlanError) : PlanResult
}
