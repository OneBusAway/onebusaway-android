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
package org.onebusaway.android.api.adapters

import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import org.onebusaway.android.api.graphql.PlanQuery
import org.onebusaway.android.api.graphql.fragment.PlaceFields
import org.onebusaway.android.api.graphql.fragment.RentalNetworkFields
import org.onebusaway.android.api.graphql.fragment.RentalUriFields
import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.directions.model.TripAbsoluteDirection
import org.onebusaway.android.directions.model.TripAlert
import org.onebusaway.android.directions.model.TripAlertSeverity
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegAlternative
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripRelativeDirection
import org.onebusaway.android.directions.model.TripStep
import org.onebusaway.android.directions.model.TripVehicleRental
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.time.ServerTime

/**
 * Maps the OTP 2.x GraphQL `planConnection` response (Apollo-generated from
 * `src/main/graphql/otp2/Plan.graphql`) onto the *same* app-owned trip-plan domain model
 * (`directions/model/TripItinerary.kt`) that [toTripItinerary] (OTP1 REST) mints — a second, equally
 * canonical minting site for that model, per #1780. Every enum/[ServerTime]/[Duration] value is
 * minted here, exactly once, same discipline as the OTP1 adapter.
 *
 * Wire-format deltas from OTP1 (see issue #1780 for the full table): times are ISO-8601
 * `OffsetDateTime` strings (not epoch-ms), delays are ISO-8601 `Duration` strings (not a ms/sec
 * int), and itinerary/leg `duration` is already seconds as a plain number. `vertexType` is inferred
 * structurally from which of `stop`/`rentalVehicle`/`vehicleParking`/`vehicleRentalStation` is
 * non-null — never OTP2's own deprecated `Place.vertexType` field, which would trip a Kotlin
 * deprecation warning under this repo's `-PwarningsAsErrors=true` CI gate.
 */
fun PlanQuery.Data.toTripItineraries(): List<TripItinerary> = planConnection?.edges.orEmpty().mapNotNull { it?.node }.map { it.toTripItinerary() }

private fun PlanQuery.Node.toTripItinerary(): TripItinerary = TripItinerary(
    duration = (duration ?: 0L).seconds,
    startTime = requireField("itinerary.start", start?.toServerTime()),
    legs = legs.filterNotNull().map { it.toTripLeg() }
)

private fun PlanQuery.Leg.toTripLeg(): TripLeg = TripLeg(
    mode = mode?.rawValue.toEnum<TripMode>(),
    route = null, // No OTP2 equivalent of OTP1's flat display-string `route` field.
    routeId = route?.routeFields?.gtfsId,
    // Blank→null, same as the OTP1 adapter: GTFS routinely publishes an empty `route_short_name`
    // (every Washington State Ferries route does), so absence arrives as both null and "".
    // Normalizing here leaves the domain one representation of "this route has no short name".
    routeShortName = route?.routeFields?.shortName?.ifBlank { null },
    routeLongName = route?.routeFields?.longName?.ifBlank { null },
    routeColor = route?.routeFields?.color,
    agencyId = route?.routeFields?.agency?.gtfsId,
    agencyName = route?.routeFields?.agency?.name,
    headsign = trip?.tripHeadsign,
    tripId = trip?.gtfsId,
    realTime = realTime ?: false,
    interlineWithPreviousLeg = interlineWithPreviousLeg ?: false,
    distance = distance ?: 0.0,
    duration = (duration ?: 0.0).seconds,
    departureDelay = start.estimated?.delay.toDelayDuration(),
    arrivalDelay = end.estimated?.delay.toDelayDuration(),
    startTime = requireField(
        "leg.start",
        (start.estimated?.time ?: start.scheduledTime).toServerTime()
    ),
    endTime = requireField("leg.end", (end.estimated?.time ?: end.scheduledTime).toServerTime()),
    from = from.placeFields.toTripPlace(),
    to = to.placeFields.toTripPlace(),
    intermediateStops = null,
    stop = stopCalls.toIntermediateTripPlaces(),
    steps = steps.orEmpty().filterNotNull().map { it.toTripStep() },
    legGeometry = legGeometry?.let { TripLegGeometry(points = it.points, length = it.length ?: 0) },
    // OTP's own alternative-leg search for this leg (#2010) — null on a non-transit leg, and carried
    // over unjudged: interchangeability is decided by `interchangeableRoutes()`, which needs the whole
    // itinerary (the next leg's departure) and so can't be answered leg-at-a-time here.
    alternatives = nextLegs.orEmpty().map { it.toTripLegAlternative() },
    // Already scoped by OTP to this leg's entities and time window (#2143) — see the `alerts`
    // selection in Plan.graphql. Empty rather than null on a leg with nothing to report.
    alerts = alerts.orEmpty().filterNotNull().map { it.toTripAlert() }
)

/**
 * Blank→null on every text field, same normalization the route names get above: OTP returns `""` for
 * a translation the feed didn't publish (`alertDescriptionText` is even non-null in the schema), so
 * without this the domain would carry an empty header that reads as present and renders as a blank
 * row.
 */
private fun PlanQuery.Alert.toTripAlert(): TripAlert = TripAlert(
    id = id,
    header = alertHeaderText?.ifBlank { null },
    description = alertDescriptionText.ifBlank { null },
    url = alertUrl?.ifBlank { null },
    // An unrecognized severity (a schema the server has moved past) lands on the same
    // UNKNOWN_SEVERITY the wire vocabulary already has a token for, which the banner styles as a
    // warning — an alert whose severity we can't read is still an alert.
    severity = alertSeverityLevel?.rawValue.toEnum<TripAlertSeverity>() ?: TripAlertSeverity.UNKNOWN_SEVERITY
)

/**
 * OTP2's `stopCalls` includes the boarding and alighting calls, but `TripLeg.stop` is consumed as a
 * leg's *intermediate* stops — the directions drawer's "N stops in between" and the reminder plan's
 * stop progression — so the two endpoints are dropped here; they already arrive as `from`/`to`.
 *
 * A call this query cannot represent as a transit stop (OTP's flex `Location`/`LocationGroup`
 * variants) collapses the whole list to null rather than quietly omitting one stop: a hole in the
 * middle would shift the reminder plan's penultimate stop and alert the rider at the wrong place.
 */
private fun List<PlanQuery.StopCall>.toIntermediateTripPlaces(): List<TripPlace>? {
    if (size <= 2) return emptyList()
    return subList(1, size - 1).map { call ->
        val stop = call.stopLocation.onStop ?: return null
        TripPlace(
            name = stop.name,
            stopId = stop.gtfsId,
            // The rider-facing stop number, which the directions drawer appends to each intermediate
            // stop's name. Omitting it here is why OTP2 legs showed a bare name where OTP1 showed
            // "Name (1002)".
            stopCode = stop.code,
            lat = stop.lat,
            lon = stop.lon,
            vertexType = TripVertexType.TRANSIT
        )
    }
}

private fun PlanQuery.NextLeg.toTripLegAlternative(): TripLegAlternative = TripLegAlternative(
    routeId = route?.alternativeRouteFields?.gtfsId,
    routeShortName = route?.alternativeRouteFields?.shortName?.ifBlank { null },
    routeLongName = route?.alternativeRouteFields?.longName?.ifBlank { null },
    routeColor = route?.alternativeRouteFields?.color,
    agencyId = route?.alternativeRouteFields?.agency?.gtfsId,
    agencyName = route?.alternativeRouteFields?.agency?.name,
    headsign = trip?.tripHeadsign,
    // The alternative trip's own board→alight ride time, the quantity the interchangeability rule
    // compares against the planned leg's.
    duration = (duration ?: 0.0).seconds,
    fromStopId = from.stop?.gtfsId,
    toStopId = to.stop?.gtfsId
)

// PlaceFields backs both Leg.from and Leg.to (see the Plan.graphql fragment) — one mapping instead
// of two structurally-identical copies.
private fun PlaceFields.toTripPlace(): TripPlace = TripPlace(
    name = name,
    stopId = stop?.gtfsId,
    stopCode = stop?.code,
    lat = lat,
    lon = lon,
    vertexType = inferVertexType(
        hasStop = stop != null,
        hasRental = rentalVehicle != null || vehicleRentalStation != null,
        hasParking = vehicleParking != null
    ),
    rental = rentalVehicle?.toTripVehicleRental() ?: vehicleRentalStation?.toTripVehicleRental()
)

/** A free-floating rental vehicle: no dock, so no `stationName` to walk the rider to. */
private fun PlaceFields.RentalVehicle.toTripVehicleRental(): TripVehicleRental = toTripVehicleRental(
    id = vehicleId,
    network = rentalNetwork.rentalNetworkFields,
    uris = rentalUris?.rentalUriFields,
    formFactor = vehicleType?.formFactor?.rawValue.toEnum<RentalFormFactor>(),
    propulsion = vehicleType?.propulsionType?.rawValue.toEnum<RentalPropulsion>(),
    rangeMeters = fuel?.range
)

/**
 * A docked rental station. It publishes no vehicle type — the dock holds whatever the operator left
 * in it — so [TripVehicleRental.formFactor]/[TripVehicleRental.propulsion] stay null and the drawer
 * words the leg by its own travel mode instead.
 */
private fun PlaceFields.VehicleRentalStation.toTripVehicleRental(): TripVehicleRental = toTripVehicleRental(
    id = stationId,
    network = rentalNetwork.rentalNetworkFields,
    uris = rentalUris?.rentalUriFields,
    stationName = name.ifBlank { null }
)

/**
 * The rental facts both endpoint shapes state identically, mapped once: OTP types their network and
 * URIs the same way, and the shared Plan.graphql fragments hand this the same generated types from
 * either. Blank→null on every URL, the same normalization the route names get above.
 */
private fun toTripVehicleRental(
    id: String?,
    network: RentalNetworkFields,
    uris: RentalUriFields?,
    stationName: String? = null,
    formFactor: RentalFormFactor? = null,
    propulsion: RentalPropulsion? = null,
    rangeMeters: Int? = null
): TripVehicleRental = TripVehicleRental(
    id = id,
    stationName = stationName,
    networkId = network.networkId,
    networkUrl = network.url?.ifBlank { null },
    androidUri = uris?.android?.ifBlank { null },
    webUri = uris?.web?.ifBlank { null },
    formFactor = formFactor,
    propulsion = propulsion,
    rangeMeters = rangeMeters
)

/**
 * OTP2's `Place.stop`/`rentalVehicle`/`vehicleParking`/`vehicleRentalStation` are populated
 * mutually-exclusively by OTP based on the place's actual kind (never guessed from magnitude/shape),
 * so reading which one is non-null is a structural fact, not a heuristic — see the deprecated-field
 * note on [PlanQuery.Data.toTripItineraries]. A place matching none of them is a plain street
 * location/POI, i.e. OTP1's own `NORMAL`.
 */
private fun inferVertexType(hasStop: Boolean, hasRental: Boolean, hasParking: Boolean): TripVertexType = when {
    hasStop -> TripVertexType.TRANSIT
    hasRental -> TripVertexType.BIKESHARE
    hasParking -> TripVertexType.BIKEPARK
    else -> TripVertexType.NORMAL
}

private fun PlanQuery.Step.toTripStep(): TripStep = TripStep(
    distance = distance ?: 0.0,
    relativeDirection = relativeDirection?.rawValue.toEnum<TripRelativeDirection>(),
    absoluteDirection = absoluteDirection?.rawValue.toEnum<TripAbsoluteDirection>(),
    streetName = streetName,
    exit = exit,
    stayOn = stayOn ?: false,
    lat = lat ?: 0.0,
    lon = lon ?: 0.0
)

// requireField/toEnum are shared with the OTP1 adapter — see TripPlanAdapters.kt.

/** Parses an OTP2 `OffsetDateTime` scalar string (mapped to plain `String`; see the Apollo `mapScalar`
 * config) into the app's server-clock domain type. */
private fun String.toServerTime(): ServerTime = ServerTime(OffsetDateTime.parse(this).toInstant().toEpochMilli())

/** Parses an OTP2 `Duration` scalar string (an ISO-8601 duration, e.g. `PT2M`); absent (no real-time
 * estimate for this event) means no delay. */
private fun String?.toDelayDuration(): Duration = this?.let { java.time.Duration.parse(it).toKotlinDuration() } ?: Duration.ZERO
