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
    // Whether the rider covers this leg on a *hired* vehicle — OTP's `rentedBike`, published on both
    // protocols (OTP2 `Leg.rentedBike`, OTP1 `leg.rentedBike`). The one thing that separates a
    // bikeshare ride from a ride on the rider's own bike, since OTP gives both the plain `BICYCLE`
    // mode; read rather than inferred from the leg's endpoints (#2159). Despite the wire name it is
    // not bike-specific on OTP2 — the server sets it from `isRentingVehicle()` — so the domain calls
    // it what it means.
    //
    // False rather than null when the wire didn't say: OTP2 leaves it null on exactly the legs that
    // aren't street legs (transit legs, zero-distance transfer legs), where "is the rider on a hired
    // vehicle" has no meaning, and OTP1 states it on every leg. So there is no leg where the
    // difference between "false" and "unstated" is a fact about the trip.
    val rentedVehicle: Boolean = false,
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
    val alternatives: List<TripLegAlternative> = emptyList(),
    // The service alerts OTP considers applicable to this leg (#2143) — already scoped by the server
    // to the leg's own entities *and* its time window (see the `alerts` selection in Plan.graphql),
    // so nothing here needs an active-window check. Always empty on the OTP1 path: the REST `/plan`
    // response this app's regions serve carries no leg alerts at all (verified against the live OTP1
    // server), so there is nothing to map rather than a mapping left undone.
    val alerts: List<TripAlert> = emptyList()
)

/**
 * One service alert attached to a [TripLeg] — a disruption the rider has to know about before they
 * trust the itinerary, which is the whole point of surfacing it in the planner: an itinerary routed
 * over suspended service looks perfectly ordinary otherwise (#2143).
 *
 * [id] is OTP's own global alert id, stable for as long as the feed publishes the alert under the
 * same id. It is *not* used as the rider-facing row identity — a feed that republishes the same
 * disruption under a fresh id would then read as a new alert, the #1593 failure — so the trip-results
 * layer keys rows on the alert's content instead. It is kept because it is the only thing that
 * identifies the alert back to OTP.
 *
 * [header] and [description] are GTFS-realtime `header_text`/`description_text`, already
 * language-negotiated by OTP. Both are plain text, not HTML (unlike the OBA `situation` path's
 * description) — GTFS-rt `TranslatedString` carries no markup.
 */
@Serializable
data class TripAlert(
    val id: String,
    val header: String? = null,
    val description: String? = null,
    val url: String? = null,
    val severity: TripAlertSeverity = TripAlertSeverity.UNKNOWN_SEVERITY
)

/**
 * Mirrors OTP2's `AlertSeverityLevelType` wire vocabulary exactly (verified against the vendored
 * `schema.graphqls`), same discipline as [TripMode]. Mapping it onto the app's three alert-banner
 * styles is the presentation layer's job, not this model's.
 */
enum class TripAlertSeverity { INFO, WARNING, SEVERE, UNKNOWN_SEVERITY }

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
    // Carried for the same reason the planned leg carries it: a route with no short name has to name
    // itself somehow, and its id is not a name (see [routeDisplayShortName]).
    val routeLongName: String? = null,
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
    // The GTFS stop id (OTP2 `stop.gtfsId`, OTP1 `stopId`), when this place is a transit stop — the
    // identity the arrivals board / route focus keys on, and what destination reminders match stops
    // by. Distinct from [stopCode], the human-facing platform code. Null for non-stop places.
    val stopId: String? = null,
    val stopCode: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val vertexType: TripVertexType? = null,
    // The rented vehicle (or its dock) this place *is*, when the place is a vehicle-rental endpoint —
    // null everywhere else. Replaces the bare `bikeShareId` this used to carry: the id alone can only
    // filter the map's bike layer, while a rider being sent to a shared bike also needs to know whose
    // it is and how to unlock it (#2150).
    val rental: TripVehicleRental? = null
)

/**
 * A vehicle-rental endpoint of a leg — the shared bike/scooter the rider picks up, or the dock they
 * pick it up from (#2150). OTP models the two separately (`Place.rentalVehicle` vs.
 * `Place.vehicleRentalStation`) but publishes the same rental facts on both, so they land on one type
 * here, and [stationName] is what the rider can act on: the dock to walk to, when the pickup is one
 * that published a name.
 *
 * Every field is what the *feed* stated, carried unjudged:
 *  - [kind] is which of those two shapes OTP populated, recorded because merging them here would
 *    otherwise lose it — and [id] means a different thing in each (a `vehicleId`, or a `stationId`).
 *    Read it, never [stationName], to tell a loose vehicle from a dock: a station that published no
 *    name is still a station. Null only on the OTP1 path, which states neither.
 *  - [id] is OTP's network-qualified `network:id`, the identity the map's bike layer filters on. It is
 *    not shown to the rider: the ids the live Puget Sound networks publish are UUIDs, which no rider
 *    can match against a bike in front of them.
 *  - [networkId] is the operator, from OTP's own `rentalNetwork.networkId` — a GBFS `system_id` like
 *    `lime_seattle`, not a brand name. Turning it into one is presentation, and lives in
 *    `RentalOperators` (the UI layer), not here.
 *  - [androidUri]/[webUri] are the operator's deep links to *this* vehicle or station
 *    (`rentalUris.android`/`.web`), and [networkUrl] the operator's own system URL. All three are null
 *    on every vehicle the live OTP2 deployment serves today, which is exactly why they are carried
 *    rather than assumed: a feed that does publish them lets the app hand the rider straight to the
 *    bike they were routed onto. All three are **absolute or absent** — a feed value naming no scheme
 *    is dropped at the wire boundary, since nothing on the device can open one (see
 *    `Otp2PlanAdapters.absoluteUriOrNull`), so a reader may open what it finds here.
 *  - [rangeMeters] is `fuel.range` — how far the vehicle can still travel on its current charge,
 *    documented by the schema as meters.
 *
 * OTP1 populates only [id] (its `bikeShareId`): that API carries no rental metadata at all, so a leg
 * planned on an OTP1 region has a rental with nothing to say about its operator, and the drawer draws
 * the plain bike row it always did.
 */
@Serializable
data class TripVehicleRental(
    val id: String? = null,
    val kind: RentalEndpointKind? = null,
    val stationName: String? = null,
    val networkId: String? = null,
    val networkUrl: String? = null,
    val androidUri: String? = null,
    val webUri: String? = null,
    val formFactor: RentalFormFactor? = null,
    val propulsion: RentalPropulsion? = null,
    val rangeMeters: Int? = null
)

/**
 * Which rental endpoint a [TripVehicleRental] describes: a free-floating vehicle
 * (`Place.rentalVehicle`) or a dock (`Place.vehicleRentalStation`).
 *
 * Structural, from which field OTP populated — not read off the values, which cannot tell them apart:
 * both carry an id, and a dock may publish no name. It decides what [TripVehicleRental.id] identifies,
 * which is why a link that names a *vehicle* to the operator's app (#2158) is built for one and never
 * the other.
 */
enum class RentalEndpointKind { VEHICLE, STATION }

/** Mirrors OTP2's `FormFactor` wire vocabulary exactly (which mirrors GBFS's `vehicle_type`). */
enum class RentalFormFactor { BICYCLE, CAR, CARGO_BICYCLE, MOPED, OTHER, SCOOTER, SCOOTER_SEATED, SCOOTER_STANDING }

/** Mirrors OTP2's `PropulsionType` wire vocabulary exactly (which mirrors GBFS's `propulsion_type`). */
enum class RentalPropulsion { COMBUSTION, COMBUSTION_DIESEL, ELECTRIC, ELECTRIC_ASSIST, HUMAN, HYBRID, HYDROGEN_FUEL_CELL, PLUG_IN_HYBRID }

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
 * How a leg names its route, in the three forms the directions UI asks for. They differ only in which
 * field wins, so they live together — reading one means reading the ordering it *didn't* choose:
 *
 *  - [routeDisplayShortName] — the badge. Short name only; null when there isn't one.
 *  - [routeDisplayLabel] — prose and compact titles. Short name, else the long one.
 *  - [routeDisplayName] — a row title that already has the badge beside it, so it leads with the long
 *    name and falls back to the short one.
 *
 * None of them falls back to the route **id**. A GTFS id is an identifier, not a name: badging a
 * Washington State Ferries run (`route_short_name` is empty; the route is named only
 * "Seattle - Bremerton") as its OTP2 gtfsId `95:74` puts a string on screen that reads like a route
 * number the rider could look for and will never find. A route with no short name simply has no badge;
 * callers draw none and use one of the other two. The id fallback was near-harmless on OTP1, where the
 * flat [TripLeg.route] display string usually caught the gap first; OTP2 has no equivalent field (its
 * adapter sets `route = null`), which promoted the id from last resort to first.
 *
 * All three test null alone, not blankness: the adapters normalize a blank wire name to null on the way
 * in, so absence has one representation by the time it reaches here.
 */
fun TripLeg.routeDisplayShortName(): String? = routeShortName ?: route

/**
 * The leg's route named as compactly as it can be — for prose and titles, where something has to be
 * said, as opposed to [routeDisplayShortName], whose absence a badge can express by not being drawn.
 * As a #662 work-around this deliberately never uses the trip short name.
 */
fun TripLeg.routeDisplayLabel(): String? = routeDisplayShortName() ?: routeLongName

/** The fuller name for a row that shows the badge separately, so the long form leads. */
fun TripLeg.routeDisplayName(): String? = routeLongName ?: routeDisplayShortName()

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
