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
package org.onebusaway.android.directions.model

import kotlin.time.Duration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.PolylineDecoder

/**
 * The app's own trip-plan domain model — replaces direct use of the vendored, unpublished-snapshot
 * `org.opentripplanner.api.model.*` POJOs (`edu.usf.cutr.opentripplanner.android:opentripplanner-pojos`)
 * that used to flow, largely unconverted, from the network layer all the way into Compose UI/ViewModel
 * signatures.
 *
 * Minted in exactly two places — `api/adapters/TripPlanAdapters.kt` (OTP1 REST, from
 * `api/contract/OtpPlanModels.kt`'s wire DTOs) and `api/adapters/Otp2PlanAdapters.kt` (OTP2 GraphQL,
 * #1780, from the Apollo-generated `PlanQuery.Data`) — both equally canonical, targeting this same
 * model so the rest of the app (UI/ViewModels/the trip monitor) never needs to know which protocol
 * produced a result. Consumers never re-parse a raw wire value (mode strings/enums, timestamps, delay
 * values) themselves. `duration`/`departureDelay`/`arrivalDelay` are [Duration] rather than a raw
 * ms/seconds `Long`, and `startTime`/`endTime` are [ServerTime] (the OTP server's clock, same "mint at
 * the boundary" domain-typing `org.onebusaway.android.time.TypedTime` uses elsewhere) rather than an
 * epoch-ms string or an ISO-8601 offset-datetime string, so unit/format confusion between OTP protocol
 * versions (OTP1 epoch-ms vs. OTP2 `OffsetDateTime`/ISO-8601 `Duration` strings) is no longer possible
 * by construction — there's nothing left to disambiguate.
 *
 * `from`/`to`/`startTime`/`endTime` are non-null: a well-formed OTP leg always has two endpoints and an
 * absolute start/end time (that's the point of a routing response), so the adapter — the one place that
 * knows whether a response is well-formed — asserts that once, instead of every consumer repeating a
 * `!!`/null-check whose answer was already known at parse time. Fixture construction (tests) can still
 * omit them; the field defaults below exist for that convenience only — production code always goes
 * through the adapter, which never relies on them.
 *
 * `@Serializable` (kotlinx.serialization, not a wire concern here) so the trip-plan-monitor notification
 * path (`TripPlanMonitorService`/`TripPlanScreen`) can JSON-encode a result list into an `Intent` extra
 * instead of relying on `java.io.Serializable`, which the old OTP1 POJOs got "for free" from the vendored
 * library and this domain model doesn't.
 */
@Serializable
data class TripItinerary(
    @Serializable(with = DurationSerializer::class) val duration: Duration = Duration.ZERO,
    @Serializable(with = ServerTimeSerializer::class) val startTime: ServerTime = ServerTime(0L),
    val legs: List<TripLeg> = emptyList()
)

@Serializable
data class TripLeg(
    val mode: TripMode? = null,
    val route: String? = null,
    val routeId: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    // The route's agency GTFS id (OTP2 `agency.gtfsId`, e.g. `kcm:1`) — used, with [agencyName], to
    // resolve this leg's route/stops onto OBA ids for route focus. Null on the OTP1 path.
    val agencyId: String? = null,
    val agencyName: String? = null,
    val headsign: String? = null,
    val tripId: String? = null,
    val realTime: Boolean = false,
    // OTP2 `Leg.interlineWithPreviousLeg`: true when the same vehicle continues from the previous leg
    // and the passenger stays aboard (a self-interline where a route reverses onto itself, or a
    // stay-aboard interline onto a different route). The directions layer folds such a leg into the
    // previous leg's card instead of emitting a spurious get-off/get-on pair (#2000). Always false on
    // the OTP1 path.
    val interlineWithPreviousLeg: Boolean = false,
    val distance: Double = 0.0,
    @Serializable(with = DurationSerializer::class) val duration: Duration = Duration.ZERO,
    @Serializable(with = DurationSerializer::class) val departureDelay: Duration = Duration.ZERO,
    @Serializable(with = DurationSerializer::class) val arrivalDelay: Duration = Duration.ZERO,
    @Serializable(with = ServerTimeSerializer::class) val startTime: ServerTime = ServerTime(0L),
    @Serializable(with = ServerTimeSerializer::class) val endTime: ServerTime = ServerTime(0L),
    val from: TripPlace = TripPlace(),
    val to: TripPlace = TripPlace(),
    val intermediateStops: List<TripPlace>? = null,
    val stop: List<TripPlace>? = null,
    val steps: List<TripStep> = emptyList(),
    val legGeometry: TripLegGeometry? = null,
    // Other departures OTP found between this leg's own board and alight stops, on any route
    // (#2010) — the raw candidate set, straight off the wire. Includes later trips of this leg's own
    // route. Which of them the drawer may present as interchangeable is decided by
    // [interchangeableRoutes], not here. Always empty on the OTP1 path, which has no equivalent.
    val alternatives: List<TripLegAlternative> = emptyList()
)

/**
 * One departure from OTP's alternative-leg search for a [TripLeg] (OTP2 `Leg.nextLegs`, #2010): a
 * single trip that runs between the same two stops as the leg it hangs off.
 *
 * [duration] is that trip's board→alight ride time — the field the interchangeability rule compares
 * against the planned leg's, so a local can't be offered in place of an express. [fromStopId] and
 * [toStopId] are OTP's GTFS stop ids for this trip's own boarding and alighting stops; the query asks
 * OTP for exact-stop alternatives, and [interchangeableRoutes] re-checks them against the leg rather
 * than trusting that, so a candidate can never send the rider to a different platform than the one
 * the drawer names.
 */
@Serializable
data class TripLegAlternative(
    val routeId: String? = null,
    val routeShortName: String? = null,
    val routeColor: String? = null,
    val agencyId: String? = null,
    val agencyName: String? = null,
    val headsign: String? = null,
    @Serializable(with = DurationSerializer::class) val duration: Duration = Duration.ZERO,
    val fromStopId: String? = null,
    val toStopId: String? = null
)

@Serializable
data class TripPlace(
    val name: String? = null,
    // The GTFS stop id (OTP2 `stop.gtfsId`), when this place is a transit stop — the identity the
    // arrivals board / route focus keys on. Distinct from [stopCode], the human-facing platform code.
    // Null for non-stop places and on the OTP1 path (its wire place carries no stop id).
    val stopId: String? = null,
    val stopCode: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val vertexType: TripVertexType? = null,
    val bikeShareId: String? = null
)

@Serializable
data class TripStep(
    val distance: Double = 0.0,
    val relativeDirection: TripRelativeDirection? = null,
    val absoluteDirection: TripAbsoluteDirection? = null,
    val streetName: String? = null,
    val exit: String? = null,
    val stayOn: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

/**
 * The leg's display short name: its route short name, else the OTP1 flat route string, else the route
 * id; null when the leg carries none. As a #662 work-around this deliberately never uses the trip short
 * name. Shared by the directions generator's itinerary title and the interline badge/label logic so the
 * fallback lives in one place.
 */
fun TripLeg.routeDisplayShortName(): String? = listOf(routeShortName, route, routeId).firstOrNull { !it.isNullOrEmpty() }

@Serializable
data class TripLegGeometry(val points: String? = null, val length: Int = 0)

/** Decode the encoded leg polyline to points; empty when the geometry is absent or degenerate. */
fun TripLegGeometry.decodedPoints(): List<GeoPoint> {
    val encoded = points ?: return emptyList()
    if (encoded.isEmpty() || length <= 0) return emptyList()
    return PolylineDecoder.decode(encoded, length)
}

private val tripItineraryJson = Json { ignoreUnknownKeys = true }

/**
 * JSON-encodes a plan result for the one spot it needs to cross a real Android serialization boundary
 * (the trip-plan-monitor's "your trip changed" notification `Intent` — see
 * `TripPlanMonitorService.notifyChange` / `TripPlanScreen.maybeRestoreFromIntent`). Paired with
 * [String.toTripItineraries].
 */
fun List<TripItinerary>.toJson(): String = tripItineraryJson.encodeToString(ListSerializer(TripItinerary.serializer()), this)

/**
 * The read side of [List.toJson]. A corrupted/truncated extra degrades to an empty list — exactly how
 * [TripPlanScreen.maybeRestoreFromIntent][org.onebusaway.android.ui.tripplan] already treats a missing
 * extra — rather than crashing the activity on notification re-entry.
 */
fun String.toTripItineraries(): List<TripItinerary> = runCatching {
    tripItineraryJson.decodeFromString(ListSerializer(TripItinerary.serializer()), this)
}.getOrDefault(emptyList())

private object ServerTimeSerializer : KSerializer<ServerTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ServerTime", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: ServerTime) = encoder.encodeLong(value.epochMs)
    override fun deserialize(decoder: Decoder): ServerTime = ServerTime(decoder.decodeLong())
}

private object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toIsoString())
    override fun deserialize(decoder: Decoder): Duration = Duration.parseIsoString(decoder.decodeString())
}

/**
 * Mirrors OTP1's `org.opentripplanner.routing.core.TraverseMode` wire vocabulary exactly (verified
 * against the vendored jar). [isTransit] mirrors `TraverseMode.isTransit()` so callers don't each
 * re-derive it from the raw mode name.
 */
enum class TripMode {
    WALK,
    BICYCLE,
    CAR,
    TRAM,
    SUBWAY,
    RAIL,
    BUS,
    FERRY,
    CABLE_CAR,
    GONDOLA,
    FUNICULAR,
    TRANSIT,
    TRAINISH,
    BUSISH,
    BOARDING,
    ALIGHTING,
    TRANSFER;

    val isTransit: Boolean
        get() = this in TRANSIT_MODES

    /** Mirrors `TraverseMode.isOnStreetNonTransit()`: true only for WALK/BICYCLE/CAR. */
    val isOnStreetNonTransit: Boolean
        get() = this == WALK || this == BICYCLE || this == CAR

    private companion object {
        val TRANSIT_MODES = setOf(TRAM, SUBWAY, RAIL, BUS, FERRY, CABLE_CAR, GONDOLA, FUNICULAR, TRANSIT, TRAINISH, BUSISH)
    }
}

/** Mirrors OTP1's `org.opentripplanner.api.model.VertexType` wire vocabulary exactly. */
enum class TripVertexType { NORMAL, BIKESHARE, BIKEPARK, TRANSIT }

/** Mirrors OTP1's `org.opentripplanner.api.model.RelativeDirection` wire vocabulary exactly. */
enum class TripRelativeDirection {
    DEPART,
    HARD_LEFT,
    LEFT,
    SLIGHTLY_LEFT,
    CONTINUE,
    SLIGHTLY_RIGHT,
    RIGHT,
    HARD_RIGHT,
    CIRCLE_CLOCKWISE,
    CIRCLE_COUNTERCLOCKWISE,
    ELEVATOR,
    UTURN_LEFT,
    UTURN_RIGHT
}

/** Mirrors OTP1's `org.opentripplanner.api.model.AbsoluteDirection` wire vocabulary exactly. */
enum class TripAbsoluteDirection { NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST }
