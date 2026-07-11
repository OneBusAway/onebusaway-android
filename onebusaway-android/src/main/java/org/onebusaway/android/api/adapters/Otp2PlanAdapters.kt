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

import org.onebusaway.android.api.graphql.PlanQuery
import org.onebusaway.android.api.graphql.fragment.PlaceFields
import org.onebusaway.android.directions.model.TripAbsoluteDirection
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripRelativeDirection
import org.onebusaway.android.directions.model.TripStep
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.time.ServerTime
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

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
fun PlanQuery.Data.toTripItineraries(): List<TripItinerary> =
    planConnection?.edges.orEmpty().mapNotNull { it?.node }.map { it.toTripItinerary() }

private fun PlanQuery.Node.toTripItinerary(): TripItinerary = TripItinerary(
    duration = (duration ?: 0L).seconds,
    startTime = requireField("itinerary.start", start?.toServerTime()),
    legs = legs.filterNotNull().map { it.toTripLeg() },
)

private fun PlanQuery.Leg.toTripLeg(): TripLeg = TripLeg(
    mode = mode?.rawValue.toEnum<TripMode>(),
    route = null, // No OTP2 equivalent of OTP1's flat display-string `route` field.
    routeId = route?.gtfsId,
    routeShortName = route?.shortName,
    routeLongName = route?.longName,
    routeColor = route?.color,
    agencyName = route?.agency?.name,
    // No OTP2 equivalent: OTP2 timestamps already carry their own offset (issue #1780's wire table).
    agencyTimeZoneOffset = 0,
    headsign = trip?.tripHeadsign,
    tripId = trip?.gtfsId,
    realTime = realTime ?: false,
    distance = distance ?: 0.0,
    duration = (duration ?: 0.0).seconds,
    departureDelay = start.estimated?.delay.toDelayDuration(),
    arrivalDelay = end.estimated?.delay.toDelayDuration(),
    startTime = requireField(
        "leg.start",
        (start.estimated?.time ?: start.scheduledTime).toServerTime(),
    ),
    endTime = requireField("leg.end", (end.estimated?.time ?: end.scheduledTime).toServerTime()),
    from = from.placeFields.toTripPlace(),
    to = to.placeFields.toTripPlace(),
    // Not requested by Plan.graphql: OTP2's nearest equivalents (`stopCalls`/deprecated
    // `intermediatePlaces`) have a materially different shape from OTP1's flat stop-place list.
    intermediateStops = null,
    stop = null,
    steps = steps.orEmpty().filterNotNull().map { it.toTripStep() },
    legGeometry = legGeometry?.let { TripLegGeometry(points = it.points, length = it.length ?: 0) },
)

// PlaceFields backs both Leg.from and Leg.to (see the Plan.graphql fragment) — one mapping instead
// of two structurally-identical copies.
private fun PlaceFields.toTripPlace(): TripPlace = TripPlace(
    name = name,
    stopCode = stop?.code,
    lat = lat,
    lon = lon,
    vertexType = inferVertexType(
        hasStop = stop != null,
        hasRental = rentalVehicle != null || vehicleRentalStation != null,
        hasParking = vehicleParking != null,
    ),
    bikeShareId = rentalVehicle?.vehicleId ?: vehicleRentalStation?.stationId,
)

/**
 * OTP2's `Place.stop`/`rentalVehicle`/`vehicleParking`/`vehicleRentalStation` are populated
 * mutually-exclusively by OTP based on the place's actual kind (never guessed from magnitude/shape),
 * so reading which one is non-null is a structural fact, not a heuristic — see the deprecated-field
 * note on [PlanQuery.Data.toTripItineraries]. A place matching none of them is a plain street
 * location/POI, i.e. OTP1's own `NORMAL`.
 */
private fun inferVertexType(hasStop: Boolean, hasRental: Boolean, hasParking: Boolean): TripVertexType =
    when {
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
    lon = lon ?: 0.0,
)

// requireField/toEnum are shared with the OTP1 adapter — see TripPlanAdapters.kt.

/** Parses an OTP2 `OffsetDateTime` scalar string (mapped to plain `String`; see the Apollo `mapScalar`
 * config) into the app's server-clock domain type. */
private fun String.toServerTime(): ServerTime = ServerTime(OffsetDateTime.parse(this).toInstant().toEpochMilli())

/** Parses an OTP2 `Duration` scalar string (an ISO-8601 duration, e.g. `PT2M`); absent (no real-time
 * estimate for this event) means no delay. */
private fun String?.toDelayDuration(): Duration =
    this?.let { java.time.Duration.parse(it).toKotlinDuration() } ?: Duration.ZERO
