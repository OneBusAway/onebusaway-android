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
package org.onebusaway.android.ui.tripplan.pinned

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.StreetMode
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.VehicleMode
import org.onebusaway.android.ui.tripplan.WalkPreference
import org.onebusaway.android.ui.tripplan.enumValueOrDefault

/**
 * A pinned trip's **request**, as it is stored — the trip the rider asked for, in a vocabulary that is
 * allowed to outlive the types the form is written in.
 *
 * A deliberate copy of [TripPlanParams] rather than `@Serializable` on the real thing, for three
 * reasons that all point the same way:
 * - [TripEndpoint] is a sealed interface, and polymorphic serialization keys its subtypes on their
 *   fully-qualified class names by default. A later rename or package move would then silently orphan
 *   every stored pin — with no compile error, because nothing about the move touches the payload.
 * - [TripPlanParams] carries defaulted fields, which vanish from an encoded payload unless whoever
 *   configures the `Json` remembers `encodeDefaults`. A type that exists to be written down should not
 *   depend on a setting living somewhere else.
 * - It keeps `kotlinx.serialization` out of `ui.tripplan` entirely, so the form's own types stay free to
 *   change shape without a stored-data conversation.
 *
 * The cost is this file and its round-trip test; the benefit is that the persisted vocabulary is an
 * artifact a reviewer can read and diff. Enums travel by **name**, through [enumValueOrDefault], so the
 * "a name this build doesn't know falls back to the neutral value" rule keeps its single statement.
 *
 * @param departNow the form anchor [TripPlanParams] does not carry. Without it a resumed "leave now"
 *        trip would come back pinned to the instant it was *submitted*, which is precisely the
 *        moving-anchor bug [org.onebusaway.android.ui.tripplan.TripPlanFormState.departNow] exists to
 *        prevent.
 */
@Serializable
internal data class PinnedTripQuery(
    val from: PinnedEndpoint,
    val to: PinnedEndpoint,
    val dateTimeMillis: Long,
    val departNow: Boolean,
    val arriving: Boolean,
    val vehicleMode: String,
    val streetMode: String,
    val wheelchair: Boolean,
    val optimizeTransfers: Boolean,
    val maxWalkMeters: Double?,
    val walkPreference: String,
    val cyclingPreference: String,
    val bikePreference: String
)

/**
 * One end of a pinned trip. A flat [kind] tag rather than a serialized hierarchy: the five endpoint
 * shapes differ only in which of these fields mean anything, which doesn't earn a class per shape on
 * the wire, and a flat tag is readable by any build that comes later.
 */
@Serializable
internal data class PinnedEndpoint(
    val kind: Kind,
    val text: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val isTransit: Boolean = false
) {
    @Serializable
    enum class Kind {
        FREE_TEXT,
        GEOCODED,
        ADDRESS_BOOK,
        CURRENT_LOCATION,
        MAP_POINT
    }
}

internal fun TripPlanParams.toPinnedQuery(departNow: Boolean): PinnedTripQuery = PinnedTripQuery(
    from = from.toPinnedEndpoint(),
    to = to.toPinnedEndpoint(),
    dateTimeMillis = dateTimeMillis,
    departNow = departNow,
    arriving = arriving,
    vehicleMode = modes.vehicle.name,
    streetMode = modes.street.name,
    wheelchair = wheelchair,
    optimizeTransfers = optimizeTransfers,
    maxWalkMeters = maxWalkMeters,
    walkPreference = walkPreference.name,
    cyclingPreference = cyclingPreference.name,
    bikePreference = bikePreference.name
)

/**
 * This stored request as the planner's own type, or **null** when the payload can't describe one.
 *
 * Fallible on purpose, and the one thing that can fail is a coordinate: [TripEndpoint.MapPoint] holds
 * non-null coordinates, so a `MAP_POINT` row without them is a corrupt payload rather than a point at
 * the origin of the Atlantic. Everything else has a defined reading — an unknown enum name is a value
 * from a newer build and degrades to the neutral one.
 */
internal fun PinnedTripQuery.toParamsOrNull(): TripPlanParams? {
    val origin = from.toEndpointOrNull() ?: return null
    val destination = to.toEndpointOrNull() ?: return null
    return TripPlanParams(
        from = origin,
        to = destination,
        dateTimeMillis = dateTimeMillis,
        arriving = arriving,
        modes = TripModeSelection(
            vehicle = enumValueOrDefault(vehicleMode, VehicleMode.ALL_TRANSIT),
            street = enumValueOrDefault(streetMode, StreetMode.WALK)
        ),
        wheelchair = wheelchair,
        optimizeTransfers = optimizeTransfers,
        maxWalkMeters = maxWalkMeters,
        walkPreference = enumValueOrDefault(walkPreference, WalkPreference.MEDIUM),
        cyclingPreference = enumValueOrDefault(cyclingPreference, CyclingPreference.DEFAULT),
        bikePreference = enumValueOrDefault(bikePreference, BikePreference.MEDIUM)
    )
}

private fun TripEndpoint.toPinnedEndpoint(): PinnedEndpoint = when (this) {
    is TripEndpoint.FreeText -> PinnedEndpoint(PinnedEndpoint.Kind.FREE_TEXT, text = query)
    is TripEndpoint.Geocoded ->
        PinnedEndpoint(PinnedEndpoint.Kind.GEOCODED, displayName, lat, lon, isTransit)
    is TripEndpoint.AddressBook ->
        PinnedEndpoint(PinnedEndpoint.Kind.ADDRESS_BOOK, displayName, lat, lon)
    is TripEndpoint.CurrentLocation ->
        PinnedEndpoint(PinnedEndpoint.Kind.CURRENT_LOCATION, lat = lat, lon = lon)
    is TripEndpoint.MapPoint -> PinnedEndpoint(PinnedEndpoint.Kind.MAP_POINT, lat = lat, lon = lon)
}

private fun PinnedEndpoint.toEndpointOrNull(): TripEndpoint? = when (kind) {
    PinnedEndpoint.Kind.FREE_TEXT -> TripEndpoint.FreeText(text.orEmpty())
    PinnedEndpoint.Kind.GEOCODED -> TripEndpoint.Geocoded(text.orEmpty(), lat, lon, isTransit)
    PinnedEndpoint.Kind.ADDRESS_BOOK -> TripEndpoint.AddressBook(text.orEmpty(), lat, lon)
    PinnedEndpoint.Kind.CURRENT_LOCATION -> TripEndpoint.CurrentLocation(lat, lon)
    // The only shape whose coordinates are structurally required; see the KDoc on toParamsOrNull.
    PinnedEndpoint.Kind.MAP_POINT -> lat?.let { la -> lon?.let { lo -> TripEndpoint.MapPoint(la, lo) } }
}

internal object PinnedTripJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(query: PinnedTripQuery): String = json.encodeToString(query)

    /**
     * The stored request, or null when the payload isn't one.
     *
     * The catch is deliberately narrow, as [org.onebusaway.android.nav.ReminderPlanJson]'s is:
     * [IllegalArgumentException] covers malformed JSON (`SerializationException` extends it) and JSON
     * that decodes but breaks the model's own `require`s. Anything else isn't a bad payload and must
     * not be silenced. No Android dependencies, so the JVM tests drive this directly.
     */
    fun decode(value: String): PinnedTripQuery? = try {
        json.decodeFromString<PinnedTripQuery>(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}
